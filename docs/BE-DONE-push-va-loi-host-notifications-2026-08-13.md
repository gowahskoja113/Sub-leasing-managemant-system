# BE DONE — 500 `/host/notifications` + push Host / Expo

**Ngày:** 13/08/2026  
**Người gửi:** team BE  
**Người nhận:** team FE (web admin + mobile)  
**Phản hồi:** `BE-push-va-loi-host-notifications-2026-08-13.md`  
**Phát hiện:** web admin + BE `localhost:8080`

---

## Tóm tắt

| # | Mức độ | Việc FE yêu cầu | Trạng thái |
|---|--------|-----------------|------------|
| 1 | 🔴 | NPE `syncSystemNotifications` → `GET /host/notifications` 500 | ✅ Done |
| 2 | 🟡 | Escalation bảo trì đẩy push cho Host | ✅ Done |
| 3a | 🟡 | `RestTemplate` timeout 5s | ✅ Done |
| 3b | 🟢 | Batch Expo 100 token / request | ✅ Done |
| 3c | 🟢 | `DeviceNotRegistered` → xoá token | ✅ Done |

Chuông Host **không còn 500** khi có HĐ `PENDING` chưa gắn tenant (luồng đón khách).

**Không** làm `@Async` trong lần này — timeout 5s đã chặn treo màn thanh toán khi Expo chậm. Async là bước sau nếu FE vẫn thấy trễ.

---

## 1. 🔴 `GET /api/v1/host/notifications` hết 500

### Trước

`contract.getTenant().getUser().getFullName()` trên HĐ `PENDING` **chưa có tenant** (tên nằm `draftTenantName`) → NPE → 500. Badge chuông Host hiện 0, `skipErrorToast` nên im lặng.

### Sau

```
tenant.user.fullName  →  draftTenantName  →  "khách thuê"
```

```json
"HĐ HD-… — Nguyễn Văn A chờ Host duyệt."
```

hoặc nháp:

```json
"HĐ HD-… — Nguyễn Văn A chờ Host duyệt."   // draftTenantName
```

### Rà thêm trong `syncSystemNotifications`

| Vòng | Rủi ro | Việc làm |
|------|--------|----------|
| `PENDING_HOST_REVIEW` property | Chỉ dùng `propertyName` | Không đụng (không NPE quan hệ) |
| `PENDING` tenant contract | `tenant` null | ✅ fallback tên |
| Master lease sắp hết hạn | `endDate` null → NPE `.isAfter` | ✅ bỏ qua lease không `endDate` |

Cùng helper dùng cho `buildInvoices` (ACTIVE thiếu tenant cũng không 500).

### Việc FE làm

- Bỏ workaround “admin không gọi chuông Host” nếu muốn admin xem lại — **không bắt buộc**.
- Host mở chuông: list + badge `unreadOnly` phải **200**.
- Retry `GET /api/v1/host/notifications?unreadOnly=true&page=0&size=1` với HĐ nháp PENDING trong DB.

---

## 2. 🟡 Push escalation bảo trì cho Host

`notifyPropertyHost` sau khi `hostNotificationRepository.save`:

```
userPushTokenService.sendToUser(host.getId(), title, body, data)
```

| Key | Giá trị |
|-----|---------|
| `type` | `MAINTENANCE_REOPEN_ESCALATION` |
| `ticketId` / `requestId` | id ticket |

Vẫn ghi `host_notifications` (web portal). Push là thêm — Host không mở web vẫn nhận nếu đã đăng ký Expo token (app Host / cùng user).

**Không** đẩy push cho 3 nhắc việc lười (căn chờ giá, HĐ chờ duyệt, master lease) — đúng phạm vi FE xin.

---

## 3. `PushNotificationService`

### 3a. Timeout 5s

`SimpleClientHttpRequestFactory` — connect + read **5 giây**. Expo chậm / mạng lỗi → log warn, **không** treo tạo HĐ / thanh toán / bảo trì.

### 3b. Batch

`UserPushTokenService.sendToUser` gọi `sendPushNotifications(tokens, …)`. Expo nhận **mảng**, cắt **100** token / request.

`sendPushNotification(token, …)` (checkout legacy) vẫn dùng được — đi chung 1 đường batch.

### 3c. Token chết

Parse ticket Expo. `details.error === "DeviceNotRegistered"` (hoặc message chứa chuỗi đó):

- Xoá dòng `user_push_tokens`
- Clear `users.push_token` nếu trùng

Lần sau không gửi vào token hỏng.

Lỗi Expo khác: log warn, **không** xoá.

---

## Payload / API không đổi

- `GET /api/v1/host/notifications` — cùng DTO, chỉ hết 500.
- Type Host `MAINTENANCE_REOPEN_ESCALATION` — giữ nguyên trên web; thêm push cùng type.

---

## File đổi

| File | Việc |
|------|------|
| `HostPortalServiceImpl.java` | Tên khách an toàn + `endDate` null |
| `MaintenanceServiceImpl.java` | Push Host sau save escalation |
| `PushNotificationService.java` | Timeout, batch, parse/xoá token chết |
| `UserPushTokenService.java` | Gửi batch |
| `UserPushTokenRepository.java` | `deleteByToken` |
| `UserRepository.java` | `findByPushToken` |

---

## Checklist FE

- [ ] Host + admin: `GET /host/notifications` **200** khi DB có HĐ PENDING chưa gắn tenant
- [ ] Chuông Host hiện đúng tin `CONTRACT_PENDING` (tên draft / “khách thuê”)
- [ ] Ticket bảo trì reject ≥ 2 lần: Host có tin portal **và** push `MAINTENANCE_REOPEN_ESCALATION` (nếu đã register token)
- [ ] Thanh toán / tạo HĐ không treo khi Expo chậm (tối đa ~5s rồi bỏ qua push)
- [ ] Gỡ app / token hết hạn: lần push sau token biến khỏi `user_push_tokens`
