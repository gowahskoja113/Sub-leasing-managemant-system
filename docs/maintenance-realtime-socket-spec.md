# Maintenance Realtime Socket — Implementation Spec (As-Built)

> Tài liệu ghi nhận **code đã triển khai** trên backend SLMS2026.  
> Yêu cầu gốc: BE-YEUCAU — realtime socket luồng bảo trì (03/09/2026)
>
> **Ngày implement:** 03/09/2026  
> **Trạng thái:** ✅ Backend đã ship — FE có thể subscribe `/user/queue/maintenance`

---

## 1. Tóm tắt

| Khía cạnh | Trước | Sau (as-built) |
|-----------|-------|----------------|
| Queue STOMP | Chỉ `/user/queue/billing` | Thêm `/user/queue/maintenance` (cùng kết nối `/ws`) |
| `RealtimeEventService` | 3 method billing/contract | + `publishMaintenanceEvent` |
| `MaintenanceServiceImpl` | Không đẩy WS | Publish sau mỗi thao tác đổi trạng thái (9 điểm) |
| Push + khay chuông | Một số chỗ đã có `saveAndPush` | Bổ sung đủ 3 chỗ còn thiếu: `reportFault`, `adminReviewFault`, cancel-by-tenant |

---

## 2. Contract FE

### 2.1 Kết nối

- Endpoint: `/ws` (STOMP thuần, không SockJS) — **không đổi**
- Auth: header `Authorization: Bearer <token>` trên frame `CONNECT` — **không đổi**
- Trong `onConnect` sẵn có của `useBillingRealtime` (hoặc singleton client):

```ts
client.subscribe('/user/queue/maintenance', (frame) => {
  const payload = JSON.parse(frame.body);
  // refetch màn đang mở
});
```

Không mở `new Client(...)` thứ hai.

### 2.2 Payload (`MaintenanceRealtimeEvent`)

```json
{
  "event": "MAINTENANCE_FAULT_REPORTED",
  "requestId": 123,
  "requestCode": "M-123",
  "status": "TENANT_FAULT",
  "propertyId": 45,
  "propertyName": "MTX#01 ...",
  "roomId": 7,
  "roomNumber": "P101",
  "tenantUserId": "uuid...",
  "assignedManagerId": "uuid...",
  "adminApproved": null
}
```

| Field | Kiểu | Ghi chú |
|-------|------|---------|
| `event` | string | Xem bảng eventType bên dưới |
| `requestId` | number | PK phiếu |
| `requestCode` | string | DB hoặc fallback `M-{id}` |
| `status` | string | `MaintenanceStatus` name sau khi save |
| `propertyId` / `propertyName` | | Nhà liên quan |
| `roomId` / `roomNumber` | | Nullable nếu thuê nguyên căn |
| `tenantUserId` | UUID | User khách thuê |
| `assignedManagerId` | UUID | `property.operationManagerId` |
| `adminApproved` | boolean \| null | Sau `admin-review`; null nếu chưa duyệt |

### 2.3 Event types & người nhận

| Endpoint | `event` | Ai nhận WS |
|---|---|---|
| `POST /maintenance` | `MAINTENANCE_CREATED` | Manager phụ trách nhà (`operationManagerId`); nếu null → mọi `ROLE_MANAGER` ACTIVE |
| `PUT /{id}/approve` | `MAINTENANCE_APPROVED` | Tenant |
| `PUT /{id}/reject-fault` | `MAINTENANCE_REJECT_FAULT` | Tenant |
| `PUT /{id}/report-fault` | `MAINTENANCE_FAULT_REPORTED` | **Admin** + Tenant |
| `PUT /{id}/admin-review` | `MAINTENANCE_ADMIN_REVIEWED` | Manager |
| `PUT /{id}/submit-self-repair` | `MAINTENANCE_SELF_REPAIR_SUBMITTED` | Manager |
| `PUT /{id}/verify-repair` | `MAINTENANCE_VERIFY_REPAIR` | Tenant |
| `PUT /{id}/complete` | `MAINTENANCE_COMPLETED` | Tenant |
| `PUT /{id}/cancel` (tenant) | `MAINTENANCE_CANCELLED_BY_TENANT` | Manager |
| `PUT /{id}/cancel` (manager) | `MAINTENANCE_CANCELLED_BY_MANAGER` | Tenant |

FE có thể refetch mọi màn khi nhận bất kỳ event nào liên quan `requestId` / role hiện tại — không bắt buộc branch theo từng `event`.

### 2.4 Checklist FE

- [ ] Subscribe `/user/queue/maintenance` trên client STOMP đang có
- [ ] `MaintenanceFaultReview` refetch khi `MAINTENANCE_FAULT_REPORTED`
- [ ] `MaintenanceManagerScreen` refetch khi `MAINTENANCE_CREATED` / `MAINTENANCE_ADMIN_REVIEWED` / `MAINTENANCE_SELF_REPAIR_SUBMITTED` / cancel-by-tenant
- [ ] Màn tenant (`TicketDetail` / history / detail) refetch khi approve / reject / verify / complete / cancel-by-manager / fault-reported

---

## 2b. Push + khay chuông (đã ship)

Pattern sẵn có trong `MaintenanceServiceImpl`: `saveAndPush` → ghi bảng `notifications` + `userPushTokenService.sendToUser` (nhiều thiết bị).

| Endpoint | type | Người nhận | screen / params |
|---|---|---|---|
| create | `MAINTENANCE_CREATED` | Manager | `MaintenanceTicketDetail` / `ticketId` |
| approve | `MAINTENANCE_APPROVED` | Tenant | `MaintenanceDetail` / `requestId` |
| reject-fault | `MAINTENANCE_SELF_REPAIR_ASSIGNED` / `MAINTENANCE_TENANT_FAULT` | Tenant | `MaintenanceDetail` / `requestId` |
| **report-fault** | `MAINTENANCE_FAULT_REPORTED` | **Admin** + Tenant | Admin: `MaintenanceFaultReview`/`requestId`; Tenant: `MaintenanceDetail`/`requestId` |
| **admin-review** | `MAINTENANCE_ADMIN_REVIEWED` | Manager | `MaintenanceTicketDetail` / `ticketId` |
| submit-self-repair | `MAINTENANCE_SELF_REPAIR_SUBMITTED` | Manager | `MaintenanceTicketDetail` / `ticketId` |
| verify-repair | `MAINTENANCE_COMPLETED` / `MAINTENANCE_SELF_REPAIR_OVERDUE` | Tenant | `MaintenanceDetail` / `requestId` |
| complete | `MAINTENANCE_COMPLETED` | Tenant | `MaintenanceDetail` / `requestId` |
| cancel (manager) | `MAINTENANCE_CANCELLED` | Tenant | `MaintenanceDetail` / `requestId` |
| **cancel (tenant)** | `MAINTENANCE_CANCELLED` | Manager | `MaintenanceTicketDetail` / `ticketId` |

FE mobile: không cần sửa — deep-link theo `type` chứa `MAINTENANCE` + đọc `/notifications` đã có.

---

## 3. File code liên quan

| File | Vai trò |
|------|---------|
| `dto/response/MaintenanceRealtimeEvent.java` | Payload WS |
| `service/RealtimeEventService.java` | Interface + hằng `EVT_MAINTENANCE_*` |
| `service/impl/RealtimeEventServiceImpl.java` | Build payload + route recipient + `convertAndSendToUser(..., /queue/maintenance)` |
| `service/impl/MaintenanceServiceImpl.java` | Gọi `publishMaintenanceEvent` sau `save` |
| `config/WebSocketConfig.java` | Không đổi — broker `/queue` đã bật |

---

## 4. Lưu ý deploy

Nginx trên VPS từng không forward header `Upgrade` → STOMP có thể không connect được ở môi trường thật (billing cũng vậy). Nên xác nhận nginx WS proxy cùng lúc để billing + maintenance cùng hưởng.

---

*Tài liệu as-built — cập nhật khi có thay đổi API/event.*
