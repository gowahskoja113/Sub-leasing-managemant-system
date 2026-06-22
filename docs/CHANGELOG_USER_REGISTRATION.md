# 📋 Changelog: Chức Năng Đăng Ký Tài Khoản Khách (ROLE_USER)

> **Ngày:** 2026-06-20  
> **Đối tượng:** Team FE, BE, Ops 
> **Branch:** `feature/user-registration`

---

## 🎯 Mục tiêu

Cho phép người dùng tải App → tự đăng ký tài khoản → xem danh sách nhà/phòng mà **không cần Admin tạo tài khoản**.

---

## 🔄 Tóm tắt thay đổi

### 1. Thêm Role mới: `ROLE_USER`

**File:** `src/.../enums/Role.java`

```java
public enum Role {
    ROLE_ADMIN,
    ROLE_OWNER,
    ROLE_MANAGER,
    ROLE_TENANT,
    ROLE_USER      // ← MỚI
}
```

> **Phân biệt:** `ROLE_TENANT` = người thuê đã ký hợp đồng, `ROLE_USER` = khách vãng lai mới đăng ký để xem nhà.

---

### 2. API Đăng ký mới (Dành cho FE)

| Thuộc tính | Giá trị |
|---|---|
| **Method** | `POST` |
| **URL** | `/api/v1/user/register` |
| **Auth** | ❌ Không cần Bearer Token |
| **Content-Type** | `application/json` |

**Request Body:**

```json
{
  "username": "khachmoi01",
  "password": "matkhau123",
  "phoneNumber": "0901234567",
  "fullName": "Nguyễn Văn A"
}
```

**Response (200 OK):**

```json
{
  "id": "uuid-...",
  "username": "khachmoi01",
  "phoneNumber": "0901234567",
  "fullName": "Nguyễn Văn A",
  "role": "ROLE_USER",
  "status": "ACTIVE"
}
```

**Lỗi có thể gặp:**

| HTTP Status | Message |
|---|---|
| 500 | `Username đã tồn tại trên hệ thống!` |
| 500 | `Số điện thoại đã được đăng ký!` |

> ⚠️ **Lưu ý cho FE:** Không cần gửi `role` hay `status` trong body. BE tự ép cứng `ROLE_USER` + `ACTIVE`.

---

### 3. Đăng nhập sau khi đăng ký

Dùng API đăng nhập hiện có:

| Thuộc tính | Giá trị |
|---|---|
| **Method** | `POST` |
| **URL** | `/api/v1/auth/login` |
| **Auth** | ❌ Không cần Token |

```json
{
  "username": "khachmoi01",
  "password": "matkhau123"
}
```

**Response:** Trả về JWT token — FE lưu lại và gắn vào `Authorization: Bearer <token>` cho các API tiếp theo.

---

### 4. Các API mà ROLE_USER được truy cập

| API | Method | Quyền trước | Quyền sau |
|-----|--------|-------------|-----------|
| `/api/v1/user/{id}` | GET | ADMIN, OWNER | ADMIN, OWNER, MANAGER, **USER** |
| `/api/v1/properties` | GET | Authenticated (mọi role) | ADMIN, OWNER, MANAGER, **USER** |
| `/api/v1/properties/{id}` | GET | Authenticated | ADMIN, OWNER, MANAGER, **USER** |
| `/api/v1/properties/rentable` | GET | Authenticated | ADMIN, OWNER, MANAGER, **USER** |
| `/api/v1/properties/{id}/rooms` | GET | Authenticated | ADMIN, OWNER, MANAGER, **USER** |
| `/api/v1/properties/{id}/rooms/{roomId}` | GET | Authenticated | ADMIN, OWNER, MANAGER, **USER** |
| `/api/v1/public/properties/**` | GET | Public (không cần token) | **Không đổi** — vẫn public |

> ⚠️ **ROLE_USER KHÔNG có quyền:** tạo/sửa/xóa nhà, tạo/sửa/xóa phòng, quản lý user, quản lý hợp đồng, v.v.

---

### 5. Luồng hoạt động cho FE

```
[Màn hình chào] 
    → Nhấn "Đăng ký" 
    → Gọi POST /api/v1/user/register (không cần token)
    → Thành công → Chuyển sang màn hình Đăng nhập
    → Gọi POST /api/v1/auth/login → Lấy JWT token
    → Gắn token vào Header
    → Gọi GET /api/v1/properties hoặc /api/v1/public/properties
    → Hiển thị danh sách nhà
    → Nhấn vào nhà → GET /api/v1/properties/{id}/rooms
    → Hiển thị danh sách phòng
```

---

## 📁 Danh sách file thay đổi (BE)

| File | Thay đổi |
|------|----------|
| `enums/Role.java` | Thêm `ROLE_USER` |
| `controller/UserController.java` | Thêm hàm `registerUserAccount()` + sửa `@PreAuthorize` cho `GET /{id}` |
| `config/SecurityConfig.java` | Thêm `/api/v1/user/register` vào `permitAll()` |
| `controller/PropertyController.java` | Thêm `@PreAuthorize` với `USER` cho 3 hàm GET |
| `controller/RoomController.java` | Thêm `@PreAuthorize` với `USER` cho 2 hàm GET |

---

## ✅ Checklist test (Postman)

- [ ] `POST /api/v1/user/register` không token → `200 OK`, role = `ROLE_USER`
- [ ] `POST /api/v1/auth/login` → lấy được JWT token
- [ ] `GET /api/v1/properties` với token USER → `200 OK`
- [ ] `GET /api/v1/properties/{id}/rooms` với token USER → `200 OK`
- [ ] `GET /api/v1/user/{id}` với token USER → `200 OK`
- [ ] `POST /api/v1/properties` với token USER → `403 Forbidden` (không có quyền tạo)
- [ ] `DELETE /api/v1/properties/{id}` với token USER → `403 Forbidden`
