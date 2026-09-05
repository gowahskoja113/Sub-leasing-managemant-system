# Lịch hẹn bảo trì + quét QR — Implementation Spec (As-Built)

> Backend SLMS2026 — theo `BE-YEUCAU-lich-hen-bao-tri-quet-qr-2026-09-05.md`  
> **Ngày implement:** 05/09/2026  
> **Trạng thái:** ✅ Backend đã ship — FE có thể tích hợp theo contract dưới đây

---

## 1. Quyết định đã chốt (câu hỏi trong yêu cầu)

| # | Quyết định |
|---|------------|
| 1 | Slot mặc định: **30 phút** (VISIT), **60 phút** (REPAIR) |
| 2 | **Không** auto-huỷ `REPAIR_SCHEDULED` ở đợt này |
| 3 | Đổi lịch sửa: **chỉ manager** (`/reschedule-repair`) |
| 4 | `GET /{id}` **luôn** trả `issuedInvoice` khi `billingHint = TENANT_CHARGE_PENDING` và hoá đơn chưa `PAID`/`CANCELLED` |

QR gate: **chỉ phía app** — BE không validate quét QR.

---

## 2. Model

### Cột mới `maintenance_requests`

| Field | Cột DB | Khi set |
|-------|--------|---------|
| `visitAppointmentAt` | `visit_appointment_at` | Tạo phiếu / đổi lịch xem |
| `visitArrivalConfirmedAt` | `visit_arrival_confirmed_at` | `PUT …/confirm-arrival` |
| `repairAppointmentAt` | `repair_appointment_at` | Approve/reject-fault có lịch sau, hoặc đổi lịch sửa |
| `repairStartedAt` | `repair_started_at` | `PUT …/start-repair` |

### Status mới

`REPAIR_SCHEDULED` — phòng chờ giữa đánh giá xong và bắt đầu sửa (khi chọn “đặt lịch sau”).

```
OPEN (+ visitAppointmentAt)
  --cron 2h no-show--> CANCELLED
  --confirm-arrival--> OPEN (chỉ ghi mốc)
  --approve (không repairAt)--> IN_REPAIR --> CLOSED
  --approve (+ repairAt)--> REPAIR_SCHEDULED --start-repair--> IN_REPAIR --> CLOSED
  --reject-fault MANAGER_REPAIR (không repairAt)--> TENANT_FAULT --> CLOSED
  --reject-fault MANAGER_REPAIR (+ repairAt)--> REPAIR_SCHEDULED --start-repair--> TENANT_FAULT --> CLOSED
  --reject-fault TENANT_SELF_REPAIR--> PENDING_TENANT_REPAIR → …
```

Phiếu cũ `visitAppointmentAt == null`: bỏ qua điều kiện `visitArrivalConfirmedAt` khi approve/reject-fault.

---

## 3. API

Base: `/api/v1/maintenance`

| Method | Path | Ghi chú |
|--------|------|---------|
| `POST` | `/` | **Bắt buộc** `visitAppointmentAt`; nhà phải có `operationManagerId` |
| `PUT` | `/{id}/reschedule-visit` | Body `{ visitAppointmentAt }` — OPEN, chưa confirm arrival, còn **trước ngày** hẹn |
| `PUT` | `/{id}/confirm-arrival` | Manager — ghi `visitArrivalConfirmedAt`, không đổi status |
| `PUT` | `/{id}/approve` | Optional `repairAppointmentAt`; cần confirm arrival (trừ phiếu cũ) |
| `PUT` | `/{id}/reject-fault` | Optional `repairAppointmentAt` (MANAGER_REPAIR); cùng điều kiện arrival |
| `PUT` | `/{id}/reschedule-repair` | Manager — body `{ repairAppointmentAt }` — chỉ `REPAIR_SCHEDULED` |
| `PUT` | `/{id}/start-repair` | Manager — `REPAIR_SCHEDULED` → `IN_REPAIR` / `TENANT_FAULT` theo `flowType` |
| `GET` | `/manager-availability` | Query: `propertyId` **hoặc** `managerId`, `from`, `to` |
| `PUT` | `/{id}/cancel` | Không đổi — manager huỷ được cả `REPAIR_SCHEDULED` |

### `GET /manager-availability` response

```json
[
  {
    "requestId": 123,
    "requestCode": "M-123",
    "type": "VISIT",
    "start": "2026-09-10T09:00:00",
    "end": "2026-09-10T09:30:00",
    "propertyName": "...",
    "roomNumber": "P101"
  }
]
```

`end` do BE tính (VISIT +30m / REPAIR +60m). Chỉ phiếu `OPEN` (visit) hoặc `REPAIR_SCHEDULED` (repair).

### Validate

- Giờ hành chính **07:00–18:00** (cả giờ **kết thúc** slot)
- Không trùng slot cùng manager → **409 Conflict**
- Đổi lịch chỉ khi `LocalDate.now(Asia/Ho_Chi_Minh)` **trước** ngày của `appointmentAt`
- Nhà chưa gán `operationManagerId` → 400 rõ ràng khi tạo/đặt lịch

---

## 4. Cron

```
0 */10 * * * *  Asia/Ho_Chi_Minh
```

`OPEN` + `visitAppointmentAt` đã qua > 2h + `visitArrivalConfirmedAt == null` → `CANCELLED` + timeline + notify tenant/manager + `MAINTENANCE_SCHEDULE_CHANGED`.

---

## 5. Realtime

- Event mới: `MAINTENANCE_SCHEDULE_CHANGED`
- Kênh: `/user/queue/maintenance` (tenant + manager phụ trách)
- Payload thêm: `visitAppointmentAt?`, `repairAppointmentAt?`

Bắn khi: tạo có hẹn, đổi lịch, confirm arrival, đặt/đổi lịch sửa, start-repair, cron no-show.

---

## 6. Response `MaintenanceRequestResponse` — field mới

- `visitAppointmentAt`, `visitArrivalConfirmedAt`, `repairAppointmentAt`, `repairStartedAt`
- `issuedInvoice` — có trên mọi GET khi còn hoá đơn bảo trì chưa thanh toán

---

## 7. File liên quan

| File | Vai trò |
|------|---------|
| `enums/MaintenanceStatus.java` | + `REPAIR_SCHEDULED` |
| `entity/MaintenanceRequest.java` | 4 cột lịch hẹn |
| `config/DatabaseSchemaMigration.java` | Cột + CHECK status |
| `controller/MaintenanceController.java` | Endpoint mới |
| `service/impl/MaintenanceServiceImpl.java` | Logic slot / cron / approve nhánh |
| `service/RealtimeEventService.java` | `EVT_MAINTENANCE_SCHEDULE_CHANGED` |
| `dto/response/ManagerAvailabilitySlotResponse.java` | Calendar bận |

---

*Tài liệu as-built — cập nhật khi đổi contract.*
