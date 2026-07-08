# Hướng dẫn Test API Maintenance

## Tổng quan các API đã sửa

### 1. **Admin Maintenance Controller** (`/api/admin/maintenance`)

#### 1.1. Lấy tất cả maintenance requests (có phân trang)

```http
GET /api/admin/maintenance?page=0&size=10&sort=createdAt,desc
Authorization: Bearer {admin_token}
```

#### 1.2. Lấy tất cả maintenance requests (không phân trang)

```http
GET /api/admin/maintenance/all
Authorization: Bearer {admin_token}
```

#### 1.3. Lấy chi tiết một maintenance request

```http
GET /api/admin/maintenance/{id}
Authorization: Bearer {admin_token}
```

#### 1.4. Lấy thống kê dashboard

```http
GET /api/admin/maintenance/dashboard
Authorization: Bearer {admin_token}
```

Response:

```json
{
  "total": 10,
  "message": "Total Maintenance Requests"
}
```

---

### 2. **Tenant Mobile Controller** (`/api/mobile/maintenance`)

#### 2.1. Tạo maintenance request mới

```http
POST /api/mobile/maintenance
Authorization: Bearer {tenant_token}
Content-Type: application/json

{
  "roomId": "uuid-of-room",
  "propertyId": "uuid-of-property",
  "category": "PLUMBING",
  "priority": "HIGH",
  "description": "Vòi nước bị rò rỉ ở phòng tắm",
  "imageUrls": [
    "https://example.com/image1.jpg",
    "https://example.com/image2.jpg"
  ]
}
```

**Các giá trị hợp lệ:**

- `category`: PLUMBING, ELECTRICAL, HVAC, APPLIANCE, STRUCTURAL, PEST_CONTROL, GENERAL
- `priority`: LOW, MEDIUM, HIGH, URGENT

#### 2.2. Lấy danh sách maintenance requests của tôi

```http
GET /api/mobile/maintenance/my-requests
Authorization: Bearer {tenant_token}
```

#### 2.3. Lấy chi tiết một maintenance request

```http
GET /api/mobile/maintenance/{id}
Authorization: Bearer {tenant_token}
```

---

### 3. **Manager Mobile Controller** (`/api/mobile/manager/maintenance`)

#### 3.1. Lấy danh sách maintenance requests được assign cho tôi

```http
GET /api/mobile/manager/maintenance
Authorization: Bearer {manager_token}
```

#### 3.2. Assign maintenance request cho một manager

```http
PUT /api/mobile/manager/maintenance/{id}/assign?managerId={manager_uuid}
Authorization: Bearer {manager_or_admin_token}
```

#### 3.3. Cập nhật status của maintenance request

```http
PUT /api/mobile/manager/maintenance/{id}/status?status=IN_PROGRESS
Authorization: Bearer {manager_token}
```

**Các giá trị status hợp lệ:**

- PENDING
- ASSIGNED
- IN_PROGRESS
- COMPLETED
- CANCELLED

#### 3.4. Resolve maintenance request (hoàn thành và tích hợp tài chính)

```http
PUT /api/mobile/manager/maintenance/{id}/resolve
Authorization: Bearer {manager_token}
Content-Type: application/json

{
  "resolutionNote": "Đã thay thế vòi nước mới và kiểm tra hệ thống",
  "laborCost": 500000,
  "materialCost": 300000,
  "externalServiceCost": 0,
  "equipmentIds": [1, 2, 3]
}
```

---

## Các thay đổi đã thực hiện

### ✅ Sửa lỗi trong TenantMobileController

- **Vấn đề cũ**: Method `createRequest` thiếu tham số `tenantId`
- **Giải pháp**: Lấy thông tin user từ `Principal`, sau đó truy vấn database để lấy `userId` và truyền vào service

### ✅ Sửa lỗi trong ManagerMobileController

- **Vấn đề cũ**: Sử dụng `UUID.fromString(principal.getName())` sẽ lỗi vì `principal.getName()` trả về username chứ không phải UUID
- **Giải pháp**: Lấy username từ Principal, sau đó query database để lấy User object và sử dụng `user.getId()`

### ✅ Cải thiện AdminMaintenanceController

- Thêm hỗ trợ phân trang với Pageable
- Thêm endpoint `/all` để lấy tất cả records không phân trang
- Cải thiện response cho dashboard stats (trả về JSON object thay vì String)
- Sử dụng `ResponseEntity.notFound()` cho trường hợp không tìm thấy record

---

## Cách test với Postman

### 1. Đăng nhập để lấy token

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "tenant_user",
  "password": "password123"
}
```

Lưu `accessToken` từ response.

### 2. Thêm Authorization header

- Vào tab **Authorization**
- Chọn type: **Bearer Token**
- Paste token vào ô **Token**

### 3. Test các endpoint theo vai trò

- **TENANT**: Test các API trong `/api/mobile/maintenance`
- **MANAGER**: Test các API trong `/api/mobile/manager/maintenance`
- **ADMIN**: Test các API trong `/api/admin/maintenance`

---

## Lưu ý quan trọng

1. **Authentication**: Tất cả các endpoint đều yêu cầu JWT token hợp lệ
2. **Authorization**: Mỗi endpoint có role cụ thể (ADMIN, MANAGER, TENANT)
3. **UUID Format**: Khi truyền ID, phải sử dụng định dạng UUID hợp lệ
4. **Enum Values**: Các field như `category`, `priority`, `status` phải sử dụng đúng giá trị enum

---

## Kiểm tra logs

Khi gặp lỗi, kiểm tra console logs để xem:

- Exception stack trace
- SQL queries được thực thi
- Thông tin user authentication

---

## Postman Collection

Import file sau vào Postman để có sẵn tất cả các request:
`postman/Sub-leasing managemant system.postman_collection.json`

Cập nhật collection này với các endpoint maintenance đã sửa.
