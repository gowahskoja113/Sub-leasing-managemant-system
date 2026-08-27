# BE-DONE — Đối soát điện/nước nhà CHIA PHÒNG

Ngày: 2026-08-27  
Phạm vi: **chỉ BE** (`slms2026`) — nhà **chia phòng**. Nhà nguyên căn không đổi.

## Vấn đề đã xử lý

Một giấy EVN/nước → N hoá đơn phòng tạo rải rác. Trước đây
`billedToTenantQuantity` / `companyBornQuantity` trên `UtilityBill` luôn `null`
với chia phòng → không đối soát được hao hụt hành lang / phòng trống / rò.

## Thay đổi

### 1. Biết khi nào đã đọc đủ phòng

`MeterReadingService.listEligibleForPeriod(propertyId, period, type)` — cùng bộ
điều kiện với `listPending`: HĐ ACTIVE có phòng, bỏ khách `startDate` sau
`readingDeadline`, bỏ nhà nguyên căn (`readingDeadline` null).

`listPending` / `listPendingFor` tái sử dụng cùng collector (`onlyMissingPhoto`).

### 2. Chốt đối soát sau `createRoomInvoice`

Khi mọi phòng eligible đã có hoá đơn phòng **còn hiệu lực** (không tính
tenant invoice `CANCELLED`):

| Cột | Giá trị |
|-----|---------|
| `billed_to_tenant_quantity` | Σ `consumption` hoá đơn phòng kỳ này |
| `company_born_quantity` | `totalQuantity` − billed (≥ 0) |

Idempotent: tính lại từ đầu mỗi lần phòng cuối (hoặc phát hành lại) — không `+=`.
Không chặn phát hành khi có hao hụt.

### 3. Cảnh báo hao hụt bất thường

`utility.loss-alert-threshold-percent` (mặc định **15**).  
`companyBorn ÷ total ≥ ngưỡng` → notify quản lý phụ trách + mọi `ROLE_ADMIN`
(`UTILITY_LOSS_ALERT`, dedupe theo `billId`). Nội dung ghi thêm baseline ~% từ
3 kỳ gần nhất (nếu có).

### 4. FE

`UtilityBillResponse` đã có `billedToTenantQuantity` / `companyBornQuantity` /
`roomsDone` / `roomsTotal` — null khi chưa đủ phòng; có số khi đã chốt.

## File đụng

- `service/MeterReadingService.java`
- `service/impl/MeterReadingServiceImpl.java`
- `service/impl/UtilityInvoiceServiceImpl.java`
- `repository/UtilityBillRepository.java`
- `dto/response/UtilityBillResponse.java` (comment)
- `resources/application.yaml`

## Kiểm chứng

1. 5 phòng có khách, giấy 2000 — đọc 4 phòng → hai cột vẫn null; phòng 5 → 1750 / 250
2. Huỷ + phát hành lại 1 phòng → tính lại, không cộng dồn
3. 2 phòng trống → chỉ chờ phòng có HĐ ACTIVE
4. Khách ký sau `readingDeadline` → không chặn đối soát
5. Hao 25% → cảnh báo; 4% → không
6. Nguyên căn → hành vi cũ (`createFromWholeHouseBill`)
