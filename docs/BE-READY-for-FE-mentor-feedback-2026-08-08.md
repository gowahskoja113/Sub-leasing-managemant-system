# BE Ready — FE update guide (mentor feedback 07–08/08/2026)

**Ngày:** 08/08/2026 (cập nhật push multi-device + CONTRACT_ACTIVATED / PRICE_APPROVAL_RESULT)  
**Từ:** BE team  
**Cho:** FE (mobile manager/tenant + web admin)  
**Map với:** `PLAN-mentor-feedback-2026-08-08.md` + `SETUP-push-notifications-2026-08-08.md` (FE)

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
| Push tenant `CONTRACT_ACTIVATED` sau OTP | ✅ → `ContractDetail` |
| Push manager `PRICE_APPROVAL_RESULT` (host duyệt/từ chối giá) | ✅ (**không** số tiền) |
| Multi-device push token (`user_push_tokens`) | ✅ |
| `DELETE /user/me/push-token` khi logout | ✅ |
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

### Cơ chế (OTP do admin gen — không còn mã env cố định)

```text
Manager kẹt (không chụp được) → gọi admin
Admin: POST /api/v1/admin/meter-override/passcodes  →  mã 6 số, TTL ~10'
Admin gửi mã cho manager (chat/điện thoại)
Manager: POST /api/v1/manager/meter-override/verify  + mã đó
  → mã OTP chết ngay (1 lần)
  → nhận overrideToken (UUID, TTL ~15', 1 lần dùng lúc submit chỉ số)
Submit onboard kèm overrideToken + reason
```

### Admin gen mã

```http
POST /api/v1/admin/meter-override/passcodes
Authorization: Bearer <admin>
{ "ttlMinutes": 10, "note": "Manager An — P.302" }   // body optional
```

**200**

```json
{
  "id": 1,
  "code": "482910",
  "expiresAt": "…",
  "usable": true,
  "message": "Gửi mã này cho manager. Mã dùng 1 lần, hết hạn sau 10 phút."
}
```

```http
GET /api/v1/admin/meter-override/passcodes?activeOnly=true
```

Env: `MANAGER_OVERRIDE_PASSCODE_TTL_MINUTES` (mặc định 10), `MANAGER_OVERRIDE_TTL_MINUTES` (token sau verify, mặc định 15).  
**Không còn** `MANAGER_OVERRIDE_PASSCODE` cố định.

### Manager verify

```http
POST /api/v1/manager/meter-override/verify
{
  "passcode": "482910",
  "contractId": null,   // null khi đang đón khách mới (HĐ chưa tạo)
  "meterKind": "ELEC"   // hoặc WATER
}
```

**200**

```json
{
  "valid": true,
  "overrideToken": "uuid",
  "expiresAt": "…",
  "message": "OK"
}
```

**403** `{ "valid": false, "message": "Mã không đúng hoặc đã hết hạn…" }`  
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

Env: `MANAGER_OVERRIDE_PASSCODE_TTL_MINUTES` / `MANAGER_OVERRIDE_TTL_MINUTES` (admin gen OTP; không còn mật khẩu env cố định).

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

## 9. Push notification (bổ sung 08/08 — FE handoff)

### API token (multi-device)

```http
POST   /api/v1/user/me/push-token
Authorization: Bearer …
Content-Type: application/json

{ "pushToken": "ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]" }
```

→ Lưu token vào bảng `user_push_tokens` (1 account → nhiều máy).  
Cột `User.push_token` = token mới nhất (dùng check nhanh / back-compat).

```http
DELETE /api/v1/user/me/push-token
Authorization: Bearer …
```

| Body | Hành vi |
|---|---|
| *(không body)* | Gỡ **tất cả** token của account (logout) |
| `{ "pushToken": "…" }` | Gỡ **đúng máy** đó |

Response: `{ "success": true }`

### Deep-link payload (field `data` gửi kèm Expo push)

| type | Người nhận | Khi nào | `screen` / params |
|---|---|---|---|
| `DEPOSIT_PAID_TENANT` | tenant | PayOS / completeDeposit (có số tiền) | `InvoiceList` |
| `DEPOSIT_PAID_MANAGER` | manager | cùng lúc ( **không** số tiền) | `ResumeContract` + `params.contractId` |
| `CONTRACT_ACTIVATED` | tenant | OTP confirm → HĐ `ACTIVE` | `ContractDetail` + `params.contractId` |
| `PRICE_APPROVAL_RESULT` | manager | host duyệt/từ chối giá (không số tiền) | `ResumeContract` + `params.contractId` + `approved` bool |
| `TENANT_ONBOARDING` | manager | được gán tiếp nhận khách | `ResumeContract` + `params.contractId` |
| `TENANT_CONTRACT_NO_SHOW` | manager | HĐ auto-hủy no-show | `ResumeContract` + `params.contractId` |
| `MAINTENANCE` | tenant/manager | đổi trạng thái bảo trì | `MaintenanceDetail` + `requestId` |
| Billing types (RENT_*, …) | tenant | cron nhắc/quá hạn | `InvoiceList` + `invoiceId` |

In-app: cùng lúc ghi vào bảng `notifications` (list `GET` notify hiện có).

### Chính sách ẩn tiền (ý mentor 15)

- Tin **manager** (`DEPOSIT_PAID_MANAGER`, `PRICE_APPROVAL_RESULT`): body **không** chứa số tiền.
- Tin **tenant** (`DEPOSIT_PAID_TENANT`, billing): **có** số tiền khi liên quan thanh toán.

### FE / ops ngoài BE (bắt buộc mới nhận tin thật trên Android)

1. FCM V1 credentials nạp EAS (`eas credentials`) + `google-services.json` package `com.pinkyusteam.sep`.
2. Build native (`eas build -p android --profile preview`) — **không** chạy remote push trên Expo Go SDK 53.
3. Sau login gọi `POST …/push-token`; logout gọi `DELETE …/push-token`.
4. deep-link: đọc `data.type` / `data.screen` / `data.params` (đã có `navigateFromNotification` phía FE).

### Kiểm tra nhanh

```sql
-- legacy (latest)
SELECT username, push_token FROM "User" WHERE push_token IS NOT NULL;
-- multi-device
SELECT user_id, token, updated_at FROM user_push_tokens;
```

```bash
curl -X POST https://exp.host/--/api/v2/push/send \
  -H "Content-Type: application/json" \
  -d '{"to":"ExponentPushToken[…]","title":"Test","body":"Xin chào","data":{"type":"DEPOSIT_PAID_TENANT","screen":"InvoiceList"}}'
```

| `details.error` | Ý nghĩa |
|---|---|
| `DeviceNotRegistered` | Token cũ — login lại |
| `InvalidCredentials` | Chưa nạp FCM lên EAS |
| `MessageTooBig` | Payload > 4KB |

## 10. Checklist FE theo vai

### Mobile — FE onboard / meter (author plan)

- [ ] `listManagedContracts('PENDING')` + search mở rộng + group trạng thái
- [ ] Xóa UI "Hình thức thu cọc"
- [ ] Tách decimal digits theo `RoomResponse.*Digits`; làm tròn `>5`
- [ ] Nới filter ảnh nước (FE only)
- [ ] Override passcode flow + fields request
- [ ] Tenant: thẻ đã trả nhận phòng (items từ invoice)
- [ ] Manager: badge đã thu
- [ ] Push: đăng ký token sau login; logout `DELETE /user/me/push-token` (body optional = gỡ 1 máy)
- [ ] Push deep-link: handle `CONTRACT_ACTIVATED` → `ContractDetail`, `PRICE_APPROVAL_RESULT` → `ResumeContract`
- [ ] FCM + EAS credentials + rebuild APK native (không Expo Go)
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
| Ý 15 ẩn tiền manager (UI copy) | FE constants; BE push manager đã ẩn số tiền |
| RENT_* notification copy text | FE content; BE only pipe + schedule |
| ML Kit offline | FE native build |
| Làm tròn chỉ số server-side | FE quyết (điện nguyên / nước 1 lẻ) — BE lưu `BigDecimal` |
| FCM key / google-services.json | FE/ops (không commit secret) |

---

## 12. Smoke test nhanh (Postman)

1. Manager tạo HĐ + `POST …/contracts/{id}/deposit-payment` → QR.  
2. Giả lập webhook PayOS (hoặc env sandbox) → check:
   - contract `paymentStatus=PAID`, `depositPaidAt` set  
   - `GET` tenant invoices có `HD-ONBOARD-*`, `items` length 2  
   - `notifications` + push `DEPOSIT_PAID_*` nếu user có token  
3. OTP confirm HĐ → notification `CONTRACT_ACTIVATED` + push tenant.  
4. Host approve/reject giá → manager nhận `PRICE_APPROVAL_RESULT` (không số tiền).  
5. `POST /manager/meter-override/verify` → token → update draft không ảnh + reason → `GET /admin/meter-overrides` thấy log.  
6. `GET /admin/handover-status` → list; `?propertyId=` → rooms.  
7. `GET /manager/invoices/{onboardId}` → items.  
8. `POST/DELETE /user/me/push-token` + row trong `user_push_tokens`.

---

## Liên hệ BE nếu

- Webhook PayOS local không bắn → cần tunnel / manual complete (hỏi BE script).  
- Passcode 403 "Mã không đúng hoặc đã hết hạn" → admin gen mã mới (`POST /admin/meter-override/passcodes`).
- `items` rỗng trên hóa đơn cũ không có `note ONBOARD|` → chỉ áp dụng HĐ/payment sau bản BE này + seed mới.

**BE sẵn sàng cho FE cập nhật theo checklist §10.**
