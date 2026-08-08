# BE Ready — FE update guide (mentor feedback 07–08/08/2026)

**Ngày:** 08/08/2026  
**Từ:** BE team  
**Cho:** FE (mobile manager/tenant + web admin)  
**Map với:** `PLAN-mentor-feedback-2026-08-08.md`

> Backend đã xong (hoặc bổ sung) các phần BE trong plan mentor. Doc này là **hợp đồng API** để FE bám và cập nhật màn hình / service layer. Không cần đợi thêm BE trừ mục ghi chú ở cuối.

---

## 1. N1 — Tiền onboard để lại dấu (ý 10–12, 14)

### Đã có trên BE

Khi PayOS webhook gọi `markDepositPaid` / `completeDepositPayment`:

| Việc | Trạng thái |
|---|---|
| `paymentStatus = PAID`, `paidAt`, `depositPaidAt`, `depositMethod` | ✅ |
| `TenantInvoice` code `HD-ONBOARD-{contractId}`, status `PAID` | ✅ |
| `TenantPayment` gắn invoice | ✅ |
| `note` encode 2 cấu phần | ✅ `ONBOARD\|rentAmount=…\|depositAmount=…\|depositMonths=…` |
| Push tenant `DEPOSIT_PAID_TENANT` → screen `InvoiceList` | ✅ (có số tiền) |
| Push manager `DEPOSIT_PAID_MANAGER` → screen `ResumeContract` | ✅ (**không** kèm số tiền) |
| Idempotent (retry webhook / backfill) | ✅ |

`TenantContractResponse` đã có:

- `deposit` / `rentAmount` / `initialPaymentAmount` (rent + deposit)
- `paymentStatus`, `paidAt`, `depositPaidAt`, `depositMethod`
- `payosCheckoutUrl`, `payosQrCode`, `payosOrderCode`

### FE cần làm

**Tenant**

1. Màn Home + Hợp đồng: thẻ **"Đã thanh toán khi nhận phòng"**.
2. Data:
   - Ưu tiên list hóa đơn tenant: code bắt đầu `HD-ONBOARD-` **hoặc** `billingPeriod` chứa `"Thu lúc nhận phòng"`.
   - Render `items[]` từ `GET /api/v1/tenant/invoices/{id}` (hoặc list nếu đã trả `items`):
     - `Tiền nhà tháng đầu`
     - `Tiền cọc (N tháng)`
3. Lịch sử thanh toán: `GET` payments của tenant — bản ghi method `PAYOS` / amount = `initialPaymentAmount`.

**Manager**

1. `ResumeContractScreen`: badge **"✅ Đã thu"** khi `paymentStatus === 'PAID'` (hoặc `depositPaidAt != null`).
2. Ý 14 (số tiền QR): **không sửa** — PayOS đã fix cứng. Bảng tách rent/deposit trên màn QR giữ như hiện tại.

---

## 2. Chi tiết hóa đơn manager/admin — `items[]` (ý 17, web)

### Endpoint mới

```http
GET /api/v1/manager/invoices/{id}
Authorization: Bearer …   # ROLE_MANAGER | ROLE_ADMIN
```

**Response** (`ManagerInvoiceResponse` + items):

```json
{
  "id": 12,
  "code": "HD-ONBOARD-42",
  "type": "RENT",
  "propertyId": 3,
  "propertyName": "…",
  "roomNumber": "101",
  "tenantName": "…",
  "month": null,
  "year": null,
  "billingPeriod": "Thu lúc nhận phòng 2026-08-01",
  "amount": 15000000,
  "status": "PAID",
  "dueDate": null,
  "createdAt": "…",
  "paidAt": "…",
  "paymentMethod": "PAYOS",
  "transactionId": "123456",
  "items": [
    { "label": "Tiền nhà tháng đầu", "amount": 5000000 },
    { "label": "Tiền cọc (2 tháng)", "amount": 10000000 }
  ]
}
```

List `GET /api/v1/manager/invoices` **không** đính `items` (payload nhẹ).  
Web admin: detail drawer/page gọi endpoint trên.

Tenant API cũ **vẫn** trả `items` trên list + detail.

---

## 3. N2 — OCR đồng hồ

### BE

- Vision OCR meter: `DOCUMENT_TEXT_DETECTION` (thay `TEXT_DETECTION`) — tốt hơn cho LCD.
- Endpoint không đổi: `POST /api/v1/ocr/meter` body `{ "imageUrl": "…" }`.
- **Không** làm tròn phía BE — FE tự split số + làm tròn theo rule mentor (`redDigit > 5` mới lên, **bằng 5 thì xuống**).

### Config chữ số / phòng (mới)

`RoomResponse` (+ create/update room):

| Field | Default |
|---|---|
| `elecIntegerDigits` | 5 |
| `elecDecimalDigits` | 1 |
| `waterIntegerDigits` | 5 |
| `waterDecimalDigits` | 3 |

- `GET /api/v1/properties/{id}/rooms` / get room — đọc để vẽ ô số tách.
- `POST` / `PUT` room — optional; null → BE default khi save.

FE (Onboarding / meterPhoto):

1. Lấy config phòng trước khi OCR.
2. Cắt chuỗi digits theo integer/decimal; có dấu `.` trong OCR → tin dấu đó.
3. UI vạch ngăn kéo được + confirm manager.

---

## 4. N3 — Meter override passcode (ý 5)

### Verify

```http
POST /api/v1/manager/meter-override/verify
{
  "passcode": "…",
  "contractId": 42,
  "meterKind": "ELEC"   // hoặc WATER; ELECTRIC/ELECTRICITY cũng accept → ELEC
}
```

**200**

```json
{
  "valid": true,
  "overrideToken": "uuid",
  "expiresAt": "2026-08-08T18:00:00",
  "message": "OK"
}
```

**403** `{ "valid": false, "message": "Mã không đúng…" }`  
**429** lock 5 phút sau 5 lần sai.

### Gửi kèm khi onboard / update draft

```json
{
  "initialElectricReading": 3082,
  "electricMeterImageUrl": null,
  "electricMeterOverrideToken": "<uuid từ verify>",
  "electricMeterOverrideReason": "Camera hỏng — không chụp được",
  "initialWaterReading": 12.5,
  "waterMeterOverrideToken": "…",
  "waterMeterOverrideReason": "…"
}
```

Rule BE: token **chỉ consume** khi **không có** ảnh meter tương ứng. Có ảnh → bỏ qua override.  
Reason bắt buộc. Token one-time, TTL default 15 phút (`MANAGER_OVERRIDE_TTL_MINUTES`).

### Admin audit

```http
GET /api/v1/admin/meter-overrides
```

Env demo: `MANAGER_OVERRIDE_PASSCODE` (xem `.env` server).

### UI gợi ý

- Nút *"Không chụp được — xin mã từ quản trị"* → modal passcode + lý do → enable ô nhập tay + nhãn đỏ `"Nhập tay có mã"`.
- Fail-open khi Vision chết: vẫn cho nhập tay (local) nhưng nếu có BE thì ưu tiên passcode.

---

## 5. N4 — HĐ đang đón dở (`PENDING`)

BE **đã** hỗ trợ; chỉ FE gọi thêm:

```http
GET /api/v1/manager/contracts?status=PENDING
```

(Cùng pattern `status=DRAFT`.)

Search local: thêm phone, roomNumber, contractCode.

---

## 6. N5 — UI hình thức thu cọc

Chỉ FE: xóa chip "Hình thức thu cọc" — không API.

---

## 7. N6 — Tiến độ bàn giao (web admin)

```http
# Bảng tóm tắt mọi toà (rooms = null)
GET /api/v1/admin/handover-status
Authorization: ADMIN

# Chi tiết 1 toà (có rooms[])
GET /api/v1/admin/handover-status?propertyId=3
```

**Tóm tắt / detail fields:**

| Field | Ý nghĩa |
|---|---|
| `propertyStatus` | VD `ACTIVE`, `PENDING_OPERATION_MANAGER` |
| `operationManagerName` | QL vận hành |
| `managerAcceptedAt` | Mốc nhận nhà từ host |
| `totalRooms` / `roomsHandedOver` | X/Y phòng đã giao (HĐ ACTIVE) |
| `rooms[].conditionPhotoCount` | Ảnh hiện trạng |
| `rooms[].hasMeterReadings` | Có chỉ số điện/nước |
| `rooms[].moveInDate`, `activatedAt` | Ngày vào ở / kích hoạt |

Trang **mới** — không sửa `BillingPaymentMonitoring.tsx`.

---

## 8. N7 — Seed + contract code

- Seeder demo: multi-status contracts + hóa đơn onboard PAID + `TenantPayment`.
- `generateContractCode()` random + check unique (không còn `count()+1`) → seed 50 HĐ rồi onboard thật **không** đụng mã.

Accounts seed mặc định: password `123456`.

---

## 9. Push token

```http
POST   /api/v1/user/me/push-token   { "pushToken": "ExponentPushToken[…]" }
DELETE /api/v1/user/me/push-token   // logout
```

Payload deep-link gửi kèm notify (đã wire BE):

| type | screen gợi ý |
|---|---|
| `DEPOSIT_PAID_TENANT` | `InvoiceList` |
| `DEPOSIT_PAID_MANAGER` | `ResumeContract` + `params.contractId` |

FCM credentials + rebuild APK vẫn do FE/ops (ngoài BE).

---

## 10. Checklist FE theo vai

### Mobile — FE onboard / meter (author plan)

- [ ] `listManagedContracts('PENDING')` + search mở rộng + group trạng thái
- [ ] Xóa UI "Hình thức thu cọc"
- [ ] Tách decimal digits theo `RoomResponse.*Digits`; làm tròn `>5`
- [ ] Nới filter ảnh nước (FE only)
- [ ] Override passcode flow + fields request
- [ ] Tenant: thẻ đã trả nhận phòng (items từ invoice)
- [ ] Manager: badge đã thu
- [ ] Push pipe / logout DELETE token (nếu chưa)
- [ ] Fail-open OCR

### Web — FE billing admin (bạn kia)

- [ ] `GET /manager/invoices/{id}` → render `items[]` (onboard 2 dòng)
- [ ] List invoice đã PAID sau onboard (code `HD-ONBOARD-*`)

### Web — FE handover (author)

- [ ] Trang mới: list `/admin/handover-status` + drill-down `?propertyId=`

---

## 11. Không đụng / out of BE scope lần này

| Mục | Ghi chú |
|---|---|
| Ý 15 ẩn tiền manager | FE only (constants) |
| RENT_* notification copy | FE content; BE only pipe + deposit types |
| ML Kit offline | FE native build |
| Làm tròn chỉ số server-side | FE quyết (điện nguyên / nước 1 lẻ) — BE lưu `BigDecimal` |

---

## 12. Smoke test nhanh (Postman)

1. Manager tạo HĐ + `POST …/contracts/{id}/deposit-payment` → QR.  
2. Giả lập webhook PayOS (hoặc env sandbox) → check:
   - contract `paymentStatus=PAID`, `depositPaidAt` set  
   - `GET` tenant invoices có `HD-ONBOARD-*`, `items` length 2  
   - notification repo + push nếu có token  
3. `POST /manager/meter-override/verify` → token → update draft không ảnh + reason → `GET /admin/meter-overrides` thấy log.  
4. `GET /admin/handover-status` → list; `?propertyId=` → rooms.  
5. `GET /manager/invoices/{onboardId}` → items.

---

## Liên hệ BE nếu

- Webhook PayOS local không bắn → cần tunnel / manual complete (hỏi BE script).  
- Passcode 403 "Chưa cấu hình MANAGER_OVERRIDE_PASSCODE" → set env server.  
- `items` rỗng trên hóa đơn cũ không có `note ONBOARD|` → chỉ áp dụng HĐ/payment sau bản BE này + seed mới.

**BE sẵn sàng cho FE cập nhật theo checklist §10.**
