# BE DONE — đổi nốt lỗi phân quyền sang 403

**Ngày:** 13/08/2026  
**Người gửi:** team BE  
**Người nhận:** team FE (mobile + web admin)  
**Phản hồi:** `BE-xin-doi-not-loi-phan-quyen-403-2026-08-13.md`  
**Liên quan:** `94f4dd9` + `docs/BE-DONE-xin-not-error-code-2026-08-13.md`

---

## Tóm tắt

10 chỗ FE xin + 1 chỗ cùng hàm `assertCanView` đã đổi sang `AccessDeniedException`. HTTP **403** + `code: "FORBIDDEN"`. `@PreAuthorize` **không** đụng. Message **giữ tiếng Việt**.

| # | Mức độ | Việc FE yêu cầu | Trạng thái |
|---|--------|-----------------|------------|
| 1 | 🟡 | 10 chỗ → `AccessDeniedException` | ✅ Done |
| 2 | 🟢 | Giữ `message` tiếng Việt trong body 403 | ✅ Done |
| 3 | 🟢 | Tách câu 3 chỗ `MaintenanceServiceImpl` | ✅ Done |

`PropertyAccessServiceImpl.assertCanManageProperty` đổi 1 dòng → **11 lời gọi / 7 file** manager API ra 403 đúng chuẩn.

---

## Response (không đổi shape)

Giống EVN lần trước. Web `api.ts` đã bắt được.

```json
{
  "status": 403,
  "error": "Forbidden",
  "code": "FORBIDDEN",
  "path": "/api/v1/...",
  "message": "<câu tiếng Việt> - Kiểm tra lại Role hoặc Vùng quản lý địa lý của tài khoản này!"
}
```

- `error` vẫn `"Forbidden"` (chữ máy) — FE đảo ưu tiên, đọc `message`.
- `code: "FORBIDDEN"` → web **không** retry cold-start.
- Suffix handler 403 **giữ nguyên** (không đổi `GlobalExceptionHandler`).

---

## 1. 10 chỗ FE liệt kê

| File | Message (giữ nguyên, trừ Maintenance) |
|------|----------------------------------------|
| `PropertyAccessServiceImpl` | Bạn không có quyền quản lý tòa nhà này |
| `ManagerBillingServiceImpl` | Bạn không có quyền xem hoá đơn này |
| `ManagerBillingServiceImpl` | Bạn không có quyền xử lý giao dịch này |
| `EquipmentServiceImpl` | Bạn không có quyền xem thiết bị này |
| `EquipmentServiceImpl` | Bạn không có quyền xem thiết bị của phòng này |
| `TenantContractDocumentServiceImpl` | Bạn không có quyền xem hợp đồng này *(tenant + role khác)* |

### Bonus cùng hàm `assertCanView`

FE không liệt kê dòng manager, nhưng cùng check quyền xem HĐ — để 422 thì lệch tenant 403:

| File | Message |
|------|---------|
| `TenantContractDocumentServiceImpl` | Bạn không quản lý toà nhà của hợp đồng này |

---

## 2. Maintenance — 3 câu tách ngữ cảnh

Trước: cả 3 chỗ cùng `"Bạn không có quyền thao tác trên yêu cầu này"`.

| Hàm | Khi nào | Message mới |
|-----|---------|-------------|
| `requireTenantOwner` | User không phải tenant của ticket | Yêu cầu sửa chữa này không thuộc về bạn |
| `requireManagerAccess` | Không phải manager / admin | Chỉ quản lý vận hành mới được thao tác trên yêu cầu sửa chữa này |
| `requireManagerAccess` | Manager nhưng không quản lý nhà của ticket | Bạn không quản lý tòa nhà của yêu cầu sửa chữa này |

Vẫn 403 + `code: "FORBIDDEN"`. FE hiện `message` (có suffix handler).

---

## 3. Đã rà `try/catch (BusinessException)`

Không chỗ nào bọc 10 hàm này rồi nuốt exception. `AccessDeniedException` đi thẳng lên `GlobalExceptionHandler` → 403.

Các `catch (BusinessException)` còn lại (OCR / Vision / PayOS / dashboard resolve HĐ / auth) **không** liên quan — không đổi.

`@PreAuthorize` **không** đụng.

---

## Việc FE làm

- 403 + `code === 'FORBIDDEN'` → toast `message`, đá màn trước, **không** retry.
- 422 + `BUSINESS_ERROR` / mã nghiệp vụ → toast + giữ màn (dữ liệu).
- Web: không cần sửa thêm `api.ts` (đã sẵn 13/08).
- Mobile: mỗi màn bắt 403 như đang làm.

---

## File đổi

| File | Việc |
|------|------|
| `PropertyAccessServiceImpl.java` | `AccessDeniedException` (11 call-site manager) |
| `ManagerBillingServiceImpl.java` | 2 chỗ xem HĐ / xử lý claim |
| `EquipmentServiceImpl.java` | 2 chỗ tenant xem thiết bị |
| `MaintenanceServiceImpl.java` | 3 chỗ + câu cụ thể |
| `TenantContractDocumentServiceImpl.java` | 3 chỗ (2 FE xin + 1 manager cùng hàm) |

---

## Checklist FE

- [ ] Manager mở nhà không thuộc quyền → 403 `FORBIDDEN` (mọi API qua `assertCanManageProperty`)
- [ ] Manager xem hoá đơn / duyệt claim nhà người khác → 403
- [ ] Tenant xem thiết bị / phòng không phải HĐ ACTIVE của mình → 403
- [ ] Tenant / manager / role khác xem PDF HĐ không thuộc mình → 403
- [ ] Ticket sửa chữa: 3 câu `message` khác nhau theo ngữ cảnh
- [ ] 403 có `code: "FORBIDDEN"` → web không retry cold-start
- [ ] `error` vẫn `"Forbidden"`; UI lấy `message`
- [ ] Lỗi dữ liệu (sai trạng thái, validation) **vẫn 422**
