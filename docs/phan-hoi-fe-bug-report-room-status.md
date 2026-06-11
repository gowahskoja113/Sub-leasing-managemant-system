# Phản hồi Backend: Bug Report "Thiếu API Cập Nhật Trạng Thái Phòng (500)"

> Phản hồi cho file `backend_bug_report.md` từ team Frontend (tháng 06/2026).

---

## Kết luận ngắn

**Báo cáo FE đúng về triệu chứng (500), nhưng nguyên nhân không phải "BE chưa viết code" trong repo hiện tại.**

| Điểm | Chi tiết |
|------|----------|
| Payload FE | ✅ Đúng — `{ "status": "AVAILABLE" }` |
| Endpoint FE gọi | ✅ Đúng path — `PATCH /api/v1/properties/{propertyId}/rooms/{roomId}/status` |
| Code trong repo `slms2026` | ✅ **Đã implement đầy đủ** |
| Server FE đang chạy | ❌ Đang trỏ tới bản backend **cũ / repo khác** chưa có endpoint này |

---

## 1. Vì sao FE kiểm tra source thấy "chưa có API"?

Trong bug report, FE đã mở source tại:

```
C:\sep490\Sub-leasing-managemant-system
```

Trong khi code đã được bổ sung tại workspace:

```
f:\Capstone\slms2026
```

Đây là **hai bản copy khác nhau** của project. Bản FE đang inspect **chưa được sync** các thay đổi sau:

| File | Trạng thái trên `slms2026` |
|------|---------------------------|
| `dto/request/UpdateRoomStatusRequest.java` | Đã tạo (file mới) |
| `controller/RoomController.java` | Đã thêm `@PatchMapping("/{roomId}/status")` |
| `service/RoomService.java` | Đã thêm `updateRoomStatus(...)` |
| `service/impl/RoomServiceImpl.java` | Đã implement logic nghiệp vụ |

→ FE nhìn đúng file nhưng **sai repo / sai branch / chưa pull** → kết luận "chưa implement" là hợp lý với bản họ đang mở, nhưng **không phản ánh** bản backend mới nhất.

---

## 2. Vì sao lỗi là 500 thay vì 404?

Khi gọi endpoint **chưa tồn tại** trên server đang chạy:

- Spring Boot thường trả **404 Not Found**
- Một số setup (reverse proxy, API gateway, dev proxy của FE) có thể **wrap thành 500**
- Hoặc server đang chạy **JAR/class cũ** build trước khi có `PATCH .../status`

**500 ở đây = server FE đang gọi chưa có handler cho route này**, không phải lỗi logic bên trong `updateRoomStatus`.

Sau khi chạy đúng bản backend mới, response sẽ là:

| Tình huống | HTTP | Ví dụ `error` |
|------------|------|---------------|
| Thành công | **200** | — |
| Phòng đang thuê | **422** | `Phòng P101 đang cho thuê — không thể đổi trạng thái` |
| Tòa nhà chưa ACTIVE | **422** | `Chỉ cập nhật trạng thái phòng khi tòa nhà đang ACTIVE` |
| Phòng chưa kích hoạt (DRAFT) | **422** | `Phòng P101 chưa kích hoạt — không thể cập nhật trạng thái` |
| Phòng không tồn tại | **404** | `Không tìm thấy phòng ID=...` |
| JSON sai | **400** | Validation failed |

---

## 3. API đã implement — contract cho FE

### Request

```http
PATCH /api/v1/properties/{propertyId}/rooms/{roomId}/status
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "status": "AVAILABLE"
}
```

Giá trị `status` hợp lệ: **`AVAILABLE`** | **`MAINTENANCE`** (enum `RoomStatus`).

### Response thành công (200)

```json
{
  "id": 5,
  "propertyId": 1,
  "propertyName": "Nhà ABC",
  "roomNumber": "P101",
  "price": 3500000,
  "status": "AVAILABLE",
  ...
}
```

### Quy tắc nghiệp vụ (đã code, chi tiết hơn bug report)

1. Tòa nhà phải **`ACTIVE`** (không chỉ "không DRAFT")
2. Chỉ áp dụng nhà **chia phòng** (`wholeHouse = false`)
3. Chặn khi phòng **`RENTED`**
4. Chặn khi phòng **`DRAFT`** (chưa qua bước kích hoạt onboarding)
5. Chỉ cho đổi sang **`AVAILABLE`** hoặc **`MAINTENANCE`**

---

## 4. Việc cần làm để FE test được

### Backend

1. **Dùng đúng repo** `slms2026` (hoặc branch đã merge code mới)
2. **Pull / sync** các file liên quan (xem mục 1)
3. **Rebuild & restart** server:
   ```bash
   mvn clean compile
   mvn spring-boot:run
   ```
4. Xác nhận endpoint tồn tại qua Swagger: `/swagger-ui.html` → tìm `RoomController` → `PATCH .../status`

### Frontend

1. Kiểm tra `baseURL` / `.env` trỏ đúng server vừa restart (không trỏ instance cũ)
2. Giữ nguyên payload hiện tại — **đã đúng**
3. Sau khi BE deploy: kỳ vọng **200** hoặc **422**, không còn **500** do thiếu route

### Cách verify nhanh (curl / Postman)

```bash
curl -X PATCH "http://localhost:8080/api/v1/properties/1/rooms/5/status" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d "{\"status\": \"AVAILABLE\"}"
```

- Nếu vẫn **500** + không có log `Đã cập nhật trạng thái phòng` → vẫn đang chạy bản cũ
- Nếu **404** route → server chưa load `RoomController` mới
- Nếu **422** → API đã chạy, kiểm tra điều kiện nghiệp vụ (property ACTIVE, room không RENTED/DRAFT)

---

## 5. So sánh code FE đề xuất vs code BE thực tế

FE đề xuất trong bug report đã đúng hướng. BE đã implement **đầy đủ hơn**:

| FE đề xuất | BE thực tế |
|------------|------------|
| Chặn `RENTED` | ✅ Có |
| Chặn property chưa hoạt động | ✅ Kiểm tra `property.status == ACTIVE` (chặt hơn "không DRAFT") |
| — | ✅ Chặn nhà nguyên căn (`wholeHouse = true`) |
| — | ✅ Chặn phòng `DRAFT` |
| — | ✅ Chỉ cho `AVAILABLE` / `MAINTENANCE` trong request |
| — | ✅ `@Valid` + `@NotNull` trên `UpdateRoomStatusRequest` |

**Không cần FE sửa integration** — chỉ cần BE deploy đúng bản.

---

## 6. Checklist đồng bộ repo (cho cả 2 team)

- [ ] Xác nhận FE và BE cùng trỏ **một remote Git** (không dùng 2 folder local khác nhau)
- [ ] BE commit & push các file: `UpdateRoomStatusRequest`, `RoomController`, `RoomService`, `RoomServiceImpl`, `RoomRepository`
- [ ] FE pull branch mới nhất
- [ ] BE restart sau khi build
- [ ] FE test lại `PATCH .../status` — kỳ vọng 200/422, không còn 500

---

## 7. Liên hệ tài liệu

- Đối chiếu tổng thể: [doi-chieu-ke-hoach-fe-activation-room-status.md](./doi-chieu-ke-hoach-fe-activation-room-status.md)
- Hướng dẫn API đầy đủ: [fe-fix-activation-manager-room-status.md](./fe-fix-activation-manager-room-status.md)

---

## Tóm lại cho team FE

> API **đã có** trong backend `slms2026`. Lỗi 500 xảy ra vì server đang chạy **bản cũ** (repo/path `C:\sep490\Sub-leasing-managemant-system` chưa có patch).  
> Payload và endpoint FE gọi **đều đúng**. Sau khi BE sync code + restart, tính năng sẽ hoạt động mà **không cần sửa thêm phía FE**.
