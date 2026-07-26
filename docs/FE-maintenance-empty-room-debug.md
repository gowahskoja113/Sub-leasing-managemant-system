# FE Debug — Không tạo được request bảo trì khi phòng không có nội thất

## Kết luận nhanh

| Layer | Có lỗi? | Ghi chú |
|-------|---------|---------|
| **FE** | **Có (khả năng cao)** | Alert chặn form trước khi gọi API |
| **BE** | Không (với HĐ theo phòng) | Không bắt buộc thiết bị; chỉ cần `roomId` |

Alert quan sát được:

> **Không xác định được phòng của bạn. Vui lòng kiểm tra hợp đồng đang hiệu lực.**

Chuỗi này **không tồn tại trong backend**. BE không trả message này. Đây là copy FE (`alert` / dialog client).

---

## Hiện tượng

- Tenant vào form tạo yêu cầu sửa chữa / bảo trì.
- Phòng / nhà **không có nội thất / thiết bị** (list equipment rỗng).
- FE hiện alert trên, không gửi được `POST /api/v1/maintenance`.

Dễ nhầm: nghĩ BE “không tìm thấy phòng vì không có đồ”. Thực tế FE **không resolve được `roomId`**.

---

## Nguyên nhân FE hay gặp

### Sai: lấy `roomId` từ thiết bị

```text
GET /tenant/me/equipments  →  []
→ roomId = equipments[0]?.roomId  →  undefined
→ alert "Không xác định được phòng..."
```

Phòng trống thiết bị → luôn fail, dù hợp đồng ACTIVE và dashboard có `room.id`.

### Đúng: lấy `roomId` từ dashboard / hợp đồng

```text
GET /api/v1/tenant/me/dashboard
→ roomId = response.room.id
→ POST /api/v1/maintenance { roomId, title, description, images, equipmentId? }
```

`equipmentId` **optional**. Không có thiết bị vẫn tạo request được (báo hỏng tường, điện, nước, cửa…).

---

## Checklist debug FE (làm theo thứ tự)

### 1. Xác nhận alert là client-side

- Mở DevTools → Network.
- Reproduce lỗi.
- Nếu **không có** request `POST /api/v1/maintenance` → chắc chắn FE chặn trước.
- Search codebase FE: `"Không xác định được phòng"` / `"xác định được phòng"`.

### 2. Gọi dashboard với token tenant đang test

```http
GET /api/v1/tenant/me/dashboard
Authorization: Bearer {tenantToken}
```

| Kết quả `room.id` | Ý nghĩa |
|-------------------|---------|
| Có số (vd. `12`) | BE OK. FE phải dùng giá trị này. Bug nằm ở FE resolve `roomId`. |
| `null` | HĐ **nguyên căn** (`TenantContract.room == null`). Xem mục “Case nguyên căn” bên dưới. |
| Không có `room` / dashboard trống | Không có HĐ ACTIVE → đúng khi báo kiểm tra hợp đồng. |

Ví dụ HĐ theo phòng (OK):

```json
{
  "room": {
    "id": 12,
    "roomNumber": "P01",
    "floor": 2
  },
  "building": {
    "propertyId": 26,
    "name": "..."
  }
}
```

### 3. Gọi list thiết bị (không dùng để lấy roomId)

```http
GET /api/v1/tenant/me/equipments
Authorization: Bearer {tenantToken}
```

| Kết quả | Ý nghĩa |
|---------|---------|
| `[]` | Bình thường nếu phòng trống nội thất. **Không** phải lỗi phòng. |
| Có items | Có thể pre-fill `equipmentId`, nhưng vẫn lấy `roomId` từ dashboard. |

### 4. Thử gọi create trực tiếp (bypass FE)

```http
POST /api/v1/maintenance
Authorization: Bearer {tenantToken}
Content-Type: application/json

{
  "roomId": 12,
  "title": "Test tường thấm nước",
  "description": "Góc tường bị thấm, không gắn thiết bị.",
  "images": ["https://example.com/before.jpg"]
}
```

- **Không** gửi `equipmentId`.
- Nếu BE trả `201` / success → xác nhận BE không phụ thuộc nội thất.
- Message BE nếu sai `roomId`: `"Phòng không tồn tại"` (khác hẳn alert FE).

---

## Fix FE đề xuất

1. **Source of truth cho `roomId`:** chỉ từ `GET /tenant/me/dashboard` → `room.id` (hoặc API contract ACTIVE tương đương), **không** từ `equipments[0]`.
2. **Cho phép form khi không có thiết bị:**
   - Ẩn / để trống dropdown thiết bị.
   - Không set `equipmentId` (hoặc `null`).
   - Vẫn require: `title`, `description`, `images` (BEFORE).
3. **Chỉ chặn khi thật sự thiếu phòng:**
   - Không có HĐ ACTIVE, hoặc
   - `room.id == null` **và** chưa hỗ trợ nguyên căn (xem dưới).
4. Đổi copy alert cho đúng nguyên nhân:
   - Thiếu HĐ → mới nói “kiểm tra hợp đồng đang hiệu lực”.
   - Có HĐ + thiếu `room.id` (nguyên căn) → message riêng, không nhầm với “không có thiết bị”.

Pseudo-code:

```ts
const dashboard = await getTenantDashboard();
const roomId = dashboard?.room?.id;

if (roomId == null) {
  // Không dùng: "không có thiết bị"
  // Phân nhánh: no contract vs whole-house vs missing room
  showError(/* message đúng case */);
  return;
}

await createMaintenance({
  roomId,
  equipmentId: selectedEquipment?.id ?? undefined, // optional
  title,
  description,
  images,
});
```

---

## Case nguyên căn

**Đã hỗ trợ BE (2026-07-26).** Xem handoff: [`FE-maintenance-wholehouse-handoff.md`](./FE-maintenance-wholehouse-handoff.md).

| Case | FE hành vi đúng |
|------|-----------------|
| HĐ phòng + có `room.id` + không thiết bị | Cho tạo request, `equipmentId` optional |
| HĐ nguyên căn + `contract.type === "WHOLE_HOUSE"` | Cho tạo request **không cần** `roomId` |

Đừng gộp hai case thành một alert “kiểm tra hợp đồng”.

---

## API liên quan (tóm tắt)

| Method | Endpoint | Dùng để |
|--------|----------|---------|
| `GET` | `/api/v1/tenant/me/dashboard` | **Lấy `roomId`** |
| `GET` | `/api/v1/tenant/me/equipments` | List thiết bị (optional UI) |
| `GET` | `/api/v1/equipments/by-qr/{qrCode}` | Pre-fill từ QR |
| `POST` | `/api/v1/maintenance` | Tạo request — `roomId` bắt buộc, `equipmentId` optional |

Body create:

```json
{
  "roomId": 12,
  "equipmentId": null,
  "title": "...",
  "description": "...",
  "images": ["https://..."]
}
```

Chi tiết flow đầy đủ: [`FE-MAINTENANCE-GUIDE.md`](./FE-MAINTENANCE-GUIDE.md) (§4.1, §7.2).

---

## Acceptance criteria sau khi fix FE

- [ ] Phòng HĐ ACTIVE, **không có** thiết bị → vẫn mở form và tạo request thành công.
- [ ] Network có `POST /maintenance` với `roomId` từ dashboard, không có / `null` `equipmentId`.
- [ ] Không còn alert “Không xác định được phòng…” chỉ vì `equipments === []`.
- [ ] Alert “kiểm tra hợp đồng” chỉ hiện khi thật sự không có HĐ ACTIVE.
- [ ] (Optional) Nguyên căn: message riêng hoặc disable có giải thích, không silent fail như thiếu phòng.
