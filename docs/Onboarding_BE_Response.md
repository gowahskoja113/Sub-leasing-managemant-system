# PHẢN HỒI TỪ TEAM BE — Onboarding Tenant: tạo tài khoản & nâng quyền role

> Người viết: BE team.
> Ngày: 20/06/2026.
> Nhánh: `feature/tenant-onboarding`
> Ref: Onboarding.md (FE gửi 20/06/2026)

---

## Trạng thái: ✅ ĐÃ HOÀN THÀNH (4/4 tasks)

---

## 1. ✅ Thêm role `ROLE_USER`

**File thay đổi:** `enums/Role.java`

```java
public enum Role {
    ROLE_ADMIN,
    ROLE_OWNER,
    ROLE_MANAGER,
    ROLE_TENANT,
    ROLE_USER   // MỚI: người dùng app chỉ tìm/xem nhà
}
```

**Phân quyền:**
- `ROLE_USER` **KHÔNG** truy cập được: maintenance, hoá đơn, hợp đồng, thanh toán — tất cả endpoint này đã có `@PreAuthorize` chặn sẵn.
- `ROLE_USER` **CÓ THỂ** truy cập: `/api/v1/public/properties/**` (xem nhà, tìm nhà) — đã `permitAll()`.
- **Không cần FE đổi gì.**

---

## 2. ✅ Sửa logic `POST /api/v1/tenant-contracts/{contractId}/confirm`

**File thay đổi:** `service/impl/TenantOnboardingServiceImpl.java`

**Logic resolve tài khoản theo SĐT khi confirm:**

| Trường hợp | Xử lý BE | Response flag |
|-----------|----------|---------------|
| Chưa có user theo SĐT | Tạo mới: `username = SĐT`, `password = encode("123456")`, `role = ROLE_TENANT`, `status = ACTIVE` | `tenantAccountCreated = true` |
| Đã có user `ROLE_USER` | Nâng role → `ROLE_TENANT`, giữ nguyên mật khẩu cũ | `tenantRolePromoted = true` |
| Đã có user `ROLE_TENANT` hoặc role khác | Giữ nguyên, chỉ liên kết vào hợp đồng | cả 2 flag = `null` |

**Lưu ý cho FE:**
- Tìm user theo `findByPhoneNumber` trước, fallback `findByUsername(phone)` → tránh trùng dữ liệu cũ.
- Password mặc định `123456` — FE hiển thị cho khách ở màn thành công.
- Luồng `onboardTenant` (tạo HĐ ban đầu) cũng đã cập nhật: nếu SĐT thuộc `ROLE_USER` → tự nâng quyền thay vì báo lỗi.

---

## 3. ✅ Mở rộng response `confirm` (`TenantContractResponse`)

**File thay đổi:** `dto/response/TenantContractResponse.java`

3 field mới (chỉ có giá trị khi gọi confirm, các API khác trả `null`):

```json
{
  "...các field hiện có...",
  "tenantUsername": "0912345678",
  "tenantAccountCreated": true,
  "tenantRolePromoted": false
}
```

| Field | Kiểu | Mô tả |
|-------|------|-------|
| `tenantUsername` | `String` | Username đăng nhập (= SĐT khách) |
| `tenantAccountCreated` | `Boolean` | `true` nếu vừa **tạo mới** tài khoản |
| `tenantRolePromoted` | `Boolean` | `true` nếu vừa **nâng** `ROLE_USER` → `ROLE_TENANT` |

**FE dùng:**
- `tenantAccountCreated = true` → hiển thị "Tài khoản: `<SĐT>` · Mật khẩu mặc định: 123456".
- `tenantRolePromoted = true` → "Tài khoản `<SĐT>` đã được cấp quyền Tenant — đăng nhập bằng mật khẩu hiện có".
- Cả 2 `null/false` → tài khoản đã có sẵn, không cần thông báo gì đặc biệt.

---

## 4. ✅ Mở rộng `GET /api/v1/tenants/lookup`

**File thay đổi:** `dto/response/TenantLookupResponse.java` + `controller/TenantLookupController.java`

**Thay đổi:**
- Bỏ filter chỉ `ROLE_TENANT` → giờ trả kết quả cho **mọi role** (bao gồm `ROLE_USER`).
- Thêm field `role` trong response:

```json
{
  "exists": true,
  "fullName": "Nguyễn Văn A",
  "phoneNumber": "0912345678",
  "cccd": "001234567890",
  "role": "ROLE_USER"
}
```

| `role` | Ý nghĩa FE |
|--------|------------|
| `"ROLE_USER"` | Khách đã có tài khoản app, sẽ được **nâng quyền** khi confirm |
| `"ROLE_TENANT"` | Đã là tenant, chỉ liên kết HĐ mới |
| `null` | `exists = false`, chưa có tài khoản → sẽ **tạo mới** khi confirm |

---

## Tổng kết file thay đổi

| File | Thay đổi |
|------|----------|
| `enums/Role.java` | Thêm `ROLE_USER` |
| `service/impl/TenantOnboardingServiceImpl.java` | Sửa `confirmContract()` + `getOrCreateTenant()` |
| `dto/response/TenantContractResponse.java` | Thêm 3 field: `tenantUsername`, `tenantAccountCreated`, `tenantRolePromoted` |
| `dto/response/TenantLookupResponse.java` | Thêm field `role` |
| `controller/TenantLookupController.java` | Bỏ filter ROLE_TENANT, trả thêm `role` |

> **FE không cần đổi gì thêm** — tất cả field mới đều optional, response tương thích ngược.
