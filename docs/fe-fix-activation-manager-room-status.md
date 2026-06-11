# SLMS — Hướng dẫn tích hợp FE: Duyệt giá, Kích hoạt, Gán/Đổi OM, Trạng thái phòng

Tài liệu mô tả các API đã sửa/bổ sung để xử lý 4 vấn đề FE báo lại (tháng 06/2026).

---

## Tóm tắt thay đổi Backend

| Vấn đề FE | Nguyên nhân | Giải pháp BE |
|-----------|-------------|--------------|
| Duyệt giá & kích hoạt không được | FE gửi `operationManagerId` trong `host-confirm` nhưng BE bỏ qua → nhà kẹt `PENDING_OPERATION_MANAGER` | `host-confirm` nhận `operationManagerId` (tuỳ chọn) và kích hoạt ngay nếu có |
| Gán quản lý chưa được | FE có thể gọi `POST` thay vì `PATCH`, hoặc gửi field `id`/`managerId` | Thêm `POST` alias; hỗ trợ `@JsonAlias` |
| Đổi quản lý | Chưa có API | Thêm `PUT /operation-manager` khi nhà `ACTIVE` |
| Cập nhật trạng thái phòng | Chưa có API | Thêm `PATCH .../rooms/{roomId}/status`; chặn khi `RENTED` |

---

## 1. Luồng kích hoạt tòa nhà (Property)

### State machine (cập nhật)

```
DRAFT
  → submit-to-host → PENDING_HOST_REVIEW (hoặc UNDER_RENOVATION nếu đang cải tạo)
  → host-confirm   → PENDING_OPERATION_MANAGER
                     hoặc ACTIVE (nếu gửi kèm operationManagerId)
  → assign OM      → ACTIVE (bước riêng, khi chưa gửi OM trong host-confirm)
```

### Hai cách kích hoạt (FE chọn 1)

#### Cách A — Một bước (khuyến nghị, khớp UI hiện tại)

Host duyệt giá **và** chọn Operation Manager trong cùng form → gọi `host-confirm` kèm `operationManagerId`.

```http
POST /api/v1/properties/{propertyId}/host-confirm
Authorization: Bearer <token>
Content-Type: application/json
```

**Nhà nguyên căn:**
```json
{
  "contingencyPercent": 110,
  "propertyPrice": 15000000,
  "operationManagerId": "550e8400-e29b-41d4-a716-446655440042"
}
```

**Nhà chia phòng:**
```json
{
  "contingencyPercent": 110,
  "roomPrices": [
    { "roomId": 5, "price": 3500000 },
    { "roomId": 6, "price": 2800000 }
  ],
  "operationManagerId": "550e8400-e29b-41d4-a716-446655440042"
}
```

**Response khi thành công:**
- `propertyStatus`: `"ACTIVE"`
- `operationManagerId`: UUID đã gán
- Nhà chia phòng: `rooms[].status` = `"AVAILABLE"`

**Alias field được chấp nhận cho OM:** `operationManagerId`, `id`, `managerId`

#### Cách B — Hai bước

1. `host-confirm` **không** gửi `operationManagerId` → `propertyStatus` = `PENDING_OPERATION_MANAGER`
2. Gán OM riêng (xem mục 2) → `ACTIVE`

---

## 2. Gán Operation Manager (lần đầu)

Dùng khi nhà đang `PENDING_OPERATION_MANAGER` (sau host-confirm không kèm OM).

```http
PATCH /api/v1/properties/{propertyId}/operation-manager
```
hoặc
```http
POST /api/v1/properties/{propertyId}/operation-manager
```

```json
{
  "operationManagerId": "550e8400-e29b-41d4-a716-446655440042"
}
```

**Alias body:** `operationManagerId`, `id`, `managerId`, `userId`

**Response:** `PropertyActivationResponse`
```json
{
  "propertyId": 1,
  "pricingScope": "ROOM",
  "propertyStatus": "ACTIVE",
  "hostContingencyPercent": 110,
  "operationManagerId": "550e8400-e29b-41d4-a716-446655440042",
  "rooms": [
    {
      "roomId": 5,
      "roomNumber": "P101",
      "price": 3500000,
      "adminSuggestedPrice": 3200000,
      "status": "AVAILABLE"
    }
  ]
}
```

### Lấy danh sách OM

```http
GET /api/v1/user/managers
```

Dùng field `id` từ `UserResponse` làm `operationManagerId`.

---

## 3. Đổi Operation Manager (nhà đã ACTIVE)

**Mới** — dùng khi cần thay OM trên tòa nhà đang kinh doanh.

```http
PUT /api/v1/properties/{propertyId}/operation-manager
```

```json
{
  "operationManagerId": "660e8400-e29b-41d4-a716-446655440099"
}
```

**Điều kiện:**
- Property `status` phải là `ACTIVE`
- User phải có `role = ROLE_MANAGER` và `status = ACTIVE`

**Response:** `PropertyResponse` (có `operationManagerId` mới)

**Lỗi thường gặp:**

| HTTP | Message | Xử lý FE |
|------|---------|----------|
| 422 | `Chỉ có thể đổi Operation Manager khi nhà đang ở trạng thái ACTIVE` | Chỉ hiện nút "Đổi OM" khi `status === 'ACTIVE'` |
| 422 | `User ID=... không phải Operation Manager` | Chỉ chọn từ `GET /user/managers` |
| 404 | `Không tìm thấy Operation Manager ID=...` | Validate UUID trước khi gửi |

> **Lưu ý:** `PATCH`/`POST` operation-manager dùng cho **gán lần đầu** (`PENDING_OPERATION_MANAGER`). `PUT` dùng cho **đổi OM** (`ACTIVE`). Không dùng `PUT` khi nhà chưa ACTIVE.

---

## 4. Cập nhật trạng thái phòng (trống / bảo trì)

**Mới** — chỉ áp dụng nhà chia phòng (`wholeHouse = false`), tòa nhà `ACTIVE`.

```http
PATCH /api/v1/properties/{propertyId}/rooms/{roomId}/status
```

```json
{
  "status": "MAINTENANCE"
}
```

hoặc

```json
{
  "status": "AVAILABLE"
}
```

### Quy tắc nghiệp vụ

| Trạng thái hiện tại | Cho phép đổi? | Ghi chú |
|---------------------|---------------|---------|
| `AVAILABLE` | ✅ → `MAINTENANCE` hoặc `AVAILABLE` | Phòng trống |
| `MAINTENANCE` | ✅ → `AVAILABLE` hoặc `MAINTENANCE` | Đang bảo trì |
| `RENTED` | ❌ | **Không cho cập nhật** — hiển thị thông báo trên FE |
| `DRAFT` | ❌ | Phòng chưa kích hoạt onboarding |

**Giá trị `status` hợp lệ trong request:** chỉ `AVAILABLE` hoặc `MAINTENANCE`.

**Response:** `RoomResponse` với `status` mới.

### Gợi ý UI

- Dropdown trạng thái phòng: chỉ hiện `AVAILABLE` ("Trống") và `MAINTENANCE` ("Bảo trì")
- Disable dropdown + tooltip khi `room.status === 'RENTED'`: *"Phòng đang cho thuê, không thể đổi trạng thái"*
- Ẩn hoặc disable toàn bộ trên nhà nguyên căn

---

## 5. Bảng API tổng hợp

| Method | Endpoint | Khi nào dùng | Status trước | Status sau |
|--------|----------|--------------|--------------|------------|
| `POST` | `/properties/{id}/host-confirm` | Host duyệt giá | `PENDING_HOST_REVIEW` | `PENDING_OPERATION_MANAGER` hoặc `ACTIVE`* |
| `PATCH` / `POST` | `/properties/{id}/operation-manager` | Gán OM lần đầu | `PENDING_OPERATION_MANAGER` | `ACTIVE` |
| `PUT` | `/properties/{id}/operation-manager` | Đổi OM | `ACTIVE` | `ACTIVE` |
| `PATCH` | `/properties/{id}/rooms/{roomId}/status` | Trống / bảo trì | `ACTIVE` (property) | — |

\* `ACTIVE` nếu body có `operationManagerId`

---

## 6. Ví dụ luồng E2E (chia phòng)

```
1. POST /properties/draft
2. ... (onboarding wizard)
3. POST /properties/{id}/submit-to-host          → PENDING_HOST_REVIEW
4. POST /properties/{id}/host-confirm            → ACTIVE (kèm operationManagerId)
   HOẶC
   4a. POST /properties/{id}/host-confirm        → PENDING_OPERATION_MANAGER
   4b. POST /properties/{id}/operation-manager    → ACTIVE

5. (Sau khi vận hành) PUT /properties/{id}/operation-manager  → đổi OM

6. PATCH /properties/{id}/rooms/{roomId}/status   → MAINTENANCE / AVAILABLE
```

---

## 7. Mã lỗi & xử lý FE

| Message BE | HTTP | Hành động FE |
|------------|------|--------------|
| `Chỉ có thể xác nhận khi nhà ở trạng thái PENDING_HOST_REVIEW` | 422 | Chờ admin `submit-to-host` |
| `Chỉ có thể gán Operation Manager khi nhà ở trạng thái PENDING_OPERATION_MANAGER` | 422 | Nhà đã ACTIVE hoặc chưa host-confirm |
| `Phòng ... đang cho thuê — không thể đổi trạng thái` | 422 | Disable nút đổi trạng thái |
| `Chỉ cập nhật trạng thái phòng khi tòa nhà đang ACTIVE` | 422 | Chỉ cho phép sau kích hoạt |
| `JSON không hợp lệ — operationManagerId phải là UUID` | 400 | Gửi UUID string, không gửi number |

---

## 8. Checklist tích hợp Frontend

- [ ] `host-confirm`: gửi `operationManagerId` (hoặc `id`) nếu UI chọn OM trong cùng màn hình duyệt giá
- [ ] Kiểm tra `propertyStatus` trong response: `ACTIVE` = kích hoạt thành công
- [ ] Nếu tách 2 bước: sau `host-confirm` gọi `POST` hoặc `PATCH` operation-manager
- [ ] Đổi OM: dùng `PUT` (không dùng PATCH/POST)
- [ ] Load OM từ `GET /api/v1/user/managers`, map field `id` → `operationManagerId`
- [ ] Trạng thái phòng: `PATCH .../status` với `AVAILABLE` | `MAINTENANCE`
- [ ] Disable đổi trạng thái khi `room.status === 'RENTED'`
- [ ] Hiển thị lỗi 422 từ field `error` trong `ErrorResponse`

---

## 9. File Backend đã thay đổi

| File | Thay đổi |
|------|----------|
| `dto/request/HostConfirmRequest.java` | Thêm `operationManagerId` (optional) |
| `dto/request/AssignOperationManagerRequest.java` | Thêm JsonAlias `managerId`, `userId` |
| `dto/request/UpdateRoomStatusRequest.java` | **Mới** |
| `service/impl/PropertyOnboardingServiceImpl.java` | `finalizeActivation`, `changeOperationManager` |
| `service/PropertyOnboardingService.java` | Interface `changeOperationManager` |
| `controller/PropertyOnboardingController.java` | `POST` + `PUT` operation-manager |
| `service/RoomService.java` | `updateRoomStatus` |
| `service/impl/RoomServiceImpl.java` | Logic chặn `RENTED` |
| `controller/RoomController.java` | `PATCH /{roomId}/status` |
