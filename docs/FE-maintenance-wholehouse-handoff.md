# FE Handoff — Bảo trì nguyên căn & dashboard `contract.type`

**Ngày:** 2026-07-26  
**BE đã ship:** hỗ trợ tạo ticket bảo trì cho HĐ nguyên căn + thêm `contract.type` trên dashboard.

---

## Tóm tắt cho FE

| Trước | Sau |
|-------|-----|
| Dashboard không có `contract.type` | Có `contract.type`: `ROOM` \| `WHOLE_HOUSE` |
| `POST /maintenance` bắt buộc `roomId` | `roomId` **optional** với nguyên căn |
| Nguyên căn → dead-end trên app | Gửi ticket **không cần** `roomId` |

FE có thể **bỏ** alert kiểu *"Báo hỏng cho nhà thuê nguyên căn chưa được hỗ trợ"* và cho tenant tạo request bình thường.

---

## 1. Dashboard — field mới

```http
GET /api/v1/tenant/me/dashboard
Authorization: Bearer {tenantToken}
```

`contract` giờ có `type`:

```json
{
  "room": {
    "id": null,
    "roomNumber": "Nhà Lê Lợi 01",
    "floor": null,
    "area": 85.0,
    "depositAmount": 10000000
  },
  "contract": {
    "id": 101,
    "code": "HD-...",
    "startDate": "2026-01-01",
    "endDate": "2027-01-01",
    "daysLeft": 180,
    "status": "ACTIVE",
    "type": "WHOLE_HOUSE"
  },
  "building": {
    "propertyId": 26,
    "name": "Nhà Lê Lợi 01",
    "address": "..."
  }
}
```

| `contract.type` | `room.id` | Ý nghĩa |
|-----------------|-----------|---------|
| `ROOM` | số (vd. `12`) | Thuê theo phòng |
| `WHOLE_HOUSE` | `null` | Thuê nguyên căn — `room.roomNumber` = tên property |

**Rule FE:** dùng `contract.type` (không chỉ suy từ `room.id`), khớp list contract API cũ.

---

## 2. Tạo request bảo trì

```http
POST /api/v1/maintenance
Authorization: Bearer {tenantToken}
Content-Type: application/json
```

### 2.1 HĐ theo phòng (`type === "ROOM"`)

```json
{
  "roomId": 12,
  "equipmentId": null,
  "title": "Tường thấm nước",
  "description": "Góc tường phòng bị thấm.",
  "images": ["https://.../before.jpg"]
}
```

- Gửi `roomId` = `dashboard.room.id` (khuyến nghị).
- Nếu **không** gửi `roomId`, BE vẫn lấy phòng từ HĐ ACTIVE (miễn là khớp).
- `equipmentId` optional — phòng trống thiết bị vẫn tạo được.

### 2.2 HĐ nguyên căn (`type === "WHOLE_HOUSE"`) — **mới**

```json
{
  "roomId": null,
  "equipmentId": null,
  "title": "Điện chập chờn",
  "description": "Cả nhà bị chập điện tầng trệt.",
  "images": ["https://.../before.jpg"]
}
```

Hoặc **bỏ hẳn** field `roomId`:

```json
{
  "title": "Điện chập chờn",
  "description": "Cả nhà bị chập điện tầng trệt.",
  "images": ["https://.../before.jpg"]
}
```

BE lấy `property` từ HĐ ACTIVE; `room` trên ticket = `null`.

### 2.3 Response (nguyên căn)

```json
{
  "id": 55,
  "requestCode": "M-55",
  "title": "Điện chập chờn",
  "status": "PENDING",
  "roomId": null,
  "roomName": "Nhà Lê Lợi 01",
  "propertyId": 26,
  "propertyName": "Nhà Lê Lợi 01",
  "equipmentId": null,
  "beforeImages": ["https://.../before.jpg"],
  "...": "..."
}
```

`roomName` fallback = tên căn khi không có phòng — dùng cho list/detail.

---

## 3. Logic FE đề xuất (thay dead-end cũ)

```ts
const dash = await getTenantDashboard();
if (!dash?.contract) {
  alert("Không tìm thấy hợp đồng đang hiệu lực");
  return;
}

const type = dash.contract.type; // "ROOM" | "WHOLE_HOUSE"
const roomId =
  type === "ROOM"
    ? dash.room?.id ?? undefined
    : undefined; // nguyên căn: không gửi / null

if (type === "ROOM" && roomId == null) {
  alert("Không xác định được phòng của bạn. Vui lòng liên hệ quản lý vận hành");
  return;
}

// BỎ: alert "nguyên căn chưa hỗ trợ"

await createMaintenance({
  roomId,                    // ROOM: số; WHOLE_HOUSE: undefined/null
  equipmentId: selectedEq?.id ?? undefined,
  title,
  description,
  images,
});
```

### Alert map (cập nhật)

| Điều kiện | Message |
|-----------|---------|
| Không có `dash.contract` | Không tìm thấy hợp đồng đang hiệu lực |
| `type === "ROOM"` và không có `room.id` | Không xác định được phòng… liên hệ QL |
| `type === "WHOLE_HOUSE"` | **Cho tạo** — không chặn |
| List `equipments === []` | **Cho tạo** — không gắn `equipmentId` |

---

## 4. Validation / lỗi BE cần biết

| Situation | HTTP / message |
|-----------|----------------|
| Không HĐ ACTIVE | 404 — `Không tìm thấy hợp đồng đang hiệu lực` |
| ROOM + `roomId` khác phòng trên HĐ | Business — `Phòng không khớp hợp đồng đang hiệu lực` |
| WHOLE_HOUSE + `roomId` không thuộc property | Business — `Phòng không thuộc căn nhà đang thuê` |
| Thiếu title / description / images | Business — như cũ |
| Phòng không tồn tại (khi gửi roomId lạ) | 404 — `Phòng không tồn tại` |

---

## 5. Checklist FE

- [ ] Đọc `contract.type` từ dashboard (TypeScript type cập nhật).
- [ ] `WHOLE_HOUSE`: bỏ chặn “chưa hỗ trợ”; `POST` không bắt buộc `roomId`.
- [ ] `ROOM`: vẫn ưu tiên `dashboard.room.id`; không lấy `roomId` từ `equipments[0]`.
- [ ] Phòng/nhà **không có thiết bị** vẫn mở form (`equipmentId` optional).
- [ ] List/detail: khi `roomId == null`, hiển thị `roomName` / `propertyName`.
- [ ] Regression: tenant ROOM + có thiết bị / không thiết bị vẫn tạo OK.

---

## 6. Liên quan

- Debug bug alert cũ: [`FE-maintenance-empty-room-debug.md`](./FE-maintenance-empty-room-debug.md)
- Guide tổng: [`FE-MAINTENANCE-GUIDE.md`](./FE-MAINTENANCE-GUIDE.md) (§4.1, §7.2) — `roomId` với nguyên căn xem doc này.

---

## 7. Smoke test nhanh

1. Login tenant nguyên căn demo (`contract.type === "WHOLE_HOUSE"`, `room.id === null`).
2. `GET /tenant/me/equipments` có thể `[]`.
3. `POST /maintenance` **không** gửi `roomId` → success `PENDING`.
4. `GET /maintenance/my-requests` → item có `roomId: null`, `roomName` = tên căn.
5. Login tenant ROOM → gửi `roomId` từ dashboard → success như cũ.
