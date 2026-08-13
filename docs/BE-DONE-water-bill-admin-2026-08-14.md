# BE DONE — module hoá đơn NƯỚC cho admin (gộp với EVN)

**Ngày:** 14/08/2026  
**Người nhận:** FE (web admin + mobile)  
**Phản hồi:** `BE-NEED-water-bill-admin-2026-08-14.md`

---

## Tóm tắt

Không tạo bảng `water_bills` riêng. Giữ `evn_bills`, thêm cột `type` (`ELECTRIC` | `WATER`).  
API mới `utility-bills` đúng contract FE đã code. API EVN cũ **giữ nguyên**.

| # | Việc FE xin | Trạng thái |
|---|---|---|
| 1 | POST/GET/DELETE admin + GET manager `utility-bills` | ✅ |
| 2 | Body `type` + `totalQuantity` | ✅ |
| 3 | `unitPrice` chưa làm tròn + `unitPriceExact` | ✅ |
| 4 | Sai số thành tiền ±1đ (lỗi 199 kWh × 2.008đ) | ✅ |
| 5 | Ảnh công tơ nước | ✅ **(A)** — vẫn bắt buộc, giống điện |

---

## API mới (FE `waterBill.service.ts` gọi được ngay)

| Method | Path | Role |
|---|---|---|
| POST | `/api/v1/admin/utility-bills` | ADMIN |
| GET | `/api/v1/admin/utility-bills?propertyId&month&year&type` | ADMIN |
| DELETE | `/api/v1/admin/utility-bills/{id}` | ADMIN — REVOKED |
| GET | `/api/v1/manager/utility-bills?propertyId&month&year&type` | MANAGER — chỉ PUBLISHED, đúng nhà mình |

`type` query: `WATER` | `ELECTRIC` | `ELECTRICITY`. Bỏ trống = cả hai.

### POST body

```json
{
  "propertyId": 9,
  "type": "WATER",
  "billingPeriod": "01/09 – 30/09/2026",
  "month": 9,
  "year": 2026,
  "totalQuantity": 42,
  "totalAmount": 512000,
  "imageUrl": "https://res.cloudinary.com/..."
}
```

`unitPrice` BE tính = `totalAmount / totalQuantity` **scale 8, không làm tròn về VND nguyên**.

### Response (mỗi phần tử)

```json
{
  "id": 1,
  "propertyId": 9,
  "propertyName": "Nhà A",
  "type": "WATER",
  "billingPeriod": "01/09 – 30/09/2026",
  "month": 9,
  "year": 2026,
  "totalQuantity": 42,
  "totalKwh": null,
  "totalAmount": 512000,
  "unitPrice": 12190.47619048,
  "unitPriceExact": 12190.47619048,
  "imageUrl": "...",
  "status": "PUBLISHED",
  "createdBy": "admin",
  "createdAt": "2026-08-14T00:00:00"
}
```

- `type` response luôn `ELECTRICITY` | `WATER` (cùng mapper hoá đơn phòng).
- Điện: `totalKwh` = `totalQuantity`. Nước: `totalKwh` = `null`.
- `unitPrice` === `unitPriceExact` (đủ chữ số để FE nhân ra khớp tổng).

### Error code (HTTP 422)

| Code | Khi nào |
|---|---|
| `UTILITY_BILL_ALREADY_EXISTS` | Đã có PUBLISHED cùng nhà + tháng + năm + type |
| `UTILITY_BILL_IN_USE` | Thu hồi nhưng kỳ đó đã gửi hoá đơn điện/nước cho khách |

Manager sai nhà → **403** `FORBIDDEN` (giống EVN).

---

## API EVN cũ — không gãy

`/api/v1/admin/evn-bills` và `/api/v1/manager/evn-bills` vẫn chạy.  
List EVN **chỉ trả ELECTRIC**. Code lỗi vẫn `EVN_BILL_ALREADY_EXISTS` / `EVN_BILL_IN_USE`.

Response EVN có thêm `type`, `totalQuantity`, `unitPriceExact` — FE cũ bỏ qua cũng được.

---

## Ảnh công tơ nước = phương án **(A)**

`ensureMeterPhotoOrOverride` **giữ** cho `WATER`. Gửi hoá đơn nước vẫn cần:

- `meterImageUrl` / ảnh công tơ kỳ này, **hoặc**
- override token (kind `WATER`) + `overrideReason`

Không có thì 422 `METER_PHOTO_REQUIRED`:  
`"Chưa có ảnh công tơ kỳ này — không thể phát hành hoá đơn điện/nước."`

FE mobile: bổ sung bước chụp ảnh công tơ nước giống tab Điện.

---

## Thành tiền không khớp — đã nới

`validateInvoiceAmounts` cho phép **|consumption × unitPrice − amount| ≤ 1đ**.

| Code mới | Message cũ (không đổi) |
|---|---|
| `AMOUNT_MISMATCH` | Thành tiền không khớp (tiêu thụ × đơn giá) |
| `CONSUMPTION_MISMATCH` | Tiêu thụ không khớp (chỉ số mới − chỉ số cũ) |
| `READING_ORDER_INVALID` | Chỉ số mới phải lớn hơn hoặc bằng chỉ số cũ |

FE nên gửi `unitPriceExact` từ hoá đơn admin, `amount` = làm tròn 2 số của tích. Lệch 1đ vẫn qua.

---

## Notify manager khi admin chốt nước

| | Điện | Nước |
|---|---|---|
| `type` | `EVN_BILL_PUBLISHED` | `WATER_BILL_PUBLISHED` |
| `screen` | `UtilityBilling` | `UtilityBilling` |
| `params.propertyId` | có | có |

---

## OCR

`POST /api/v1/ocr/evn-bill` **không đổi tên**. Parser nước dùng lại rawText như FE nói.  
Không có `/ocr/utility-bill` trong đợt này.

---

## Schema

Bảng vẫn `evn_bills`:

- `type VARCHAR(20) NOT NULL DEFAULT 'ELECTRIC'`
- `total_kwh` = tổng kWh **hoặc** m³ (`totalQuantity` trên API)
- `unit_price NUMERIC(19,8)`
