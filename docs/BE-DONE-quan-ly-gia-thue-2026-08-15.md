# BE DONE — Quản lý giá thuê: niêm yết, hợp đồng, tăng giá theo năm

**Ngày:** 15/08/2026  
**Người gửi:** team BE  
**Người nhận:** team FE  
**Phản hồi:** `BE-NEED-quan-ly-gia-thue-2026-08-15.md`  
**Phạm vi:** không chặn lệch giá (deal 1tr / niêm yết 10tr vẫn hợp lệ)

---

## Tóm tắt

| # | Spec | Trạng thái |
|---|------|------------|
| 3.1 | Hai trường `listedPrice` / `appliedPrice` | ✅ Done |
| 3.2 | Khoá giá niêm yết theo **đơn vị đang có khách**, endpoint riêng | ✅ Done |
| 3.3 | Import không chặn ngưỡng + dry-run đối chiếu + notify Host ngay | ✅ Done |
| 3.4 | Điều khoản tăng giá structured trên HĐ + job tự áp | ✅ Done |
| 3.5 | Quay về niêm yết khi checkout `COMPLETED` / thanh lý | ✅ Done |
| 3.6 | Lịch sử giá 4 loại | ✅ Done |
| 4 | API PATCH giá + GET history + giá trên dry-run import | ✅ Done |
| 5 | Lưu file Excel gốc + ô cam kết admin | ❌ Chưa làm (đợt sau) |

Hoá đơn / công nợ / P&L **vẫn lấy `contract.rentAmount`**. `appliedPrice` trên phòng/nhà là bản denormalize để dashboard đối chiếu niêm yết vs giá đang chạy.

---

## 1. Mô hình giá

| Trường | Cột DB | Ý nghĩa | Ai đổi |
|---|---|---|---|
| **listedPrice** | `rooms.price` / `properties.price` | Giá host duyệt, giá bán | Host/Admin, **chỉ khi đơn vị trống** |
| **appliedPrice** | `rooms.applied_price` / `properties.applied_price` | Giá HĐ hiện hành | Hệ thống (HĐ / điều khoản / trả phòng) |

Phòng trống: `appliedPrice = listedPrice`.  
Response vẫn trả `price` (= listed) để FE cũ không vỡ; thêm `listedPrice`, `appliedPrice`, `priceLocked`.

Backfill lúc start app:

- Trống → `applied_price = price`
- HĐ `ACTIVE` / `EXPIRED` → `applied_price = rent_amount`

---

## 2. API mới

Quyền: `OWNER`, `ADMIN`. Lỗi nghiệp vụ → **422**.

### Đổi giá niêm yết — phòng

```
PATCH /api/v1/properties/{propertyId}/rooms/{roomId}/price
```

```json
{ "price": 10000000, "reason": "phòng trống 2 tháng, hạ giá" }
```

- `reason` bắt buộc.
- **422** nếu phòng đang có HĐ `ACTIVE` hoặc `EXPIRED` (chưa thanh lý / checkout xong).
- Nhà nguyên căn: 422 — dùng API dưới.

`PUT /api/v1/properties/{id}/rooms/{roomId}` **không nới**. Diện tích, công tơ, mã phòng vẫn chỉ sửa khi nhà onboarding.

### Đổi giá niêm yết — nguyên căn

```
PATCH /api/v1/properties/{id}/price
```

Cùng body. 422 nếu căn đang có khách.

### Lịch sử giá

```
GET /api/v1/properties/{id}/price-history
GET /api/v1/properties/{id}/price-history?roomId=13
```

```json
{
  "id": 1,
  "propertyId": 27,
  "roomId": 13,
  "roomNumber": "101",
  "changeType": "HOP_DONG",
  "changeTypeLabel": "HỢP ĐỒNG",
  "oldPrice": 10945000,
  "newPrice": 5800000,
  "contractId": 42,
  "reason": "HĐ HD-2026-00042 · Trần Văn A",
  "changedByName": "admin01",
  "changedAt": "2026-08-15T19:00:00"
}
```

`changeType`: `HOP_DONG` | `DIEU_KHOAN_HD` | `TU_DONG` | `HOST_DOI`

---

## 3. Response đã thêm field

### Phòng / nhà

```json
{
  "price": 10945000,
  "listedPrice": 10945000,
  "appliedPrice": 5800000,
  "priceLocked": true
}
```

`priceLocked = true` khi đơn vị có HĐ `ACTIVE`/`EXPIRED` → FE disable ô sửa niêm yết.

### Hợp đồng (`TenantContractResponse`)

- `listedPrice` — giá niêm yết đơn vị tại thời điểm đọc (để FE khối đỏ đối chiếu)
- `rentEscalationType` — `NONE` | `PERCENT` | `SCHEDULE`
- `rentEscalationPercent`
- `rentScheduleJson`

ROLE_MANAGER vẫn **không** thấy số tiền (`rentAmount` / `listedPrice` = null) — giữ chính sách cũ.

### Import Excel HĐ nháp (dry-run **và** import thật)

`BulkImportContractResultResponse` thêm:

| Field | Dùng cho bảng đối chiếu |
|---|---|
| `listedPrice` | Giá duyệt / niêm yết |
| `rentAmount` | Giá trong file |
| `deltaPercent` | vd. `-47.0` |
| `roomId` / `roomNumber` | Đơn vị |
| `tenantName` | Khách |

**Không chặn** theo %. Chỉ 400 khi validation file: giá ≤ 0, rỗng, không phải số, trùng phòng, v.v.

---

## 4. Notify Host (lớp phát hiện duy nhất)

Bắn **ngay** khi `appliedPrice` đổi vì HĐ (tạo/import/sửa nháp) hoặc điều khoản tăng giá — không gom digest.

In-app `notifications` + `host_notifications` + Expo push.

Mẫu:

```
⚠ Giá phòng 101 vừa đổi:  10.945.000đ  →  5.800.000đ   (−47.0%)
   Theo HĐ HD-… · Trần Văn A · import/tạo HĐ bởi admin01
```

`data.screen = ContractDetail`, `params.contractId`.

Khi trả phòng / thanh lý:

```
Phòng 101 đã trống, giá quay về 10.000.000đ (đặt từ 02/06/2026)
— kiểm tra lại giá trước khi đăng.
```

`data.screen = PropertyDetail`.

---

## 5. Tăng giá theo năm

Nằm trên HĐ, không phải nút Host.

| `rentEscalationType` | Dữ liệu |
|---|---|
| `NONE` | mặc định |
| `PERCENT` | `rentEscalationPercent` = 5 → năm 2 = base × 1.05, năm 3 × 1.05² |
| `SCHEDULE` | `[{ "fromMonth": 13, "amount": 11000000 }]` |

Excel (cột **không bắt buộc**; thiếu = `NONE`):

| Cột | Ví dụ |
|---|---|
| Loại tăng giá | `NONE` / `PERCENT` / `SCHEDULE` |
| % tăng/năm | `5` |
| Lịch tăng giá | `13:11000000;25:12000000` hoặc JSON |

Job daily (trong `runDailySweep`, **trước** phát hoá đơn tháng): tự đổi `rentAmount` + `appliedPrice`, lịch sử `DIEU_KHOAN_HD`. **Không hồi tố** hoá đơn đã phát.

Onboard / sửa nháp nhận thêm:

```json
{
  "rentEscalationType": "PERCENT",
  "rentEscalationPercent": 5,
  "rentSchedule": [{ "fromMonth": 13, "amount": 11000000 }]
}
```

---

## 6. Quay về niêm yết

Gọi khi:

- Checkout tenant **complete** → `terminateActiveContract`
- Thanh lý HĐ (`/terminate`)
- Huỷ / xoá HĐ chưa ACTIVE (nháp) nếu đơn vị không còn HĐ khác đang chiếm

**Không** quay về lúc HĐ hết hạn (`EXPIRED`) — vẫn cần giá HĐ cho checkout / quyết toán.

Nếu đơn vị đã có HĐ khác `ACTIVE`/`EXPIRED` thì không ghi đè.

---

## 7. Việc FE nên làm

1. Màn BĐS: hiện **niêm yết** vs **đang áp dụng**; khoá sửa khi `priceLocked`.
2. Đổi giá trống → `PATCH .../price` (kèm `reason`), **đừng** dùng `PUT rooms`.
3. Timeline giá → `GET .../price-history`.
4. Dry-run import: bảng `listedPrice → rentAmount (deltaPercent)` trước khi bấm import.
5. Form HĐ: gửi điều khoản tăng giá structured (không chỉ chữ trong PDF).
6. Chuông Host: type `UNIT_PRICE_CHANGED` / restore — deep link `ContractDetail` / `PropertyDetail`.

---

## 8. Chưa làm

- Lưu file Excel gốc (hash) + bảng `contract_attestations` (ô cam kết admin) — spec mục 5.
- Báo cáo “đổi giá trong tháng” riêng: dùng `GET price-history` lọc `changedAt` phía FE là đủ.
