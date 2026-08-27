# BE-DONE — Khách dọn vào giữa kỳ: chỉ tính điện/nước từ mốc đón khách

Ngày: 2026-08-27  
Phạm vi: **chỉ BE** (`slms2026`) — nhà **NGUYÊN CĂN**. Nhà chia phòng không đổi.

## Vấn đề đã xử lý

Giấy EVN/nước tính trọn tháng, nhưng khách dọn giữa kỳ. Trước đây
`createFromWholeHouseBill` gán cứng tiêu thụ = tổng giấy → khách bị tính cả phần
trước khi dọn; FE không thể gửi `prevReading` = mốc đón vì dính `CONSUMPTION_MISMATCH`.

## Thay đổi

### 1. `UtilityInvoiceServiceImpl.createFromWholeHouseBill`

- Lấy mốc đón từ HĐ: `initialElectricReading` / `initialWaterReading` (+ `*MeterCapturedAt`).
- Nếu mốc đón **> đầu kỳ giấy** và **≤ chỉ số mới** → `prevReading` hoá đơn khách = mốc đón;
  `consumption` / `amount` = phần từ mốc đón × đơn giá giấy.
- Không thoả (khách cũ kỳ 2+, mốc ngoài khoảng, đón sau hết tháng kỳ) → giữ hành vi cũ
  (đúng số trên giấy).
- `unitPrice` luôn lấy từ giấy (`totalAmount ÷ totalQuantity`), không tính lại theo phần khách.
- Response `UtilityInvoice` trả `prevReading` = mốc đã dùng để tính (đối chiếu với khách).

### 2. Ghi phần công ty chịu trên `utility_bills`

| Cột | Ý nghĩa |
|-----|---------|
| `billed_to_tenant_quantity` | kWh/m³ đã phát hành cho khách |
| `company_born_quantity` | kWh/m³ công ty chịu = tổng giấy − phần khách |

Migration: `DatabaseSchemaMigration` thêm 2 cột `NUMERIC(19,4)`.  
Response `UtilityBillResponse` expose 2 field tương ứng.

`UtilityBill.totalQuantity` / `totalAmount` **không đổi** — vẫn là chi phí thật với nhà nước.

## File đụng

- `service/impl/UtilityInvoiceServiceImpl.java`
- `entity/UtilityBill.java`
- `dto/response/UtilityBillResponse.java`
- `service/impl/UtilityBillServiceImpl.java` (map response)
- `config/DatabaseSchemaMigration.java`

## FE cần làm

Trong `EvnBillPublishing` / `WaterBillPublishing`: có thể bỏ cảnh báo “chưa trừ được”
và tin `prevReading` / `consumption` / `amount` trên hoá đơn khách sau phát hành.
Không cần (và không nên) ghi đè chỉ số cũ trên form bằng mốc đón trước khi gọi API —
BE tự tách.

## Kiểm chứng

1. Đón 15/07, đồng hồ 19.200; giấy 19.000→19.500, 500 kWh, 1.500.000đ (3.000đ/kWh)
   → HĐ khách: prev 19.200, new 19.500, consumption 300, amount 900.000  
   → Bill: vẫn 500 / 1.500.000; `billedToTenantQuantity=300`, `companyBornQuantity=200`
2. Khách cũ kỳ 2+ (mốc đón ≤ chốt kỳ trước) → trọn kỳ như cũ
3. Mốc đón > chỉ số cuối kỳ → bỏ qua, không âm
4. Nhà chia phòng → không đổi
