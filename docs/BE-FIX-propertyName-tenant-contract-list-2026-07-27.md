# BE Fix — `propertyName` null trong list hợp đồng chờ xử lý

**Ngày:** 2026-07-27  
**Nguồn yêu cầu FE:** `BE-HANDOFF-propertyName-missing-contract-list-2026-07-27.md`  
**Trạng thái:** Đã fix

---

## 1. Hiện tượng (FE báo)

Màn mobile manager **“Hợp đồng chờ xử lý”** cần hiện tên bất động sản trên mỗi card.

API:

```http
GET /api/v1/tenant-contracts?status=...
```

DTO `TenantContractResponse` đã có field `propertyName`, nhưng response luôn trả `null` → UI không hiện tên property.

---

## 2. Nguyên nhân

Hàm map chung `TenantOnboardingServiceImpl.toResponse(TenantContract, …)` set `propertyId` nhưng **không set** `propertyName`.

Các service khác (`HostPortalServiceImpl`, `TenantHandoverServiceImpl`, `TenantPendingChargeServiceImpl`…) đã set đúng field này.

---

## 3. Thay đổi BE

**File:** `src/main/java/com/sep490/slms2026/service/impl/TenantOnboardingServiceImpl.java`

Trong `toResponse(...)`, thêm:

```java
.propertyId(c.getProperty().getId())
.propertyName(c.getProperty().getPropertyName())   // ← thêm
.roomId(room != null ? room.getId() : null)
```

Không thêm null-check: contract luôn gắn `property` trong ngữ cảnh này.

---

## 4. Phạm vi ảnh hưởng

Các API dùng chung `toResponse(...)`:

| API / luồng | Ảnh hưởng |
|-------------|-----------|
| `GET /api/v1/tenant-contracts?status=...` (`getContractsByStatus`) | List manager — **đúng màn FE cần** |
| Response sau `confirm` HĐ | Cũng có `propertyName` |

Endpoint tự map riêng (handover, checkout, pending-charge…) **không đổi**.

---

## 5. Cách FE verify

1. Gọi `GET /api/v1/tenant-contracts?status=PENDING` (hoặc status màn “chờ xử lý” đang dùng).
2. Mỗi item có `"propertyName": "<tên BĐS>"` (không còn `null`).
3. Card list mobile hiện tên property đúng.

Ví dụ response (rút gọn):

```json
{
  "id": 101,
  "propertyId": 12,
  "propertyName": "Nhà Lê Lợi 01",
  "roomId": 5,
  "roomNumber": "P01",
  "status": "PENDING"
}
```

---

## 6. Checklist

- [x] Map `propertyName` trong `toResponse`
- [x] Không đụng endpoint/service map riêng khác
- [ ] FE verify list “Hợp đồng chờ xử lý” hiện tên BĐS
