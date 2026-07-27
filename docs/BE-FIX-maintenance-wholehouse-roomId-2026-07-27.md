# BE Fix — `POST /maintenance` nguyên căn thiếu `roomId`

**Ngày:** 2026-07-27  
**Nguồn:** Phát hiện phụ khi soát multi-contract / FE `MaintenanceCreateScreen`  
**Trạng thái:** Đã fix

---

## 1. Hiện tượng

`POST /api/v1/maintenance` luôn gọi:

```java
roomRepository.findById(request.getRoomId())
```

Khi tenant **nguyên căn** (dashboard `room.id = null`) FE không gửi `roomId` → NPE / 500 thay vì lỗi business rõ ràng.  
Entity `MaintenanceRequest.room` vốn đã `nullable = true`.

---

## 2. Fix

| Case | Body | Kết quả |
|------|------|---------|
| Thuê theo phòng | `roomId` bắt buộc | Như cũ; `property` lấy từ phòng |
| Nguyên căn | `roomId` null + **`propertyId` bắt buộc** | Ticket `room = null`, gắn property; verify HĐ ACTIVE nguyên căn của tenant |
| Cả hai thiếu | — | `BusinessException` message rõ |

File chính: `MaintenanceCreateRequest`, `MaintenanceServiceImpl.createRequest`.

---

## 3. FE cần làm

- Nguyên căn: lấy `propertyId` từ dashboard (`building.propertyId` / `contracts[].propertyId`), **không** bắt `roomId`.
- Thuê phòng: vẫn gửi `roomId`.
- Chi tiết: [`FE-maintenance-non-equipment-create.md`](./FE-maintenance-non-equipment-create.md) §4.2b.
