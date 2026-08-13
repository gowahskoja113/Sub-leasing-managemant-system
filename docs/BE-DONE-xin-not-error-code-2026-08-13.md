# BE DONE — nốt error code vào field `code`

**Ngày:** 13/08/2026  
**Người gửi:** team BE  
**Người nhận:** team FE (mobile + web admin)  
**Phản hồi:** `BE-xin-not-error-code-2026-08-13.md`  
**Liên quan:** `e17a74b` *fix 2 error after review* + `docs/BE-DONE-hai-diem-nho-sau-khi-review-2026-08-13.md`

---

## Tóm tắt

Cả 3 mục FE xin đã sửa. HTTP **vẫn 422** cho lỗi nghiệp vụ; **chỉ** lỗi phân quyền EVN đổi sang **403**.

| # | Mức độ | Việc FE yêu cầu | Trạng thái |
|---|--------|-----------------|------------|
| 1 | 🟡 | 3 chỗ có sẵn tên code → constructor 2 tham số | ✅ Done |
| 2 | 🟢 | 2 chỗ EVN đặt tên `EVN_BILL_*` | ✅ Done |
| 3 | 🟡 | Lỗi phân quyền EVN trả 403, không còn 422 | ✅ Done — `AccessDeniedException` |

**Luồng EVN** (`EvnBillServiceImpl`) có đổi — team FE phụ trách EVN đọc mục 2 + 3 trước khi merge chồng.

---

## 1. 🟡 `PERIOD_ALREADY_SETTLED` / `INVOICE_ALREADY_EXISTS`

### Response (HTTP 422)

```json
{
  "status": 422,
  "code": "INVOICE_ALREADY_EXISTS",
  "message": "Hoá đơn tiền nhà của kỳ này đã tồn tại."
}
```

```json
{
  "status": 422,
  "code": "PERIOD_ALREADY_SETTLED",
  "message": "Kỳ cước này đã được tất toán, không thể tạo thêm hoá đơn tiền nhà."
}
```

Điện/nước trùng kỳ — cùng `code` `INVOICE_ALREADY_EXISTS`, message theo phòng:

```text
Phòng 101 đã nhận hoá đơn điện của kỳ 2026-08.
Nhà nguyên căn đã nhận hoá đơn nước của kỳ 2026-08.
```

### Việc FE làm

- Bắt `error.code === 'INVOICE_ALREADY_EXISTS'` → toast tử tế + nút mở hoá đơn đang có.
- Bắt `error.code === 'PERIOD_ALREADY_SETTLED'` → **không** mở lại (đã tất toán).
- Bỏ parse `"409:"` / tên code trong `message`.

### File

| File | Code |
|------|------|
| `TenantBillingServiceImpl` | `PERIOD_ALREADY_SETTLED`, `INVOICE_ALREADY_EXISTS` (tiền nhà) |
| `UtilityInvoiceServiceImpl` | `INVOICE_ALREADY_EXISTS` (điện/nước) |

---

## 2. 🟢 EVN — 2 mã mới

Cùng pattern, HTTP **422**.

| Code | Khi nào | Message |
|------|---------|---------|
| `EVN_BILL_ALREADY_EXISTS` | Tạo hoá đơn EVN trùng kỳ (đã PUBLISHED) | Đã tồn tại hoá đơn EVN cho kỳ này. |
| `EVN_BILL_IN_USE` | Thu hồi EVN nhưng kỳ đó đã gửi hoá đơn điện cho khách | Không thể thu hồi vì đã có hoá đơn điện được gửi cho khách trong kỳ này. |

```json
{ "status": 422, "code": "EVN_BILL_ALREADY_EXISTS", "message": "Đã tồn tại hoá đơn EVN cho kỳ này." }
```

```json
{ "status": 422, "code": "EVN_BILL_IN_USE", "message": "Không thể thu hồi vì đã có hoá đơn điện được gửi cho khách trong kỳ này." }
```

### Việc FE (luồng EVN) làm

- Bắt `error.code`, không dò chuỗi / không bắt 409.
- Trùng kỳ ≠ không thu hồi được — hai mã khác nhau.

---

## 3. 🟡 Phân quyền EVN → HTTP 403

`GET` danh sách EVN theo `propertyId` mà manager không quản lý nhà đó: **không còn** `BusinessException` (422).

Đổi sang `AccessDeniedException` → handler sẵn có trả **403**.

### Response

```json
{
  "status": 403,
  "error": "Forbidden",
  "code": "FORBIDDEN",
  "path": "/api/v1/...",
  "message": "Bạn không có quyền quản lý nhà này - Kiểm tra lại Role hoặc Vùng quản lý địa lý của tài khoản này!"
}
```

`code: "FORBIDDEN"` là field **mới** trên mọi 403 từ `AccessDeniedException` (kể cả `@PreAuthorize`). Additive — FE cũ bắt `status === 403` vẫn đúng.

### Việc FE làm

- `status === 403` (hoặc `code === 'FORBIDDEN'`) → đá về màn trước / re-auth. **Không** toast rồi cho bấm lại.
- Phân biệt với 422 (`EVN_BILL_*` = dữ liệu/nghiệp vụ).

---

## Việc FE nên bỏ

- Parse `"409:"` / `"403:"` / tên code trong `error.message`.
- Coi 422 EVN “không có quyền” là lỗi form — giờ là 403.

---

## Đã rà — chỗ phân quyền khác **chưa** đổi

FE nhờ kiểm các `BusinessException` kiểu “không có quyền”. Còn các chỗ sau, **vẫn 422 + `code: BUSINESS_ERROR`** — chưa đổi vì đổi 422→403 diện rộng, đụng nhiều màn FE chưa xin trong file này.

| File | Message |
|------|---------|
| `PropertyAccessServiceImpl` | Bạn không có quyền quản lý tòa nhà này *(check trung tâm, nhiều API manager)* |
| `ManagerBillingServiceImpl` | xem hoá đơn / xử lý giao dịch |
| `EquipmentServiceImpl` | xem thiết bị / thiết bị của phòng |
| `MaintenanceServiceImpl` | thao tác trên yêu cầu sửa chữa (3 chỗ) |
| `TenantContractDocumentServiceImpl` | xem hợp đồng |

Nếu FE muốn **403 diện rộng** cho cả nhóm này (đặc biệt `PropertyAccessServiceImpl`), báo lại — BE đổi một lượt sang `AccessDeniedException`.

---

## File đổi

| File | Việc |
|------|------|
| `TenantBillingServiceImpl.java` | `PERIOD_ALREADY_SETTLED`, `INVOICE_ALREADY_EXISTS` |
| `UtilityInvoiceServiceImpl.java` | `INVOICE_ALREADY_EXISTS` |
| `EvnBillServiceImpl.java` | `EVN_BILL_ALREADY_EXISTS`, `EVN_BILL_IN_USE`, `AccessDeniedException` |
| `GlobalExceptionHandler.java` | 403 body thêm `code: "FORBIDDEN"` |

---

## Checklist FE

- [ ] Tạo hoá đơn tiền nhà trùng kỳ → `code === 'INVOICE_ALREADY_EXISTS'` (422) → mở hoá đơn đang có
- [ ] Kỳ đã PAID → `code === 'PERIOD_ALREADY_SETTLED'` (422) → không mở lại
- [ ] Tạo điện/nước trùng kỳ → cùng `INVOICE_ALREADY_EXISTS`
- [ ] EVN trùng kỳ → `EVN_BILL_ALREADY_EXISTS` (422)
- [ ] Thu hồi EVN khi đã gửi điện → `EVN_BILL_IN_USE` (422)
- [ ] Manager xem EVN nhà không thuộc quyền → **403** + `code === 'FORBIDDEN'` → đá màn / re-auth
- [ ] 403 khác (`@PreAuthorize`) cũng có `code: "FORBIDDEN"`
