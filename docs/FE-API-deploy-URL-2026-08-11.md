# FE — URL API deploy (production)

**Ngày:** 11/08/2026  
**Người gửi:** team BE / DevOps  
**Người nhận:** team FE (web + mobile)  
**Trạng thái:** Deploy + DNS + HTTPS **đã xong**, API public đang chạy.

---

## 1. URL chính thức (dùng ngay)

```text
https://slms-api.duckdns.org
```

| Mục | Giá trị |
|-----|---------|
| API base (prod) | `https://slms-api.duckdns.org` |
| Prefix REST | `/api/v1/...` (ví dụ login: `/api/v1/auth/login`) |
| Swagger UI | https://slms-api.duckdns.org/swagger-ui.html |
| OpenAPI JSON | https://slms-api.duckdns.org/v3/api-docs |
| Local (dev) | `http://localhost:8080` |

**Không còn dùng** (chỉ lịch sử):

```text
http://103.78.3.170:8080
```

→ Gọi IP:8080 sẽ lỗi mixed-content (web HTTPS), cert/security mobile, hoặc port đã khoá ra ngoài.

---

## 2. FE cần set env

### 2.1 Web (Vercel)

```env
# Tên biến theo project FE — giá trị bắt buộc:
https://slms-api.duckdns.org
```

| Việc | Chi tiết |
|------|----------|
| Base URL client | Gọi thẳng HTTPS domain trên |
| `vercel.json` | **Bỏ** rewrite/proxy sang IP:8080 (nếu còn) |
| Preview / prod | Cùng base URL API (CORS đã mở `*.vercel.app`) |

### 2.2 Mobile (Expo)

| File | Việc |
|------|------|
| `.env` / env public | `EXPO_PUBLIC_REAL_API_BASE_URL=https://slms-api.duckdns.org` |
| `eas.json` | Profile `development` / `preview` / `production` → cùng base URL HTTPS |

Build release **không cần** bật cleartext traffic chỉ để gọi API.

---

## 3. CORS (đã whitelist bên BE)

Backend cho phép origin:

- `http://localhost:*` / `http://127.0.0.1:*`
- `https://*.vercel.app`
- `https://sep-frontend-prod.vercel.app`

FE có custom domain khác Vercel → báo BE để thêm origin.

---

## 4. PayOS (redirect sau thanh toán)

| Mục | URL |
|-----|-----|
| Webhook (BE + PayOS đã gắn) | `https://slms-api.duckdns.org/api/v1/payos/webhook` |
| `returnUrl` (web) | `https://sep-frontend-prod.vercel.app/payment-success` |
| `cancelUrl` (web) | `https://sep-frontend-prod.vercel.app/payment-cancel` |

Path FE khác (`/payments/success`…) → báo BE sửa env VPS, **không** đổi domain API.

Mobile deep link (`slms://...`): FE + BE thống nhất cặp URL nếu cần song song web.

---

## 5. Kiến trúc (tham khảo)

```text
Client  --HTTPS:443-->  Nginx (Let's Encrypt)
                              |
                              v
                     127.0.0.1:8080 (Docker slms-api)
```

- Domain DNS: `slms-api.duckdns.org` → VPS  
- Cert: Let's Encrypt, auto-renew  
- Port 8080 **không** public (chỉ localhost)

---

## 6. Smoke test (FE / QA tự check)

```bash
# Login validation — kỳ vọng HTTP 400 + JSON fieldErrors
curl -X POST https://slms-api.duckdns.org/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{}'
```

```bash
# Webhook public — kỳ vọng HTTP 200 + {"success":true}
curl -X POST https://slms-api.duckdns.org/api/v1/payos/webhook \
  -H "Content-Type: application/json" \
  -d '{}'
```

**Smoke đã chạy 11/08/2026:** `POST /api/v1/auth/login` → **400**; Swagger qua Nginx HTTPS → **302** → `/swagger-ui/index.html`.

---

## 7. Tài khoản demo (prod seed)

Mật khẩu mặc định: **`123456`**

| Role | Username |
|------|----------|
| Admin | `admin01`, `admin02` |
| Owner | `owner01` … `owner06` |
| Manager | `manager01` … `manager05` |
| Tenant | **SĐT** `0904000001` … `0904000036` (không còn `tenant01`…) |

Login: `POST /api/v1/auth/login` body `{ "username", "password" }` → JWT trong response (header `Authorization: Bearer …` các request sau).

---

## 8. Checklist FE

```
[ ] Đổi API base → https://slms-api.duckdns.org (web + mobile)
[ ] Web bỏ proxy Vercel → IP:8080
[ ] Web không còn mixed-content
[ ] Mobile build preview/production gọi HTTPS login + 1 API có JWT
[ ] PayOS return/cancel mở đúng path Vercel (hoặc deep link đã thống nhất)
[ ] (Optional) E2E: sandbox thanh toán → invoice PAID
```

---

## 9. Khi có sự cố — gửi BE kèm

| Sự cố | Kèm theo |
|-------|----------|
| CORS blocked | Origin FE + response headers |
| 502 / timeout | Thời điểm + endpoint |
| PayOS redirect sai | URL app đang nhận vs path mong đợi |
| Webhook không PAID | `orderCode` + thời gian thanh toán |

---

**TL;DR cho FE:** base URL duy nhất = **`https://slms-api.duckdns.org`**  
Chi tiết đầy đủ lần setup HTTPS + PayOS: `BE-HANDOFF-https-payos-ready-2026-08-06.md` (gốc repo).
