# BE — Multipart upload & Public Guest API

**Ngày:** 20/06/2026  
**Phạm vi:** Backend (`slms2026`)  
**Liên quan FE:** import file Excel, trang guest (web + mobile)

---

## Tóm tắt

| # | Vấn đề | Giải pháp |
|---|--------|-----------|
| 1 | Upload file > 1MB fail (Spring mặc định 1MB) | Thêm `spring.servlet.multipart` 25MB/30MB |
| 2 | Trang guest gọi `/api/v1/properties` bị 401/403 khi chưa đăng nhập | API public GET-only `/api/v1/public/properties/**` |

---

## 1. Cấu hình upload multipart

### Vấn đề

- BE không có block `spring.servlet.multipart` → giới hạn mặc định **1MB**.
- File import nhiều căn (~1.2MB+) bị `MaxUploadSizeExceededException`.
- FE đã map message `"Maximum upload size exceeded"` sang tiếng Việt; cần BE chỉnh config.

### Thay đổi

**File:** `src/main/resources/application.yaml`

```yaml
spring:
  # ... datasource, jpa ...
  servlet:
    multipart:
      max-file-size: 25MB
      max-request-size: 30MB
```

| Key | Giá trị | Ghi chú |
|-----|---------|---------|
| `max-file-size` | 25MB | Một file trong request |
| `max-request-size` | 30MB | Tổng body (file + field) |

### Exception handler

**File:** `src/main/java/com/sep490/slms2026/exception/GlobalExceptionHandler.java`

- Thêm `@ExceptionHandler(MaxUploadSizeExceededException.class)`.
- Trả **HTTP 413** với body:

```json
{
  "status": 413,
  "error": "Payload Too Large",
  "message": "Maximum upload size exceeded"
}
```

### Triển khai

1. **Restart app** sau khi deploy config.
2. Nếu có **Nginx / API Gateway** phía trước, kiểm tra thêm `client_max_body_size` (hoặc tương đương).

---

## 2. API public cho trang guest

### Vấn đề

- `SecurityConfig` chỉ `permitAll` cho auth, PayOS webhook, Swagger.
- `GET /api/v1/properties`, `GET /api/v1/properties/{id}`, `GET .../rooms` yêu cầu JWT.
- Khách chưa đăng nhập → 401/403, danh sách nhà rỗng (FE catch lỗi, không crash).

### Security

**File:** `src/main/java/com/sep490/slms2026/config/SecurityConfig.java`

```java
.requestMatchers(HttpMethod.GET, "/api/v1/public/properties/**").permitAll()
```

Chỉ **GET** được public; POST/PUT/DELETE vẫn yêu cầu authenticated.

### Endpoint mới

**Controller:** `PublicPropertyController`  
**Base path:** `/api/v1/public/properties`

| Method | Path | Auth | Mô tả |
|--------|------|------|--------|
| GET | `/api/v1/public/properties` | Không | Danh sách nhà (paginated) |
| GET | `/api/v1/public/properties/{id}` | Không | Chi tiết nhà |
| GET | `/api/v1/public/properties/{id}/rooms` | Không | Phòng còn trống |

**Query params (list):**

| Param | Default | Mô tả |
|-------|---------|--------|
| `page` | `0` | Trang (0-based) |
| `size` | `10` | Số bản ghi/trang |

**Ví dụ:**

```http
GET /api/v1/public/properties?page=0&size=20
GET /api/v1/public/properties/5
GET /api/v1/public/properties/5/rooms
```

### Điều kiện nghiệp vụ

Property được trả về khi **đồng thời**:

- `status = ACTIVE`
- `operationManagerId IS NOT NULL`

Nếu không thỏa (hoặc không tồn tại) → **404** `"Không tìm thấy tài sản với ID: {id}"`.

**Phòng (rooms):** chỉ trả phòng có `status = AVAILABLE` (ẩn RENTED, DRAFT, MAINTENANCE).

**`rentalAvailable`:**

- Nhà nguyên căn: chưa có HĐ tenant active (`room = null`).
- Nhà chia phòng: còn ít nhất 1 phòng `AVAILABLE`.

### Response DTO — `GuestPropertyResponse`

**File:** `src/main/java/com/sep490/slms2026/dto/response/GuestPropertyResponse.java`

| Field | Type | Nguồn dữ liệu |
|-------|------|----------------|
| `id`, `propertyName`, `shortAddress`, `fullAddress`, … | — | Giống `PropertyResponse` cơ bản |
| `latitude`, `longitude` | `Double` | Cột mới trên `properties` (có thể null) |
| `amenities` | `List<String>` | Tên catalog thiết bị distinct của nhà |
| `electricityUnitPrice` | `BigDecimal` | Cột property, hoặc chỉ số điện mới nhất (whole-house) |
| `waterUnitPrice` | `BigDecimal` | Cột property, hoặc chỉ số nước mới nhất |
| `depositMonths` | `Integer` | Cột property (có thể null) |
| `serviceFee` | `BigDecimal` | Cột property (có thể null) |
| `rentalAvailable` | `Boolean` | Tính theo HĐ/phòng trống |

**Rooms:** dùng `RoomResponse` hiện có (không đổi schema).

---

## 3. File mới

| File | Vai trò |
|------|---------|
| `controller/PublicPropertyController.java` | REST public GET-only |
| `service/PublicPropertyService.java` | Interface |
| `service/impl/PublicPropertyServiceImpl.java` | Logic lọc ACTIVE + map DTO |
| `dto/response/GuestPropertyResponse.java` | Response cho guest |

---

## 4. File đã sửa

| File | Thay đổi |
|------|----------|
| `src/main/resources/application.yaml` | Block `spring.servlet.multipart` |
| `config/SecurityConfig.java` | `permitAll` GET `/api/v1/public/properties/**` |
| `exception/GlobalExceptionHandler.java` | Handler `MaxUploadSizeExceededException` → 413 |
| `entity/Property.java` | Cột guest: `latitude`, `longitude`, `electricity_unit_price`, `water_unit_price`, `deposit_months`, `service_fee` |
| `repository/PropertyRepository.java` | `findByStatusAndOperationManagerIdIsNotNull` |
| `repository/EquipmentRepository.java` | `findDistinctAmenityNamesByPropertyId` |
| `repository/MonthlyReadingRepository.java` | `findTopByPropertyIdAndRoomIsNullAndUtilityTypeOrderByBillingMonthDesc` |

### Schema DB (Hibernate `ddl-auto=update`)

Cột mới trên bảng `properties`:

```sql
latitude              DOUBLE PRECISION
longitude             DOUBLE PRECISION
electricity_unit_price NUMERIC
water_unit_price       NUMERIC
deposit_months         INTEGER
service_fee            NUMERIC
```

Tất cả **nullable** — app tự thêm khi khởi động.

---

## 5. Hướng dẫn FE

### Import file

- Không đổi endpoint import.
- Sau restart BE, file ~1.2MB+ upload được (trong giới hạn 25MB).
- Lỗi vượt ngưỡng: HTTP **413**, `message: "Maximum upload size exceeded"`.

### Trang guest (web + mobile)

**Đổi base URL:**

| Cũ (cần token) | Mới (public) |
|----------------|--------------|
| `GET /api/v1/properties` | `GET /api/v1/public/properties` |
| `GET /api/v1/properties/{id}` | `GET /api/v1/public/properties/{id}` |
| `GET /api/v1/properties/{id}/rooms` | `GET /api/v1/public/properties/{id}/rooms` |

**Type:** dùng `GuestPropertyResponse` thay vì `PropertyResponse` cho list/detail guest.

**Lưu ý field có thể null:** `latitude`, `longitude`, `depositMonths`, `serviceFee`, giá điện/nước — FE giữ fallback hiện tại cho đến khi BE nhập dữ liệu.

---

## 6. Checklist test

### Multipart

- [ ] Restart BE
- [ ] Upload file import ~1.2MB → thành công
- [ ] Upload file > 25MB → 413 + message đúng

### Public guest

- [ ] `GET /api/v1/public/properties` **không** gửi `Authorization` → 200
- [ ] Property `DRAFT` / chưa gán manager → không có trong list / detail 404
- [ ] `GET .../rooms` chỉ trả phòng `AVAILABLE`
- [ ] Endpoint cũ `/api/v1/properties` không token → vẫn 401/403 (đúng thiết kế)

---

## 7. Việc chưa làm / follow-up

- API cập nhật `latitude`, `longitude`, giá điện/nước, cọc, phí dịch vụ trên property (OM nhập tay) — cột DB đã có, chưa có endpoint PUT riêng.
- FE chuyển sang `/api/v1/public/properties` (web `propertyService.ts`, mobile `guestPropertyService.ts`).
