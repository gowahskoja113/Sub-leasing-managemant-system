# Kích hoạt tài khoản khách thuê (OTP + tự đặt mật khẩu) — Hướng dẫn FE

Sau khi manager xác nhận HĐ (`ACTIVE`), hệ thống **tạo tài khoản tenant** với username = SĐT nhưng **không phát mật khẩu mặc định**. Khách phải tự kích hoạt trên app: **SĐT → OTP → tạo mật khẩu**, rồi mới đăng nhập bình thường.

**Liên quan:** luồng thu cọc + OTP confirm HĐ — [`FE-tenant-onboarding-otp-flow.md`](./FE-tenant-onboarding-otp-flow.md)

---

## 1. Tóm tắt nhanh

| Ai | Việc |
|----|------|
| Manager | Onboard + thu cọc + OTP xác nhận HĐ như cũ. **Không** đưa mật khẩu cho khách. Chỉ nói: mở app → **Kích hoạt tài khoản** bằng SĐT trên HĐ. |
| Tenant (lần đầu) | Màn **Kích hoạt** → nhập SĐT → OTP → tạo mật khẩu → vào app (nhận JWT). |
| Tenant (đã kích hoạt) | Màn **Đăng nhập** → SĐT + mật khẩu đã tự đặt. |

```
[HĐ ACTIVE, account tạo, isFirstLogin=true, MK random]
                │
                ▼
     ┌──────────────────────┐
     │  Màn chào Tenant App │
     │  [Đăng nhập]         │
     │  [Kích hoạt lần đầu] │
     └──────────┬───────────┘
                │
     ┌──────────┴───────────┐
     ▼                      ▼
 Đăng nhập              Kích hoạt
 SĐT + MK               SĐT → check
                        │
                        ▼
                   NEEDS_ACTIVATION
                        │
                        ▼
                   Gửi OTP → nhập OTP
                        │
                        ▼
                   Tạo mật khẩu mới
                        │
                        ▼
                   JWT (đã vào app)
```

---

## 2. Ý tưởng UI (FE nên làm theo)

### 2.1. Màn chào / Auth gate (khuyến nghị: **2 nút rõ**)

```
┌─────────────────────────────────┐
│         SLMS Tenant             │
│                                 │
│     [ Đăng nhập ]               │
│     [ Kích hoạt tài khoản ]     │
│                                 │
│  Đã có mật khẩu? → Đăng nhập    │
│  Mới nhận phòng? → Kích hoạt    │
└─────────────────────────────────┘
```

- **Không** gộp một form “SĐT + mật khẩu” cho cả hai việc — khách mới sẽ bối rối vì chưa có mật khẩu.
- Dưới form Đăng nhập thêm link nhỏ: *“Lần đầu thuê? Kích hoạt tài khoản”*.

### 2.2. Flow Kích hoạt (3 bước, wizard)

**Bước A — Nhập SĐT**

- 1 ô SĐT (placeholder: số đã khai trên hợp đồng)
- Nút **Tiếp tục**
- Gọi `POST /tenant-activate/check` trước khi gửi OTP:
  - `NEEDS_ACTIVATION` → sang bước B, gọi `send-otp`
  - `READY_TO_LOGIN` → toast “Đã kích hoạt” + điều hướng màn Đăng nhập (điền sẵn SĐT)
  - `NOT_FOUND` / `NOT_ELIGIBLE` → hiện `message` từ BE, không gửi OTP

**Bước B — OTP**

- 6 ô / 1 input OTP
- Countdown gửi lại (VD 60s) → `POST /tenant-activate/send-otp`
- Copy: *“Mã OTP đã gửi SMS. Nhập mã để xác nhận số điện thoại.”*
- **Lưu ý môi trường hiện tại (budget):** SMS OTP kích hoạt đang **hardcode gửi về số cố định của team** (giống OTP confirm HĐ), không gửi về SĐT khách trên HĐ. QA/demo đọc OTP từ máy số đó hoặc log BE `[DEV]`.

**Bước C — Tạo mật khẩu**

- Mật khẩu mới (≥ 6 ký tự) + Nhập lại
- Nút **Hoàn tất kích hoạt** → `POST /tenant-activate/confirm`
- Thành công: lưu `token` như login, `isFirstLogin = false`, vào Home tenant
- **Không** hỏi mật khẩu cũ

### 2.3. Màn Đăng nhập

- SĐT (username) + mật khẩu
- Nếu BE trả 422 kiểu *“Tài khoản chưa kích hoạt…”* → CTA chuyển sang **Kích hoạt** với SĐT đã nhập

### 2.4. Manager app (sau confirm HĐ)

Khi `POST .../confirm` thành công và có `tenantAccountCreated` / `tenantUsername`:

- **Bỏ** UI kiểu “Mật khẩu mặc định: tenant123”
- Hiện hướng dẫn ngắn:

> Tài khoản: `{tenantUsername}` (SĐT khách)  
> Nhờ khách mở app → **Kích hoạt tài khoản** → nhập SĐT → OTP → tự tạo mật khẩu.

---

## 3. API (public — nằm dưới `/api/v1/auth/**`, không cần JWT)

Base: `/api/v1/auth`

### 3.1. Kiểm tra SĐT (phân nhánh UI)

```http
POST /api/v1/auth/tenant-activate/check
Content-Type: application/json

{ "phoneNumber": "09xxxxxxxx" }
```

**Response 200:**

```json
{
  "status": "NEEDS_ACTIVATION",
  "message": "Tài khoản chưa kích hoạt. Nhập OTP rồi tạo mật khẩu để tiếp tục.",
  "username": "09xxxxxxxx"
}
```

| `status` | Ý nghĩa | FE |
|----------|---------|-----|
| `NEEDS_ACTIVATION` | Tenant + `isFirstLogin` + có HĐ `ACTIVE` | Tiếp tục gửi OTP |
| `READY_TO_LOGIN` | Đã đặt mật khẩu | Sang Đăng nhập |
| `NOT_FOUND` | Không có user | Báo liên hệ quản lý |
| `NOT_ELIGIBLE` | Không phải tenant chờ kích hoạt / chưa có HĐ ACTIVE | Hiện `message` |

### 3.2. Gửi OTP kích hoạt

```http
POST /api/v1/auth/tenant-activate/send-otp
Content-Type: application/json

{ "phoneNumber": "09xxxxxxxx" }
```

**Response 200:**

```json
{ "message": "Đã gửi mã OTP kích hoạt tài khoản" }
```

Chỉ thành công khi `check` = `NEEDS_ACTIVATION`. OTP hết hạn ~5 phút (cấu hình Twilio OTP).

### 3.3. Xác nhận OTP + tạo mật khẩu

```http
POST /api/v1/auth/tenant-activate/confirm
Content-Type: application/json

{
  "phoneNumber": "09xxxxxxxx",
  "otp": "123456",
  "newPassword": "secret12",
  "confirmPassword": "secret12"
}
```

**Response 200** (giống login):

```json
{
  "token": "eyJ...",
  "username": "09xxxxxxxx",
  "role": "ROLE_TENANT",
  "isFirstLogin": false
}
```

FE lưu token và vào app luôn — không bắt login lại.

### 3.4. Đăng nhập thường (sau khi đã kích hoạt)

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "09xxxxxxxx",
  "password": "secret12"
}
```

Nếu chưa kích hoạt → **422**:

`Tài khoản chưa kích hoạt. Vui lòng dùng chức năng Kích hoạt tài khoản (OTP + tạo mật khẩu).`

---

## 4. Điều kiện phía BE (FE cần biết để giải thích lỗi)

Tài khoản được phép kích hoạt khi **đồng thời**:

1. Có `User` với SĐT đó, `role = ROLE_TENANT`
2. `isFirstLogin = true` (chưa hoàn tất đặt mật khẩu lần đầu)
3. Có ít nhất một hợp đồng `status = ACTIVE`

Account được tạo lúc manager **confirm OTP HĐ** (hoặc onboard non-draft). Trước đó khách **chưa** kích hoạt được.

---

## 5. Lỗi thường gặp

| HTTP / message | Nguyên nhân | FE |
|----------------|-------------|-----|
| 422 — chưa kích hoạt (khi login) | `isFirstLogin=true` | Đưa sang màn Kích hoạt |
| 422 — chưa có HĐ hiệu lực | Confirm HĐ chưa xong | Bảo khách chờ / gặp manager |
| 422 — OTP sai / hết hạn | Sai mã hoặc > ~5 phút | Gửi lại OTP |
| 422 — xác nhận mật khẩu không khớp | `newPassword !== confirmPassword` | Highlight 2 ô MK |
| 400 validation | Thiếu field / MK &lt; 6 ký tự | Hiện fieldErrors |

---

## 6. Checklist FE

- [ ] Màn chào: 2 entry **Đăng nhập** / **Kích hoạt**
- [ ] Wizard kích hoạt: SĐT → OTP → tạo MK
- [ ] Gọi `check` trước `send-otp`
- [ ] `confirm` thành công → lưu token, vào Home
- [ ] Login gặp “chưa kích hoạt” → deep-link Kích hoạt
- [ ] Manager sau confirm: bỏ mật khẩu mặc định, hiện hướng dẫn kích hoạt app
- [ ] (QA) OTP kích hoạt đọc từ số override / log BE `[DEV]`, không chờ SMS về SĐT khách

---

## 7. Ghi chú kỹ thuật / demo

- **OTP delivery override:** cùng số hardcode với OTP confirm HĐ (`0352393203`). Request vẫn gửi **SĐT khách trên HĐ** để BE biết kích hoạt user nào; SMS đi về số override.
- Production sau này: bỏ override, gửi OTP đúng SĐT khách.
- `change-password` (đã login) vẫn dùng cho đổi MK sau này — **không** thay flow kích hoạt lần đầu.
