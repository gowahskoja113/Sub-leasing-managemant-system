# BE HANDOFF — HTTPS + PayOS sẵn sàng

**Ngày:** 06/08/2026  
**Người gửi:** team BE / DevOps  
**Người nhận:** team FE (web + mobile)  
**Liên quan:** `BE-HANDOFF-https-setup-2026-08-06.md` (yêu cầu FE) — **đã hoàn thành**

---

## 1. Tóm tắt

Backend VPS đã có **HTTPS public domain**, Nginx reverse proxy, cert Let's Encrypt (auto-renew), Docker chỉ bind localhost:8080.  
PayOS webhook đã **đăng ký thành công** với PayOS.

| Hạng mục | Trạng thái |
|---|---|
| Domain + DNS | ✅ `slms-api.duckdns.org` → `103.78.3.170` |
| HTTPS (Let's Encrypt) | ✅ Auto-renew OK |
| Nginx → Spring | ✅ `127.0.0.1:8080` |
| Port 8080 ra internet | ✅ Đã khóa (chỉ localhost + UFW) |
| API smoke test | ✅ `POST /api/v1/auth/login` → JSON 400 validation |
| PayOS webhook register | ✅ `code: 00` |
| CORS Vercel | ✅ Giữ whitelist sẵn có |

---

## 2. URL giao cho FE (dùng chính thức)

```text
https://slms-api.duckdns.org
```

**Không còn dùng** (chỉ giữ để tham chiếu lịch sử):

```text
http://103.78.3.170:8080
```

---

## 3. FE cần cập nhật (ước tính ~2 phút)

### 3.1 Mobile

| File | Việc |
|---|---|
| `mobile-app/.env` | `EXPO_PUBLIC_REAL_API_BASE_URL=https://slms-api.duckdns.org` |
| `mobile-app/eas.json` | Cả 3 profile `development` / `preview` / `production` → base URL HTTPS trên |

Sau khi đổi: build APK release **không cần** `usesCleartextTraffic` chỉ để gọi API.

### 3.2 Web

| File | Việc |
|---|---|
| `frontend-web/vercel.json` | **Bỏ** rewrite/proxy vòng qua Vercel sang IP:8080 |
| Env / client API base | Gọi thẳng `https://slms-api.duckdns.org` |

CORS backend đã whitelist:

- `https://*.vercel.app`
- `https://sep-frontend-prod.vercel.app`

---

## 4. PayOS (BE đã cấu hình)

| Mục | Giá trị |
|---|---|
| Webhook URL (đã confirm với PayOS) | `https://slms-api.duckdns.org/api/v1/payos/webhook` |
| `returnUrl` | `https://sep-frontend-prod.vercel.app/payment-success` |
| `cancelUrl` | `https://sep-frontend-prod.vercel.app/payment-cancel` |
| Kênh | SLMS Payment / MBBank (đã gắn webhook) |

### Nếu path FE khác

Báo BE path thật (ví dụ `/payments/success`). BE chỉ cần sửa env trên VPS và restart container — **không cần đổi domain API**.

Deep link mobile (`slms://...`) nếu cần song song web: team FE + BE thống nhất một cặp URL (hoặc app handle redirect từ trang Vercel).

---

## 5. Smoke test gợi ý cho FE

```bash
# Public HTTPS
curl -X POST https://slms-api.duckdns.org/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{}'
# Kỳ vọng: HTTP 400 + JSON fieldErrors username/password
```

```bash
# Webhook (public, không JWT)
curl -X POST https://slms-api.duckdns.org/api/v1/payos/webhook \
  -H "Content-Type: application/json" -d '{}'
# Kỳ vọng: HTTP 200 + {"success":true}
```

Swagger (nếu cần dev):  
`https://slms-api.duckdns.org/swagger-ui.html`

---

## 6. Checklist FE

```
[ ] Đổi base URL mobile (.env + eas.json) → https://slms-api.duckdns.org
[ ] Web bỏ proxy vercel.json, gọi HTTPS thẳng
[ ] Build APK release / preview chạy login + 1 API có JWT
[ ] Web production/preview gọi API không mixed-content
[ ] Flow PayOS: mở checkout → return/cancel về đúng path Vercel
[ ] (Optional) E2E: thanh toán sandbox → webhook cập nhật trạng thái PAID
```

---

## 7. Liên hệ / escalate

| Sự cố | Gửi BE kèm |
|---|---|
| CORS blocked | Origin FE + response header |
| 502 / timeout | Thời điểm + endpoint |
| PayOS không redirect đúng | return/cancel URL app đang nhận vs mong đợi |
| Webhook không đánh PAID | `orderCode` + thời gian thanh toán |

---

## 8. Ghi chú kỹ thuật (tham khảo)

- Kiến trúc: `Client --HTTPS:443--> Nginx --HTTP--> 127.0.0.1:8080 (Docker slms-api)`
- Cert: Let's Encrypt, path `/etc/letsencrypt/live/slms-api.duckdns.org/`, timer renew nền
- BE env quan trọng: `SERVER_FORWARD_HEADERS_STRATEGY=framework`, `APP_PUBLIC_BASE_URL=https://slms-api.duckdns.org`
- Fix code webhook: Spring Boot 4 / Jackson 3 — body Map thay vì inject `JsonNode` trực tiếp (`c21d97e` trên `dev`)

---

**Hết handoff.** URL duy nhất FE cần: **`https://slms-api.duckdns.org`**.
