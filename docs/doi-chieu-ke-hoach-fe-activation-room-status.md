# Đối chiếu Kế hoạch FE & Trạng thái Backend hiện tại

> Tài liệu đối chiếu file `implementation_plan (1).md` với code Backend SLMS (tháng 06/2026).  
> Kết luận: **Backend đã triển khai đủ các API cần thiết — không cần sửa thêm.** Phần còn lại là tích hợp đúng contract phía Frontend.

Chi tiết API đầy đủ: [fe-fix-activation-manager-room-status.md](./fe-fix-activation-manager-room-status.md)

**Cập nhật (feedback FE):** Nếu gặp lỗi 500 khi gọi `PATCH .../rooms/{roomId}/status`, xem [phan-hoi-fe-bug-report-room-status.md](./phan-hoi-fe-bug-report-room-status.md) — nguyên nhân thường là FE đang chạy backend cũ chưa sync code.

---

## Tóm tắt nhanh

| # | Vấn đề trong kế hoạch | Backend hiện tại | Cần sửa BE? | Việc FE cần làm |
|---|------------------------|-----------------|-------------|-----------------|
| 1 | Duyệt giá & kích hoạt không được; gán OM bị bỏ qua | Đã có `operationManagerId` trong `host-confirm`; kích hoạt `ACTIVE` khi gửi kèm OM | **Không** | Gửi đúng UUID; đọc `propertyStatus` từ response |
| 2 | Đổi quản lý tòa nhà | Đã có `PUT /operation-manager` | **Không** | Gọi `PUT` (không dùng PATCH/POST) |
| 3 | Cập nhật trạng thái phòng trống/bảo trì | Đã có `PATCH .../rooms/{roomId}/status`; chặn `RENTED` | **Không** | Gọi API mới; xử lý lỗi 422 |

---

## 1. Duyệt giá & kích hoạt tòa nhà + Gán Operation Manager

### Kế hoạch gốc nói gì?

- `HostConfirmRequest` thiếu `operationManagerId` → BE ignore field từ FE.
- `hostConfirm` chỉ set `PENDING_OPERATION_MANAGER`, không `ACTIVE`.
- Đề xuất: thêm field `Long operationManagerId` và luôn set `ACTIVE` ngay khi host duyệt.

### Backend hiện tại (đã có)

**DTO `HostConfirmRequest`** — đã có field tùy chọn:

```java
@JsonAlias({"id", "managerId"})
private UUID operationManagerId;  // KHÔNG phải Long
```

**Logic `hostConfirm`** (`PropertyOnboardingServiceImpl`):

1. Validate nhà ở `PENDING_HOST_REVIEW`, đã có depreciation, v.v.
2. Lưu giá host xác nhận → set tạm `PENDING_OPERATION_MANAGER`.
3. **Nếu body có `operationManagerId`** → gọi `finalizeActivation()`:
   - Gán `operationManagerId` + `managedBy`
   - Set property `ACTIVE`
   - Nhà chia phòng: chuyển phòng `DRAFT` → `AVAILABLE`
4. **Nếu không gửi `operationManagerId`** → trả về với `propertyStatus = PENDING_OPERATION_MANAGER` (chờ bước gán OM riêng).

### Khác biệt so với kế hoạch

| Kế hoạch | Thực tế BE |
|----------|------------|
| `operationManagerId` kiểu `Long` | Kiểu **`UUID`** (string trong JSON) |
| Luôn `ACTIVE` sau host-confirm | `ACTIVE` **chỉ khi** gửi kèm `operationManagerId`; nếu không → `PENDING_OPERATION_MANAGER` |
| Một luồng duy nhất | Hỗ trợ **2 luồng** (1 bước hoặc 2 bước) |

### Nguyên nhân FE vẫn thấy lỗi (dù BE đã sẵn sàng)

1. **Gửi sai kiểu ID** — gửi number thay vì UUID string → HTTP 400.
2. **Gửi sai tên field** — BE chấp nhận: `operationManagerId`, `id`, `managerId` (trong `host-confirm`).
3. **Không gửi `operationManagerId`** nhưng UI hiển thị "Đã kích hoạt" — DB vẫn `PENDING_OPERATION_MANAGER`.
4. **Đọc sai response** — phải kiểm tra `propertyStatus === "ACTIVE"`, không tự assume thành công.
5. **Chọn OM không hợp lệ** — user phải có `role = ROLE_MANAGER` và `status = ACTIVE` (lấy từ `GET /api/v1/user/managers`).

### API & ví dụ request

```http
POST /api/v1/properties/{propertyId}/host-confirm
Authorization: Bearer <token>
Content-Type: application/json
```

**Nhà nguyên căn (kích hoạt 1 bước):**
```json
{
  "contingencyPercent": 110,
  "propertyPrice": 15000000,
  "operationManagerId": "550e8400-e29b-41d4-a716-446655440042"
}
```

**Nhà chia phòng (kích hoạt 1 bước):**
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

**Response thành công (1 bước):**
```json
{
  "propertyId": 1,
  "pricingScope": "ROOM",
  "propertyStatus": "ACTIVE",
  "hostContingencyPercent": 110,
  "operationManagerId": "550e8400-e29b-41d4-a716-446655440042",
  "rooms": [
    { "roomId": 5, "roomNumber": "P101", "price": 3500000, "status": "AVAILABLE" }
  ]
}
```

### Luồng 2 bước (nếu UI tách chọn OM)

```
Bước 1: POST host-confirm (KHÔNG có operationManagerId)
        → propertyStatus = "PENDING_OPERATION_MANAGER"

Bước 2: POST hoặc PATCH /api/v1/properties/{id}/operation-manager
        → propertyStatus = "ACTIVE"
```

```json
{ "operationManagerId": "550e8400-e29b-41d4-a716-446655440042" }
```

Alias body bước 2: `operationManagerId`, `id`, `managerId`, `userId`.

### Lấy danh sách Operation Manager

```http
GET /api/v1/user/managers
```

Map `UserResponse.id` (UUID) → `operationManagerId` trong body gửi lên.

---

## 2. Đổi quản lý tòa nhà

### Kế hoạch gốc nói gì?

> BE chưa có API, tạm bỏ qua. Gợi ý sau này: `PATCH /api/v1/properties/{id}/manager`.

### Backend hiện tại (đã có — không cần chờ)

```http
PUT /api/v1/properties/{propertyId}/operation-manager
```

```json
{
  "operationManagerId": "660e8400-e29b-41d4-a716-446655440099"
}
```

**Điều kiện:**
- Property phải đang `ACTIVE`
- User mới phải là `ROLE_MANAGER` + `ACTIVE`

**Response:** `PropertyResponse` (có `operationManagerId` mới).

### Lưu ý phân biệt endpoint

| Method | Mục đích | Property status trước |
|--------|----------|------------------------|
| `POST` / `PATCH` `/operation-manager` | **Gán OM lần đầu** | `PENDING_OPERATION_MANAGER` |
| `PUT` `/operation-manager` | **Đổi OM** | `ACTIVE` |

FE **không** dùng `PUT` khi nhà chưa `ACTIVE`.  
Endpoint trong kế hoạch (`PATCH .../manager`) **không tồn tại** — dùng path trên.

---

## 3. Cập nhật trạng thái phòng (trống / bảo trì)

### Kế hoạch gốc nói gì?

- Chưa có API cập nhật trạng thái phòng.
- Cần tạo `UpdateRoomStatusRequest` + chặn khi `RENTED`.

### Backend hiện tại (đã có)

```http
PATCH /api/v1/properties/{propertyId}/rooms/{roomId}/status
```

```json
{ "status": "MAINTENANCE" }
```
hoặc
```json
{ "status": "AVAILABLE" }
```

**Quy tắc nghiệp vụ:**

| Trạng thái phòng hiện tại | Property `DRAFT` | Property `ACTIVE` |
|---------------------------|------------------|-------------------|
| `AVAILABLE` (trống) | ✅ → `MAINTENANCE` / `AVAILABLE` | ✅ |
| `MAINTENANCE` | ✅ → `AVAILABLE` / `MAINTENANCE` | ✅ |
| `DRAFT` | ✅ → `MAINTENANCE` / `AVAILABLE` | ❌ HTTP 422 |
| `RENTED` | ❌ HTTP 422 | ❌ HTTP 422 |

**Điều kiện thêm:**
- Tòa nhà phải `DRAFT` **hoặc** `ACTIVE` (không bắt buộc ACTIVE khi nhà còn nháp)
- Chỉ nhà chia phòng (`wholeHouse = false`)

**Response:** `RoomResponse` với `status` mới.

**Lỗi khi phòng đang thuê:**
```json
{
  "timestamp": "2026-06-11T10:00:00",
  "status": 422,
  "error": "Phòng P101 đang cho thuê — không thể đổi trạng thái"
}
```

FE: disable dropdown / hiện toast khi `room.status === "RENTED"`.

---

## 4. State machine tổng hợp (cho FE)

```
DRAFT
  └─ submit-to-host ──► PENDING_HOST_REVIEW
                            └─ host-confirm ──► PENDING_OPERATION_MANAGER
                                                      └─ POST/PATCH operation-manager ──► ACTIVE
                            └─ host-confirm (+ operationManagerId) ──────────────────► ACTIVE

ACTIVE
  └─ PUT operation-manager     → đổi OM (vẫn ACTIVE)
  └─ PATCH rooms/{id}/status   → AVAILABLE ↔ MAINTENANCE
```

---

## 5. Bảng API tổng hợp

| Method | Endpoint | Khi nào dùng |
|--------|----------|--------------|
| `POST` | `/api/v1/properties/{id}/host-confirm` | Host duyệt giá |
| `POST` / `PATCH` | `/api/v1/properties/{id}/operation-manager` | Gán OM lần đầu |
| `PUT` | `/api/v1/properties/{id}/operation-manager` | Đổi OM (nhà ACTIVE) |
| `GET` | `/api/v1/user/managers` | Load dropdown OM |
| `PATCH` | `/api/v1/properties/{id}/rooms/{roomId}/status` | Trống / bảo trì |
| `GET` | `/api/v1/properties/{id}/rooms` | Refresh danh sách phòng sau khi đổi status |

---

## 6. Mã lỗi & cách FE xử lý

| HTTP | Nguồn | Message mẫu | Hành động FE |
|------|-------|-------------|--------------|
| 400 | JSON sai kiểu | `operationManagerId phải là UUID` | Gửi UUID string từ `GET /user/managers` |
| 422 | BusinessException | `Chỉ có thể xác nhận khi nhà ở trạng thái PENDING_HOST_REVIEW` | Kiểm tra luồng onboarding |
| 422 | BusinessException | `Chỉ có thể gán Operation Manager khi nhà ở trạng thái PENDING_OPERATION_MANAGER` | Nhà đã ACTIVE hoặc chưa host-confirm |
| 422 | BusinessException | `Phòng ... đang cho thuê — không thể đổi trạng thái` | Disable UI, hiện thông báo |
| 404 | Not found | `Không tìm thấy Operation Manager ID=...` | Chọn lại từ danh sách managers |

Body lỗi chuẩn (`ErrorResponse`):
```json
{
  "timestamp": "...",
  "status": 422,
  "error": "Nội dung lỗi tiếng Việt"
}
```

Hiển thị `error` cho user.

---

## 7. Checklist tích hợp Frontend

- [ ] `host-confirm`: gửi `operationManagerId` dạng **UUID string** nếu UI chọn OM trong cùng màn hình
- [ ] Sau `host-confirm`, kiểm tra `response.propertyStatus === "ACTIVE"` mới coi là kích hoạt thành công
- [ ] Nếu `propertyStatus === "PENDING_OPERATION_MANAGER"`, gọi tiếp `POST`/`PATCH` operation-manager
- [ ] Load OM từ `GET /api/v1/user/managers`, map `id` → `operationManagerId`
- [ ] Đổi OM trên nhà ACTIVE: dùng **`PUT`** `/operation-manager`
- [ ] Trạng thái phòng: `PATCH .../rooms/{roomId}/status` với `"AVAILABLE"` | `"MAINTENANCE"`
- [ ] Disable đổi trạng thái khi `room.status === "RENTED"`
- [ ] Ẩn/disable quản lý trạng thái từng phòng trên nhà nguyên căn
- [ ] Bắt và hiển thị lỗi HTTP 422 từ field `error`

---

## 8. File Backend liên quan (đã triển khai)

| File | Nội dung |
|------|----------|
| `dto/request/HostConfirmRequest.java` | `operationManagerId` (UUID, optional) + JsonAlias |
| `dto/request/AssignOperationManagerRequest.java` | Gán/đổi OM |
| `dto/request/UpdateRoomStatusRequest.java` | `status`: AVAILABLE \| MAINTENANCE |
| `service/impl/PropertyOnboardingServiceImpl.java` | `hostConfirm`, `assignOperationManager`, `changeOperationManager`, `finalizeActivation` |
| `controller/PropertyOnboardingController.java` | host-confirm, POST/PATCH/PUT operation-manager |
| `service/impl/RoomServiceImpl.java` | `updateRoomStatus` — chặn RENTED, DRAFT |
| `controller/RoomController.java` | `PATCH /{roomId}/status` |
| `controller/UserController.java` | `GET /user/managers` |

---

## 9. Kết luận

**Backend không cần sửa thêm** cho 3 vấn đề trong kế hoạch — đã implement đầy đủ và compile thành công.

Triệu chứng FE mô tả (kích hoạt không được, OM không lưu, không đổi được trạng thái phòng) nhiều khả năng do:

1. Chưa gửi `operationManagerId` trong `host-confirm` (hoặc gửi sai kiểu).
2. UI assume `ACTIVE` mà không đọc `propertyStatus` từ API response.
3. Chưa gọi `PATCH .../rooms/{roomId}/status` — vẫn chỉ dùng GET/POST rooms.
4. Nhầm endpoint khi đổi OM (`PUT` vs `POST`/`PATCH`).

Nếu sau khi tích hợp đúng checklist mà vẫn lỗi, gửi kèm: **request body**, **response body**, **propertyId** để debug tiếp.
