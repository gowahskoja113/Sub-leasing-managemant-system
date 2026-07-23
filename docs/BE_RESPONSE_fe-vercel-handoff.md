# BE Response — Đáp ứng FE Handoff (Vercel ↔ Render)

**Từ:** Backend (SLMS / Render + Supabase)  
**Cho:** Frontend (`frontend-web` / Vercel)  
**Ngày:** 2026-07-22  
**Tham chiếu:** `BE_HANDOFF_fe-vercel-deploy.md`

---

## Tóm tắt

BE **đã đọc và hiểu** yêu cầu handoff của FE. Các mục bắt buộc đã được xác nhận / cấu hình như bảng dưới. FE có thể set `VITE_API_URL` và deploy Vercel theo thông tin này.

| Mục handoff FE | Trạng thái BE |
|---|---|
| 1. URL API Render | ✅ Đáp ứng — 1 service dùng chung prod/dev |
| 2. CORS cho Vercel + localhost | ✅ Đã cấu hình (cần deploy bản CORS mới) |
| 3. Format login / lỗi | ✅ Xác nhận field thật trên BE |
| 4. Cold start Free tier | ✅ Xác nhận — FE nên tăng timeout |
| 5. API docs / Swagger | ✅ Có link live |
| 6. Supabase cho FE | ✅ Không cần — FE chỉ gọi qua BE |

---

## 1. URL API (bắt buộc) — ✅ Đã hiểu & đáp ứng

**Hiểu yêu cầu FE:** Cần URL Render thật cho Production và Dev/Test; nếu dùng chung 1 service thì phải báo rõ.

**Trả lời BE:**

- Đang dùng **một** Web Service Render cho cả Production và Dev/Test (không tách service).
- **Base URL (chung):**

```text
https://sub-leasing-managemant-system.onrender.com
```

- FE set Environment Variables trên **cả 2** project Vercel (prod + dev):

```text
VITE_API_URL=https://sub-leasing-managemant-system.onrender.com
```

- Local FE (`localhost:5173`): có thể để `VITE_API_URL` trỏ Render, hoặc để trống + proxy Vite như FE đã mô tả.

---

## 2. CORS — ✅ Đã hiểu & đã cấu hình

**Hiểu yêu cầu FE:** Phải whitelist origin Vercel prod/dev và `http://localhost:5173`; cần credentials, methods, headers đúng — thiếu sẽ chặn hoàn toàn gọi API từ browser.

**Trả lời BE — đã cấu hình:**

| Yêu cầu FE | BE |
|---|---|
| `https://<project-production>.vercel.app` | ✅ Pattern `https://*.vercel.app` |
| `https://<project-dev>.vercel.app` | ✅ Cùng pattern `https://*.vercel.app` |
| `http://localhost:5173` | ✅ `http://localhost:*` / `http://127.0.0.1:*` |
| `Access-Control-Allow-Credentials: true` | ✅ `allow-credentials: true` |
| Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS | ✅ Đủ |
| Headers: Authorization, Content-Type | ✅ `allowed-headers: *` (gồm các header trên) |

**Lưu ý vận hành:**

- Thay đổi CORS nằm trong `application.yaml` — cần **push GitHub → Render redeploy** để có hiệu lực trên môi trường live.
- Nếu FE dùng **custom domain** (không phải `*.vercel.app`), gửi domain thật cho BE để bổ sung whitelist.

---

## 3. Format response — ✅ Đã hiểu & xác nhận

**Hiểu yêu cầu FE:** Cần biết field access token khi login (FE lưu `localStorage` + `Authorization: Bearer`), và format lỗi `{ message, error }` để hiển thị.

### 3.1 Login

- Endpoint: `POST /api/v1/auth/login`
- Access token nằm ở field **`token`** (không phải `accessToken`).

Ví dụ:

```json
{
  "token": "<jwt>",
  "username": "...",
  "role": "...",
  "firstLogin": true
}
```

FE gắn header:

```http
Authorization: Bearer <token>
```

### 3.2 Lỗi

- BE trả lỗi có `status`, thường có field **`error`**, một số case (Map handler) có thêm **`message`**.
- FE nên đọc: `error ?? message` (fallback lẫn nhau) để hiển thị cho user.
- Validation lỗi có thể kèm `fieldErrors`.

---

## 4. Cold start Render free tier — ✅ Đã hiểu & xác nhận

**Hiểu yêu cầu FE:** Cần biết plan để tăng timeout client, tránh nhầm cold start thành bug.

**Trả lời BE:**

- Plan hiện tại: **Render Free**.
- Instance **sleep ~15 phút** không traffic.
- Request đầu sau khi sleep có thể chậm **~30–60 giây** (đôi khi hơn).
- Khuyến nghị FE: timeout client ≥ 60–90s cho môi trường demo/capstone; có thể ping/health trước buổi demo.

---

## 5. API docs — ✅ Đã đáp ứng

**Hiểu yêu cầu FE:** Cần Swagger/Postman hoặc link doc path thật trên Render.

**Trả lời BE:**

| Tài liệu | URL |
|---|---|
| Swagger UI | https://sub-leasing-managemant-system.onrender.com/swagger-ui.html |
| OpenAPI JSON | https://sub-leasing-managemant-system.onrender.com/v3/api-docs |

Path API chuẩn prefix: `/api/v1/...` (ví dụ auth: `/api/v1/auth/login`).

---

## 6. Supabase — ✅ Đã hiểu (không cung cấp cho FE)

**Hiểu yêu cầu FE:** Theo kiến trúc hiện tại FE không gọi thẳng Supabase; chỉ note phòng khi sau này cần Storage/Realtime.

**Trả lời BE:**

- Đồng ý: FE **chỉ gọi qua BE**.
- **Không** cung cấp `SUPABASE_URL` / anon key cho FE ở giai đoạn này.
- DB Supabase chỉ dùng nội bộ bởi Spring Boot (JDBC). Nếu sau này cần Storage/Realtime, FE mở lại yêu cầu riêng.

---

## Việc FE làm tiếp (theo handoff — BE xác nhận khớp)

1. Set `VITE_API_URL` trên Vercel (prod + dev) = URL Render ở mục 1.
2. Code đọc `import.meta.env.VITE_API_URL` làm axios `baseURL` — đúng hướng.
3. Tăng timeout vì Free tier cold start.
4. Login lấy `token`, gắn `Bearer`.
5. Sau khi BE redeploy CORS: thử gọi từ domain Vercel; nếu CORS lỗi, gửi domain thật cho BE.

---

## Checklist đồng bộ BE ↔ FE

- [x] BE hiểu FE deploy 2 bản Vercel (main / dev)
- [x] BE báo rõ **1 backend Render dùng chung**
- [x] BE cung cấp base URL thật
- [x] BE cấu hình CORS Vercel + localhost
- [x] BE xác nhận field `token` + cách đọc lỗi
- [x] BE xác nhận Free tier / cold start
- [x] BE cung cấp Swagger live
- [x] BE xác nhận không cần Supabase key cho FE
- [ ] BE push + Render deploy bản có CORS `*.vercel.app` (nếu chưa)
- [ ] FE set `VITE_API_URL` trên Vercel và smoke-test login

---

## Liên hệ nhanh khi lệch

- Domain Vercel thật (nếu không thuộc `*.vercel.app`) → gửi BE để whitelist thêm.
- Path FE đang gọi khác Swagger → đối chiếu Swagger live, báo BE nếu thiếu endpoint.
