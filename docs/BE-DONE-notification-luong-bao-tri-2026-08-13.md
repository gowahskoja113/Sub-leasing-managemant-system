# BE DONE — thông báo luồng bảo trì gửi xuống khách thuê

**Ngày:** 13/08/2026  
**Người gửi:** team BE  
**Người nhận:** team FE (mobile)  
**Phản hồi:** `BE-xin-notification-luong-bao-tri-2026-08-13.md`  
**File:** `MaintenanceServiceImpl.java`

---

## Tóm tắt

Khách thuê **được báo** khi quản lý duyệt / sửa xong / chốt tiền / huỷ. Type tách theo sự kiện — FE **bỏ regex** tiêu đề. In-app + push có `screen` / `params`.

| # | Mức độ | Việc FE yêu cầu | Trạng thái |
|---|--------|-----------------|------------|
| 1 | 🔴 | `complete()` báo khách trước khi auto-confirm | ✅ Done |
| 2 | 🔴 | `resolveCost()` báo khách khi chốt tiền | ✅ Done |
| 3 | 🟡 | `approve()` / `cancel()` báo khách | ✅ Done |
| 4 | 🟡 | Tách `MAINTENANCE` thành nhiều type | ✅ Done |
| 5 | 🟢 | `screen` + `params` | ✅ Done |

**Ghi chú code cũ:** `addTimeline` từng bắn tin generic `"Cập nhật yêu cầu bảo trì"` + type `MAINTENANCE` (status enum trong body). FE grep `notifyProperty*` nên không thấy. Đã **gỡ** tin generic — tránh 2 tin trùng sau khi thêm `notifyTenant`.

---

## Bộ type (mỗi sự kiện một type)

| Type | Ai nhận | Khi nào | `screen` | `params` |
|------|---------|---------|----------|----------|
| `MAINTENANCE_CREATED` | Quản lý | Khách tạo yêu cầu | `MaintenanceTicketDetail` | `{ "ticketId" }` |
| `MAINTENANCE_APPROVED` | Khách | Quản lý duyệt *hoặc* chấp nhận sửa lại | `MaintenanceDetail` | `{ "requestId" }` |
| `MAINTENANCE_COMPLETED` | Khách | Quản lý báo sửa xong / giữ nguyên kết quả (cần xác nhận lại) | `MaintenanceDetail` | `{ "requestId" }` |
| `MAINTENANCE_COST_RESOLVED` | Khách | Chốt CHARGE hoặc WAIVE | `MaintenanceDetail` | `{ "requestId" }` |
| `MAINTENANCE_CANCELLED` | Khách | Quản lý huỷ (tenant tự huỷ **không** tự báo mình) | `MaintenanceDetail` | `{ "requestId" }` |
| `MAINTENANCE_REJECTED_BY_TENANT` | Quản lý | Khách từ chối kết quả | `MaintenanceTicketDetail` | `{ "ticketId" }` |
| `MAINTENANCE_COST_DISPUTED` | Quản lý | Khách khiếu nại chi phí | `MaintenanceTicketDetail` | `{ "ticketId" }` |
| `MAINTENANCE_AUTO_CONFIRMED` | Quản lý | Cron tự đóng sau N ngày | `MaintenanceTicketDetail` | `{ "ticketId" }` |
| `MAINTENANCE_REOPEN_ESCALATION` | Host | ≥2 lần từ chối — **giữ nguyên** (bảng host, không đổi) | — | — |

Type cũ `MAINTENANCE` **không còn** phát. Bản ghi in-app cũ vẫn có thể là `MAINTENANCE` — FE giữ fallback regex một thời gian rồi bỏ.

---

## Payload in-app + push

### Khách

```json
{
  "type": "MAINTENANCE_COMPLETED",
  "screen": "MaintenanceDetail",
  "paramsJson": "{\"requestId\":42}"
}
```

Push data:

```json
{
  "type": "MAINTENANCE_COMPLETED",
  "screen": "MaintenanceDetail",
  "requestId": 42,
  "params": { "requestId": 42 }
}
```

Bấm tin → `MaintenanceDetail` + `GET /api/v1/maintenance-requests/{id}` (FE đã sửa 13/08).

### Quản lý

```json
{
  "type": "MAINTENANCE_CREATED",
  "screen": "MaintenanceTicketDetail",
  "paramsJson": "{\"ticketId\":42}"
}
```

Push: `ticketId` + `params.ticketId`. Màn `MaintenanceTicketDetail`.

---

## 1. 🔴 `complete()` — báo khách trước auto-confirm

Sau khi status = `WAITING_TENANT_CONFIRM`, bắn `MAINTENANCE_COMPLETED`.

| | Tiêu đề | Body (rút gọn) |
|---|---------|----------------|
| Lần đầu sửa xong | Đã sửa xong — vui lòng xác nhận | …đã sửa xong. Vui lòng xác nhận trong app trong **N ngày** — quá hạn hệ thống sẽ tự đóng. |
| Manager cập nhật lại (đã WAITING) | Cập nhật kết quả sửa chữa — vui lòng xác nhận lại | cùng hạn N ngày |
| Có bồi thường (`costPaidBy=TENANT`) | (như trên) | thêm `Có khoản bồi thường Xđ.` |

`N` = `maintenance.auto-confirm-days` (mặc định **3**).

Cron 8h30 vẫn tự đóng + báo quản lý `MAINTENANCE_AUTO_CONFIRMED`. Khách **đã** được hỏi từ lúc `complete()` — timeline *"không phản hồi"* lúc này mới đúng nghĩa.

---

## 2. 🔴 `resolveCost()` — chốt tiền

| action | Tiêu đề | Type |
|--------|---------|------|
| `CHARGE` | Đã chốt chi phí bồi thường | `MAINTENANCE_COST_RESOLVED` |
| `WAIVE` | Đã miễn thu bồi thường | `MAINTENANCE_COST_RESOLVED` |

Body có số tiền (CHARGE) + note nếu manager ghi.

---

## 3. 🟡 `approve()` / `cancel()`

| Hàm | Type | Ghi chú |
|-----|------|---------|
| `approve()` | `MAINTENANCE_APPROVED` | Khách biết đã tiếp nhận |
| `cancel()` do **manager** | `MAINTENANCE_CANCELLED` | Kèm lý do |
| `cancel()` do **tenant** | — | Không tự báo mình |

---

## Bonus (tránh mất tin sau khi gỡ generic)

`reviewReject` trước dựa vào tin generic của `addTimeline`. Giờ:

| Manager làm | Type gửi khách |
|-------------|----------------|
| Chấp nhận sửa lại → `APPROVED` | `MAINTENANCE_APPROVED` |
| Giữ nguyên kết quả → chờ confirm lại | `MAINTENANCE_COMPLETED` |

---

## Việc FE làm

- Map **type** → icon / nhánh. **Bỏ** `/mới/i`, `DONE|CONFIRMED` trên title/content.
- Deep-link khách: `screen === 'MaintenanceDetail'` + `params.requestId`.
- Deep-link quản lý: `screen === 'MaintenanceTicketDetail'` + `params.ticketId`.
- `MAINTENANCE_COMPLETED` là tin khách **phải** thấy — cửa sổ xác nhận trước auto-confirm.
- `MAINTENANCE_COST_RESOLVED` là tin tiền — hiện rõ số.

---

## File đổi

| File | Việc |
|------|------|
| `MaintenanceServiceImpl.java` | `notifyTenant` + type/screen/params; gỡ notify generic trong `addTimeline` |

API / DTO **không** đổi.

---

## Checklist FE

- [ ] Khách tạo ticket → quản lý nhận `MAINTENANCE_CREATED` → `MaintenanceTicketDetail`
- [ ] Manager duyệt → khách nhận `MAINTENANCE_APPROVED` → `MaintenanceDetail`
- [ ] Manager complete → khách nhận `MAINTENANCE_COMPLETED` (có hạn N ngày + tiền nếu có)
- [ ] Manager CHARGE/WAIVE → khách nhận `MAINTENANCE_COST_RESOLVED`
- [ ] Manager huỷ → khách nhận `MAINTENANCE_CANCELLED`
- [ ] Khách reject → quản lý `MAINTENANCE_REJECTED_BY_TENANT`
- [ ] Khách dispute cost → quản lý `MAINTENANCE_COST_DISPUTED`
- [ ] Cron auto-confirm → quản lý `MAINTENANCE_AUTO_CONFIRMED`
- [ ] Bấm in-app (không chỉ push) mở đúng ticket
- [ ] Bỏ regex title/content
