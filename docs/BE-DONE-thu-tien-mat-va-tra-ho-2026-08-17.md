# BE DONE — Thu tiền mặt & Trả hộ hoá đơn tiền nhà

**Ngày:** 17/08/2026  
**Người gửi:** team BE  
**Người nhận:** team FE (mobile tenant/manager + web admin/host)  
**Tham chiếu spec:** `BE-NEED-thu-tien-mat-va-tra-ho-2026-08-16.md`

---

## Tóm tắt

BE đã triển khai **A1 → A3 → A2/B3 → B1/B2 → C2a/C2b** theo thứ tự trong spec.  
**Chưa làm:** C2c (Web Push trình duyệt cho host/admin), forgot-password, `expectedReceptionDate` import Excel.

| Hạng mục | Trạng thái BE | FE cần làm |
|----------|---------------|------------|
| A1 — Order PayOS 1–n | ✅ | Không (transparent) |
| A3 — Bịt rò tiền manager | ✅ | Cập nhật UI deposits/invoices |
| A2+B3 — Passcode + QR manager | ✅ | **Màn mới** admin + manager |
| B1 — Thu tiền mặt | ✅ | Nút + luồng 6 bước |
| B2 — Trả hộ | ✅ | Form tên người trả hộ + QR 15 phút |
| C2a — Chuông in-app | ✅ | Hiển thị notification mới |
| C2b — Push Expo tenant | ✅ | Đã có infra — tự nhận |
| C2c — Web Push host/admin | ❌ tách issue | Service worker + VAPID |
| Realtime bổ sung context | ✅ | Hiện dòng “tiền mặt / trả hộ” |

---

## 1. A1 — PayOS order code 1–n (BE only, FE không đổi API tenant)

### Vấn đề cũ

Mỗi lần bấm “Tạo QR” ghi đè `payos_order_code` trên hoá đơn → webhook từ mã cũ không map được → **mất tiền**.

### BE đã sửa

- Bảng mới `tenant_invoice_payos_orders`: mọi order code từng phát đều map về `invoice_id`.
- Webhook tra bảng này trước, fallback cột legacy trên `tenant_invoices`.
- Tạo QR mới → order cũ `SUPERSEDED`, order mới `ACTIVE` — **cả hai vẫn hợp lệ khi webhook về**.
- Thanh toán trùng (hoá đơn đã PAID) → admin nhận `PAYMENT_OVERPAYMENT_ADMIN`.

### FE

| Vai | Việc |
|-----|------|
| Tenant | Không đổi — vẫn `POST /tenant/me/invoices/{id}/payment` |
| Manager (luồng mới) | Dùng API mới ở mục 3 — có nút **“Tạo mã mới”** khi QR hết hạn |

---

## 2. A3 — Bịt rò tiền manager (breaking change response)

### 2.1 `GET /api/v1/manager/deposits`

**Trước:**

```json
{
  "deposit": 9500000.00,
  "depositMonths": 1,
  "tenantPhone": "0932892123"
}
```

**Sau:**

```json
{
  "tenantPhone": "093****123",
  "paymentStatus": "PAID",
  "depositPaidAt": "2026-08-01T10:00:00",
  "depositMethod": "PAYOS"
}
```

| Field | Thay đổi |
|-------|----------|
| `deposit` | **Bỏ** — không trả nữa |
| `depositMonths` | **Bỏ** — không trả nữa |
| `tenantPhone` | **Che** — `093****123` (3 đầu + 3 cuối) |
| `paymentStatus`, `depositPaidAt`, `depositMethod` | Giữ — manager biết *đã đóng cọc chưa*, không biết *bao nhiêu* |

**FE mobile manager — màn Deposits:**

- [ ] Gỡ cột/hàng hiển thị số cọc, số tháng cọc.
- [ ] Không cố parse `deposit / depositMonths` để suy giá thuê.
- [ ] Hiển thị badge trạng thái cọc từ `paymentStatus` + `depositMethod`.

---

### 2.2 `GET /api/v1/manager/invoices` và `GET /api/v1/manager/invoices/{id}`

**Trước:** Chỉ mask khi `invoiceType == RENT` → hoá đơn `HD-ONBOARD-*` (type `OTHER`) lộ số tiền sau khi trừ cọc.

**Sau:** Mask khi:

- `code` bắt đầu bằng `HD-ONBOARD-`, **hoặc**
- `invoiceType == RENT`

→ Manager thường thấy `amount`, `totalAmount`, `lateFee` = **`null`** trên list/detail.

**FE mobile manager — màn Invoices:**

- [ ] Không hiển thị “0đ” / “—” gây hiểu nhầm — dùng text **“Liên hệ admin để thu hộ”** hoặc ẩn cột tiền.
- [ ] **Chỉ** hiện số tiền sau khi gọi `POST .../payment-qr` (mục 3.5).
- [ ] Detail invoice: `items[]` / `paymentBreakdown` cũng bị mask — không render dòng tiền.

**FE web admin/owner:** Không ảnh hưởng — admin vẫn thấy đầy đủ số tiền.

---

## 3. A2 + B3 — Passcode mở hoá đơn & QR manager (API mới)

Luồng chung cho **thu tiền mặt (B1)** và **trả hộ (B2)**:

```
Admin cấp mã → Manager verify → Manager mở QR → Quét PayOS → Webhook PAID → Realtime + Notification
```

### 3.1 Admin cấp mã

```
POST /api/v1/admin/invoice-unlock/passcodes
Authorization: Bearer {admin}
```

**Body:**

```json
{
  "invoiceId": 7,
  "purpose": "CASH_COLLECT",
  "ttlMinutes": 15,
  "note": "Phòng 101 thu tiền mặt"
}
```

| Field | Bắt buộc | Giá trị |
|-------|----------|---------|
| `invoiceId` | ✅ | Mã **chỉ** mở đúng hoá đơn này |
| `purpose` | ✅ | `CASH_COLLECT` \| `PROXY_PAY` |
| `ttlMinutes` | ❌ | Mặc định 15, max 60 |
| `note` | ❌ | Ghi chú nội bộ |

**Response:**

```json
{
  "passcode": "482913",
  "invoiceId": 7,
  "purpose": "CASH_COLLECT",
  "expiresAt": "2026-08-17T15:00:00"
}
```

**FE web admin:**

- [ ] Màn/modal “Cấp mã thu hộ” trên chi tiết hoá đơn hoặc danh sách receivables.
- [ ] Dropdown `purpose`: “Thu tiền mặt” / “Trả hộ”.
- [ ] Hiển thị mã 6 số + countdown `expiresAt` — copy/gửi cho manager (Zalo/call).

**List mã đang sống:**

```
GET /api/v1/admin/invoice-unlock/passcodes?activeOnly=true
```

**Audit log:**

```
GET /api/v1/admin/invoice-unlock/logs
```

---

### 3.2 Manager nhập mã

```
POST /api/v1/manager/invoice-unlock/verify
Authorization: Bearer {manager}
```

**Body:**

```json
{
  "invoiceId": 7,
  "passcode": "482913"
}
```

**Response OK:**

```json
{
  "valid": true,
  "unlockToken": "550e8400-e29b-41d4-a716-446655440000",
  "expiresAt": "2026-08-17T15:15:00"
}
```

**Response lỗi (403):**

```json
{
  "valid": false,
  "message": "Mã không đúng hoặc đã hết hạn. Liên hệ admin để lấy mã mới."
}
```

| Quy tắc | Chi tiết |
|---------|----------|
| Mã dùng 1 lần | Verify OK → passcode chết ngay |
| Sai 3 lần | Khoá thao tác **trên hoá đơn đó** 15 phút |
| Phân quyền | Manager phải là `operationManagerId` của tòa chứa hoá đơn → không thì 403 |

**FE mobile manager:**

- [ ] Trên chi tiết hoá đơn: 2 entry point — **“Khách trả tiền mặt”** / **“Có người trả hộ”**.
- [ ] Bước 1: gọi admin (ngoài app) — không cần UI.
- [ ] Bước 2: form nhập 6 số passcode + `invoiceId`.
- [ ] Lưu `unlockToken` + `expiresAt` trong state màn hình (không persist lâu dài).

---

### 3.3 Manager mở QR (chỗ duy nhất thấy số tiền)

```
POST /api/v1/manager/invoices/{id}/payment-qr
Authorization: Bearer {manager}
```

**Body — Thu tiền mặt:**

```json
{
  "unlockToken": "550e8400-e29b-41d4-a716-446655440000",
  "purpose": "CASH_COLLECT"
}
```

**Body — Trả hộ:**

```json
{
  "unlockToken": "550e8400-e29b-41d4-a716-446655440000",
  "purpose": "PROXY_PAY",
  "payerName": "Nguyễn Văn B",
  "payerPhone": "0912345678"
}
```

| Field | Bắt buộc |
|-------|----------|
| `unlockToken` | ✅ — từ bước verify |
| `purpose` | ✅ — phải khớp passcode |
| `payerName` | ✅ khi `PROXY_PAY` |
| `payerPhone` | ❌ |

**Response:**

```json
{
  "amount": 5209677.00,
  "qrCode": "data:image/png;base64,...",
  "checkoutUrl": "https://pay.payos.vn/...",
  "orderCode": 1723890123456,
  "expiresAt": "2026-08-17T15:30:00"
}
```

| Field | FE dùng |
|-------|---------|
| `amount` | **Lần đầu manager thấy số tiền** — hiển thị to, rõ |
| `qrCode` | Render QR fullscreen cho người quét |
| `expiresAt` | **Đồng hồ đếm ngược 15 phút** |
| `orderCode` | Debug/log — không cần hiện user |

**FE mobile manager — màn QR:**

- [ ] Countdown tới `expiresAt`; hết giờ → disable QR + nút **“Tạo mã mới”**.
- [ ] “Tạo mã mới” = gọi lại admin xin passcode mới (full luồng), **không** tự regen QR không passcode.
- [ ] Trả hộ: form **tên bắt buộc** trước khi gọi API (manager khai — không verify CCCD).
- [ ] Sau khi quét xong: lắng nghe **realtime** `INVOICE_PAID` (mục 5) — **không** bắt khách bấm xác nhận để coi là PAID.
- [ ] Tuỳ chọn UX: nút “Khách xác nhận đã xong” chỉ để manager rời phòng — **không** gọi BE.

---

## 4. B1 / B2 — Luồng nghiệp vụ cho FE

### B1 — Thu tiền mặt

```
1. Khách nhắn manager: "tôi trả tiền mặt"
2. Manager đến phòng, đếm tiền (ngoài app)
3. Manager bấm "Khách trả tiền mặt" → admin cấp passcode CASH_COLLECT
4. Manager verify → mở QR → tự quét bằng app ngân hàng cá nhân
5. Webhook PAID → cả 2 máy nhận realtime
6. (Tuỳ chọn) Khách xác nhận miệng — không ảnh hưởng trạng thái HĐ
```

**Lưu ý copy UI:** Manager phải **tự chuẩn bị tiền trong tài khoản**. Không có trạng thái “đã nhận tiền mặt, nộp sau”.

---

### B2 — Trả hộ

```
1. Khách báo sẽ có người tới trả
2. Manager hỏi tên → nhập payerName (+ SĐT tuỳ chọn)
3. Admin cấp passcode PROXY_PAY
4. Manager verify → QR 15 phút trên màn manager
5. Người trả hộ quét bằng máy mình
6. Khách nhận push: "... đã được Nguyễn Văn B thanh toán hộ"
```

**Chống khai khống:** Tên người trả hộ **bắt buộc** hiện trong thông báo tenant — FE tenant phải render rõ dòng “Người nộp: … (trả hộ)”.

---

## 5. Realtime — bổ sung context (cập nhật doc 15/08)

Payload `INVOICE_PAID` trên `/user/queue/billing` **thêm field**:

```json
{
  "event": "INVOICE_PAID",
  "invoiceId": 123,
  "paymentMethod": "CASH",
  "collectionMode": "MANAGER_CASH",
  "remittedByName": "Trần Thị B",
  "payerName": null,
  "unlockedByAdminName": "Admin Nguyễn"
}
```

| `collectionMode` | Hiển thị gợi ý trên list hoá đơn |
|------------------|----------------------------------|
| `SELF` | (mặc định — không cần badge) |
| `MANAGER_CASH` | “Tiền mặt · {remittedByName} nộp” |
| `PROXY` | “{payerName} trả hộ” |

**FE admin/host — màn hoá đơn / receivables:**

- [ ] Parse 3 field mới từ STOMP.
- [ ] (Khuyến nghị) Filter `collectionMode` trên list — BE chưa có query param; FE filter client-side từ realtime + refetch list.

**FE manager/tenant:** Realtime vẫn hoạt động như doc 15/08 — thêm badge nếu muốn.

---

## 6. C2 — Thông báo in-app & push

### 6.1 Bảng notification theo vai

| Vai | API đọc chuông | Bảng BE ghi |
|-----|----------------|-------------|
| Tenant (mobile) | `GET /api/v1/notifications` | `notifications` |
| Manager (mobile) | `GET /api/v1/notifications` | `notifications` |
| Admin (web) | `GET /api/v1/notifications` | `notifications` |
| **Host (web)** | **`GET /api/v1/host/notifications`** | **`host_notifications`** |

⚠️ Host **không** đọc `/api/v1/notifications` — ghi nhầm bảng = mất thông báo im lặng.

---

### 6.2 Type & nội dung mới

| Type | Vai | Tiêu đề | Nội dung (có thể rút gọn UI) |
|------|-----|---------|------------------------------|
| `PAYMENT_RECEIVED_TENANT` | Tenant | 💰 Hoá đơn đã thanh toán | Có **số tiền** + “Người nộp: X (trả hộ)” nếu PROXY |
| `PAYMENT_RECEIVED_MANAGER` | Manager | 💰 Khách đã thanh toán | **Không** số tiền (giữ như cũ) |
| `PAYMENT_RECEIVED_ADMIN` | Admin | 💰 Thu tiền | Có số tiền + ngữ cảnh tiền mặt/trả hộ |
| `PAYMENT_RECEIVED_HOST` | Host | 💰 Thu tiền | Giống admin — qua `host_notifications` |
| `PAYMENT_OVERPAYMENT_ADMIN` | Admin | ⚠️ Thanh toán thừa | Hoá đơn đã PAID nhưng thêm tiền — cần hoàn |

**paramsJson** (tất cả): `{"invoiceId":7,"contractId":45,"collectionMode":"PROXY"}`

---

### 6.3 Push ngoài app

| Vai | Push | FE việc |
|-----|------|---------|
| Tenant | Expo — **đã bật** khi có notification mới | Không thêm code nếu đã register push token |
| Manager | Expo — như cũ | Không đổi |
| Host / Admin web | **Chưa có** (C2c) | Issue riêng — service worker |

---

### 6.4 Checklist notification FE

**Tenant mobile:**

- [ ] Handler type `PAYMENT_RECEIVED_TENANT` → màn `InvoiceDetail` (`screen` trong payload).
- [ ] Render dòng người nộp khi `collectionMode=PROXY` trong push + chuông.

**Manager mobile:**

- [ ] Type `PAYMENT_RECEIVED_MANAGER` — giữ UI cũ (không amount).

**Admin web:**

- [ ] Poll/refetch `GET /api/v1/notifications` — type mới `PAYMENT_RECEIVED_ADMIN`, `PAYMENT_OVERPAYMENT_ADMIN`.

**Host web:**

- [ ] Chỉ đọc `GET /api/v1/host/notifications`.
- [ ] Type `PAYMENT_RECEIVED_HOST` — dedupe server-side, FE refresh list là đủ.

---

## 7. Ghi nhận thanh toán — field mới (read-only cho FE)

`GET /api/v1/tenant/me/payments` (và báo cáo admin nếu có) — bản ghi `tenant_payments` có thêm:

| Field | Ý nghĩa |
|-------|---------|
| `collectionMode` | `SELF` \| `MANAGER_CASH` \| `PROXY` |
| `method` | `CASH` (tiền mặt) hoặc `QR` |
| `remittedBy` | UUID manager nộp thay (MANAGER_CASH) |
| `remitMethod` | `QR` — nộp về công ty bằng QR |
| `payerName` / `payerPhone` | Người trả hộ (PROXY) |
| `facilitatedBy` | UUID manager mở QR |
| `paymentNote` | Ghi chú nội bộ |

*(Response DTO tenant có thể chưa expose hết field — FE admin báo lại nếu cần thêm vào API.)*

---

## 8. Config server (DevOps / QA)

```properties
billing.invoice-unlock.passcode-ttl-minutes=15
billing.invoice-unlock.token-ttl-minutes=15
billing.manager-payment-qr.ttl-minutes=15
```

Migration DB tự chạy khi start app (`DatabaseSchemaMigration` + backfill order cũ).

---

## 9. Chưa làm — FE không chờ

| Hạng mục | Ghi chú |
|----------|---------|
| **C2c Web Push** host/admin | Cần service worker + VAPID — issue riêng |
| **Forgot password** | `POST /auth/forgot-password` — giảm nhu cầu trả hộ |
| **`expectedReceptionDate` null** import Excel | Doc `BE-NEED-dot-don-mock-2026-08-15.md` |

---

## 10. Test plan gợi ý cho FE

### Manager — tiền mặt

1. Login manager → mở HĐ tiền nhà → **không** thấy số tiền trên list/detail.
2. Admin cấp mã `CASH_COLLECT` → manager verify → QR hiện **amount** + countdown 15 phút.
3. Manager quét PayOS → tenant + manager + host/admin nhận realtime trong ~2s.
4. Tenant mở chuông → thấy notification có số tiền.

### Manager — trả hộ

1. Nhập tên “Nguyễn Văn B” → mở QR `PROXY_PAY`.
2. Người khác quét → tenant notification có “Người nộp: Nguyễn Văn B (trả hộ)”.
3. Tạo QR mới (passcode mới) trong lúc người kia đang quét mã cũ → **cả hai** webhook đều map đúng HĐ (BE A1).

### Regression A3

1. `GET /manager/deposits` — không còn field `deposit`.
2. `GET /manager/invoices` — `HD-ONBOARD-*` có `amount: null`.

---

## 11. Liên hệ / blocker FE

| Câu hỏi | Trả lời BE |
|---------|------------|
| Manager có API xem số tiền không qua passcode? | **Không** — by design |
| Khách bấm “Xác nhận” có cần API? | **Không** — chỉ UX |
| Host đọc `/notifications` được không? | **Không** — dùng `/host/notifications` |
| QR tenant tự thanh toán có TTL 15 phút? | **Không** — chỉ QR manager thu hộ |

---

*Tài liệu này bám spec `BE-NEED-thu-tien-mat-va-tra-ho-2026-08-16.md`. Cập nhật realtime chi tiết bổ sung thêm vào `BE-DONE-websocket-hoa-don-realtime-2026-08-15.md` khi FE onboard xong.*
