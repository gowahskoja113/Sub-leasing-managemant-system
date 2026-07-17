# FE — Quy trình bảo trì / sửa chữa (Maintenance)

Base path: `/api/v1/maintenance`  
Auth: Bearer JWT theo role từng API.

> **Phạm vi:** chỉ xử lý tạo → duyệt → sửa ngoài hệ thống → xác nhận hiện trạng.  
> **Không** chọn ai trả chi phí / tính khấu hao / `penaltyFee` trong luồng này — phần đó nằm ở **hóa đơn chi phí chung** (điện, nước, thuê phòng) sau khi ticket `CLOSED`.

---

## 1. Flow tổng quan

```
Tenant tạo (mô tả + ảnh BEFORE)
        ↓
     PENDING
        ↓  Manager approve
     APPROVED          ←──┐  (chờ thợ ngoài sửa)
        ↓  Manager complete│
WAITING_TENANT_CONFIRM    │
        ├─ Tenant confirm → CLOSED  (xong)
        ├─ Hết 3 ngày không phản hồi → CLOSED (auto)
        └─ Tenant reject (lý do + ảnh)
                ↓
             REJECTED
                ├─ Manager review-reject approve=true  → APPROVED (sửa lại)
                └─ Manager review-reject approve=false → WAITING_TENANT_CONFIRM
```

Manager có thể `cancel` ở hầu hết trạng thái (trừ đã `CLOSED` / `CANCELLED`).

---

## 2. Status (dùng cho UI)

| Status | Ý nghĩa | Ai thấy CTA chính |
|--------|---------|-------------------|
| `PENDING` | Tenant vừa tạo, chờ manager duyệt | Manager: **Duyệt** / **Hủy** |
| `APPROVED` | Đã duyệt, đang chờ thợ ngoài sửa | Manager: **Báo sửa xong** / **Hủy** |
| `WAITING_TENANT_CONFIRM` | Manager báo xong, chờ tenant | Tenant: **Xác nhận** / **Từ chối** |
| `REJECTED` | Tenant từ chối kèm lý do + ảnh | Manager: **Chấp nhận sửa lại** / **Không reopen** |
| `CLOSED` | Kết thúc luồng sửa chữa | Chỉ xem |
| `CANCELLED` | Đã hủy | Chỉ xem |

Status legacy (`ACKNOWLEDGED`, `SCHEDULED`, `IN_PROGRESS`, `DONE`, `CONFIRMED`, …) đã migrate — **FE chỉ dùng bảng trên**.

---

## 3. Happy path (từng bước + API)

### 3.1 Tenant tạo yêu cầu

```http
POST /api/v1/maintenance
Authorization: Bearer {tenantToken}
Content-Type: application/json
```

```json
{
  "roomId": 12,
  "equipmentId": 45,
  "title": "Máy lạnh không lạnh",
  "description": "Máy lạnh phòng không lạnh, chảy nước",
  "images": [
    "https://.../before-1.jpg",
    "https://.../before-2.jpg"
  ]
}
```

| Field | Bắt buộc | Ghi chú |
|-------|----------|---------|
| `roomId` | ✅ | Phòng của tenant |
| `title` | ✅ | Tiêu đề sự cố (tối đa 200 ký tự). Hiển thị trên list/detail |
| `description` | ✅ | Mô tả hiện trạng chi tiết |
| `images` | ✅ | Ính BEFORE (URL). Ít nhất 1 ảnh |
| `equipmentId` | ❌ | Nên có nếu hỏng gắn thiết bị |

→ Status: **`PENDING`** — `category` và `priority` **chưa có**, manager gán khi duyệt (§3.2).

> Upload file thô: sau khi có `id`, dùng `POST /{id}/photos?type=BEFORE` (xem §5).

---

### 3.2 Manager duyệt

Manager **bắt buộc chọn `category`** trước khi duyệt — phục vụ phân loại chi phí / báo cáo sau sửa chữa.

```http
PUT /api/v1/maintenance/{id}/approve
Authorization: Bearer {managerToken}
Content-Type: application/json
```

```json
{
  "category": "APPLIANCE",
  "priority": "HIGH"
}
```

| Field | Bắt buộc | Ghi chú |
|-------|----------|---------|
| `category` | ✅ | Phân loại sự cố (xem bảng dưới) |
| `priority` | ❌ | `LOW` \| `MEDIUM` \| `HIGH` \| `URGENT` — tùy chọn |

**Giá trị `category`:**

| Value | Dùng khi |
|-------|----------|
| `APPLIANCE` | Hư trang thiết bị: máy lạnh, tủ lạnh, thiết bị điện gia dụng |
| `FURNITURE` | Hư nội thất: bàn, ghế, tủ |
| `STRUCTURAL` | Sơn tường bong tróc, nước mưa dột, hư hại kết cấu/công trình |
| `ELECTRICAL` | Hệ thống điện, ổ cắm, cầu dao, đèn |
| `PLUMBING` | Ống nước, vòi, nhà vệ sinh, rò rỉ đường ống |
| `OTHER` | Các trường hợp khác |

→ Status: **`APPROVED`** (phòng chuyển `MAINTENANCE`)

---

### 3.3 Manager báo sửa xong

Cần có ảnh **AFTER** (upload trước hoặc gửi kèm URL).

```http
PUT /api/v1/maintenance/{id}/complete
Authorization: Bearer {managerToken}
Content-Type: application/json
```

```json
{
  "resolutionNote": "Đã thay block máy lạnh, chạy thử OK",
  "afterImages": [
    "https://.../after-1.jpg"
  ]
}
```

| Field | Bắt buộc | Ghi chú |
|-------|----------|---------|
| `afterImages` | ✅ nếu chưa upload AFTER | Hoặc upload qua `POST /{id}/photos?type=AFTER` trước |
| `resolutionNote` | ❌ | Ghi chú sau sửa |

→ Status: **`WAITING_TENANT_CONFIRM`**

---

### 3.4 Tenant xác nhận đã sửa xong

```http
PUT /api/v1/maintenance/{id}/confirm
Authorization: Bearer {tenantToken}
Content-Type: application/json
```

```json
{ "accept": true }
```

→ Status: **`CLOSED`**

> Không dùng `accept: false` ở đây. Từ chối phải gọi **`/reject`**.

**Auto-confirm:** nếu tenant không phản hồi sau **3 ngày** (config `maintenance.auto-confirm-days`) → hệ thống tự `CLOSED`.

---

## 4. Nhánh từ chối (reject)

### 4.1 Tenant từ chối

**Cách A — JSON (URL ảnh đã có):**

```http
PUT /api/v1/maintenance/{id}/reject
Authorization: Bearer {tenantToken}
Content-Type: application/json
```

```json
{
  "reason": "Máy vẫn không lạnh sau khi thợ đến",
  "images": [
    "https://.../reject-1.jpg"
  ]
}
```

**Cách B — Multipart (upload file trực tiếp):**

```http
PUT /api/v1/maintenance/{id}/reject
Authorization: Bearer {tenantToken}
Content-Type: multipart/form-data
```

| Part | Bắt buộc |
|------|----------|
| `reason` | ✅ |
| `files` | ✅ (ít nhất 1 ảnh) |

→ Status: **`REJECTED`**  
Response có `rejectReason`, `rejectImages`.

---

### 4.2 Manager xem xét reject

```http
PUT /api/v1/maintenance/{id}/review-reject
Authorization: Bearer {managerToken}
Content-Type: application/json
```

```json
{ "approve": true }
```

| `approve` | Kết quả |
|-----------|---------|
| `true` | → **`APPROVED`** — chấp nhận ý tenant, sửa lại. Ảnh AFTER cũ bị xóa; manager phải complete lại với AFTER mới |
| `false` | → **`WAITING_TENANT_CONFIRM`** — không reopen; tenant confirm lại hoặc chờ auto-confirm |

---

## 5. Upload ảnh

```http
POST /api/v1/maintenance/{id}/photos?type={TYPE}
Authorization: Bearer {token}
Content-Type: multipart/form-data

files: <file1>, <file2>, ...
```

| `type` | Ý nghĩa |
|--------|---------|
| `BEFORE` | Ảnh hiện trạng lúc tạo / bổ sung |
| `AFTER` | Ảnh sau sửa (bắt buộc trước `complete`) |
| `REJECT` | Ảnh minh chứng khi từ chối |

Role: `ADMIN` \| `MANAGER` \| `TENANT`

---

## 6. Hủy (Manager)

```http
PUT /api/v1/maintenance/{id}/cancel?reason=Không còn cần sửa
Authorization: Bearer {managerToken}
```

→ Status: **`CANCELLED`**  
Không hủy được nếu đã `CLOSED` / `CANCELLED`.

---

## 7. API đọc / list / dashboard

### 7.1 List (filter)

```http
GET /api/v1/maintenance?status=&priority=&category=&propertyId=&roomId=&page=0&size=20
Authorization: Bearer {token}
```

- **TENANT:** chỉ request của mình  
- **MANAGER:** chỉ property mình quản lý  
- **ADMIN:** tất cả  

### 7.2 List của tôi (Tenant)

```http
GET /api/v1/maintenance/my-requests?page=0&size=20
```

### 7.3 Chi tiết

```http
GET /api/v1/maintenance/{id}
```

### 7.4 Dashboard (Manager / Admin)

```http
GET /api/v1/maintenance/dashboard
```

| Field | Ý nghĩa trong flow mới |
|-------|------------------------|
| `pending` | `PENDING` |
| `inProgress` | `APPROVED` + `WAITING_TENANT_CONFIRM` + `REJECTED` |
| `resolved` | `CLOSED` |
| `cancelled` | `CANCELLED` |

---

## 8. Response shape (`MaintenanceRequestResponse`)

Dùng chung cho create / list item / detail / mọi action thành công:

```json
{
  "id": 101,
  "requestCode": "M-101",
  "title": "...",
  "description": "...",
  "category": "APPLIANCE",
  "priority": "MEDIUM",
  "status": "WAITING_TENANT_CONFIRM",
  "tenantId": "...",
  "tenantName": "...",
  "tenantPhone": "...",
  "roomId": 12,
  "roomName": "101",
  "propertyId": 3,
  "propertyName": "...",
  "equipmentId": 45,
  "equipmentName": "Máy lạnh",
  "assignedManagerId": "...",
  "assignedManagerName": "...",
  "resolutionNote": "...",
  "rejectReason": null,
  "reopenCount": 0,
  "beforeImages": ["https://..."],
  "afterImages": ["https://..."],
  "rejectImages": [],
  "images": ["https://..."],
  "acknowledgedAt": "2026-07-17T01:00:00",
  "resolvedAt": "2026-07-17T10:00:00",
  "tenantConfirmedAt": null,
  "createdAt": "...",
  "updatedAt": "...",
  "timeline": [
    {
      "oldStatus": "APPROVED",
      "newStatus": "WAITING_TENANT_CONFIRM",
      "note": "Manager báo sửa xong...",
      "changedBy": "...",
      "changedByName": "...",
      "changedAt": "..."
    }
  ]
}
```

> Ưu tiên hiển thị `beforeImages` / `afterImages` / `rejectImages`. Field `images` là gộp cả ba (tương thích cũ).

Các field `repairCost`, `costPaidBy`, `cause`, `scheduledDate` có thể còn trên response nhưng **không dùng trong UI flow này** (dành billing sau).

---

## 9. Gợi ý UI theo role

### Tenant

| Màn | Hành động |
|-----|-----------|
| Tạo request | Form: phòng, (thiết bị), **tiêu đề**, mô tả, ảnh BEFORE (không cần category/priority) |
| List của tôi | Badge theo `status` |
| `WAITING_TENANT_CONFIRM` | Nút **Đã OK** → `confirm`; **Chưa ổn** → form reject (lý do + ảnh) |
| `REJECTED` | Chờ manager xem xét (read-only) |
| `CLOSED` | Xem lại timeline + ảnh |

### Manager

| Màn | Hành động |
|-----|-----------|
| `PENDING` | Chọn **category** (bắt buộc) + (tùy chọn priority) → **Duyệt** / **Hủy** |
| `APPROVED` | Upload AFTER + **Báo sửa xong** / **Hủy** |
| `WAITING_TENANT_CONFIRM` | Chờ tenant (có thể hiện countdown ~3 ngày) |
| `REJECTED` | Hiện `rejectReason` + `rejectImages` → **Sửa lại** (`approve: true`) / **Giữ kết quả** (`approve: false`) |

---

## 10. API đã gỡ (không gọi nữa)

| API cũ | Thay bằng |
|--------|-----------|
| `PUT /{id}/acknowledge` | `PUT /{id}/approve` |
| `PUT /{id}/schedule` | — (bỏ lịch) |
| `PUT /{id}/confirm-schedule` | — |
| `PUT /{id}/status` | `cancel` / các action riêng |
| `PUT /{id}/resolve` | `PUT /{id}/complete` |
| `PUT /{id}/approve` (duyệt chi phí >2tr) | Đổi nghĩa → duyệt request; chi phí → luồng hóa đơn |

---

## 11. Lưu ý FE

1. Mọi action đổi status đều trả full `MaintenanceRequestResponse` — cập nhật UI từ response, không cần GET lại (vẫn nên GET khi vào detail).
2. Enforce CTA theo `status` ở FE; BE cũng validate — nếu sai bước sẽ `BusinessException`.
3. Chi phí hư hỏng (case còn BH / hết BH + `penaltyFee` hoặc 40% / hao mòn tự nhiên) **không** nằm trong maintain — làm ở màn hóa đơn sau `CLOSED`, tham chiếu `maintenanceRequestId` + `equipmentId`.
