# Property Code — Implementation Spec (As-Built)

> Tài liệu ghi nhận **code đã triển khai** trên backend SLMS2026.  
> Yêu cầu gốc: BE-NEED — Thêm `propertyCode` (unique) cho bất động sản (01/09/2026)
>
> **Ngày implement:** 01/09/2026  
> **Trạng thái:** ✅ Backend đã ship — FE có thể tích hợp import hoá đơn theo lô

---

## Mục lục

1. [Tóm tắt](#1-tóm-tắt)
2. [Bối cảnh & vấn đề](#2-bối-cảnh--vấn-đề)
3. [Quy tắc mã nhà](#3-quy-tắc-mã-nhà)
4. [API & contract](#4-api--contract)
5. [Database & migration](#5-database--migration)
6. [Tích hợp FE](#6-tích-hợp-fe)
7. [Kiểm chứng](#7-kiểm-chứng)
8. [File code liên quan](#8-file-code-liên-quan)

---

## 1. Tóm tắt

| Khía cạnh | Trước | Sau (as-built) |
|-----------|-------|----------------|
| Khóa khớp folder zip | Token đầu `propertyName` (FE tự parse) | Cột `property_code` unique, cố định |
| Response `PropertyResponse` | Không có mã | Có `propertyCode` (lowercase) |
| Tạo nhà | — | BE tự sinh mã nếu client không gửi |
| Đổi tên nhà | Làm hỏng khớp zip | `propertyCode` **không đổi** |
| Trùng mã (khác hoa/thường) | FE bỏ qua, không đoán | BE từ chối `409 Conflict` |

---

## 2. Bối cảnh & vấn đề

FE đã có luồng **nhập hoá đơn điện/nước theo lô**: admin nén file `.zip`, mỗi nhà một folder con (`MTX#124/`, `MTX#125/`, …), FE đối chiếu tên folder với danh sách nhà rồi OCR + phát hành hàng loạt qua `POST /api/v1/admin/utility-bills` (gọi nhiều lần).

Trước đây FE khớp bằng **token đầu của `propertyName`**:

```ts
// utils/zipUtilityBills.ts — CŨ
const codeOfProperty = (p) => p.propertyName.trim().split(/\s+/)[0].toLowerCase();
```

Cách này hỏng khi ai đó sửa tên nhà, hai nhà trùng tiền tố, hoặc tên không theo quy ước mã. BE thêm `propertyCode` để có **ràng buộc dữ liệu** thay vì quy ước đặt tên.

---

## 3. Quy tắc mã nhà

### 3.1 Format

| Thuộc tính | Giá trị |
|------------|---------|
| Độ dài tối đa | 32 ký tự |
| Ký tự cho phép | `a-z`, `A-Z`, `0-9`, `#`, `-`, `_` |
| Lưu trữ | Chuẩn hoá **lowercase** khi ghi DB |
| So khớp | Không phân biệt hoa/thường (vì đã normalize) |
| Unique | Có — index `uq_properties_property_code` |

### 3.2 Sinh mã khi tạo nhà

Thứ tự ưu tiên (`PropertyCodeService.resolveForCreate`):

1. **Client gửi `propertyCode`** → normalize + validate format + kiểm tra unique → dùng mã đó.
2. **Không gửi** → lấy token đầu `propertyName` (giống FE cũ):
   - `"MTX#124 THEO_PHONG NT cơ bản"` → `mtx#124`
   - Token phải chứa ít nhất một chữ/số (tránh tên tiếng Việt thuần chữ)
3. **Không suy được từ tên** → sinh `mtx#1`, `mtx#2`, … (số tăng dần, không trùng nhà đang có).

### 3.3 Xử lý trùng

- Khi auto-sinh từ tên: thêm hậu tố `-2`, `-3`, … (ví dụ `mtx#12-2`).
- Khi client gửi mã trùng: **từ chối** — không tự đổi tên.

### 3.4 Immutability

- `PUT /api/v1/properties/{id}` **không** cập nhật `propertyCode`.
- Đổi `propertyName` → `propertyCode` giữ nguyên.

---

## 4. API & contract

### 4.1 Endpoints trả `propertyCode`

| Method | Path | Ghi chú |
|--------|------|---------|
| `GET` | `/api/v1/properties` | Danh sách — FE dùng để đối chiếu zip |
| `GET` | `/api/v1/properties/{id}` | Chi tiết |
| `GET` | `/api/v1/properties/rentable` | Nhà còn cho thuê |
| `POST` | `/api/v1/properties` | Tạo nhà (admin) |
| `POST` | `/api/v1/properties/draft` | Tạo nhà nháp (onboarding) |

### 4.2 Request — tạo nhà

`PropertyCreateRequest` / `PropertyDraftRequest`:

```json
{
  "propertyCode": "MTX#124",
  "propertyName": "MTX#124 THEO_PHONG NT cơ bản",
  "address": "123 Nguyễn Văn Linh",
  "zoneId": "…",
  "descriptions": "…"
}
```

| Field | Bắt buộc | Ghi chú |
|-------|----------|---------|
| `propertyCode` | Không | Bỏ trống → BE tự sinh theo mục 3.2 |
| `propertyName` | Có | Không ảnh hưởng mã sau khi đã tạo |

### 4.3 Response — `PropertyResponse` (trường mới)

```json
{
  "id": 42,
  "propertyCode": "mtx#124",
  "propertyName": "MTX#124 THEO_PHONG NT cơ bản",
  "shortAddress": "123 Nguyễn Văn Linh",
  "fullAddress": "123 Nguyễn Văn Linh, Quận 7, TP.HCM",
  "status": "ACTIVE"
}
```

> `propertyCode` luôn lowercase trong response.

### 4.4 Lỗi

| HTTP | Khi nào | Message mẫu |
|------|---------|---------------|
| `400` | Format mã không hợp lệ | `Mã nhà chỉ được chứa chữ, số và ký tự # - _, tối đa 32 ký tự` |
| `409` | Mã trùng nhà khác | `Mã nhà "mtx#124" đã được sử dụng` |

---

## 5. Database & migration

Migration tự chạy qua `DatabaseSchemaMigration.ensurePropertyCodeColumn()` khi app start.

### 5.1 Cột mới `properties`

| Cột | Kiểu | Ràng buộc |
|-----|------|-----------|
| `property_code` | `VARCHAR(32)` | NOT NULL, UNIQUE |

### 5.2 Backfill nhà cũ

1. Thêm cột nullable.
2. Với mỗi nhà chưa có mã (theo `id` tăng dần):
   - Suy từ `property_name` (token đầu, lowercase) — cùng logic FE cũ.
   - Trùng → `-2`, `-3`, …
   - Không suy được → `mtx#N` (N tăng dần).
3. Log warning khi có collision hoặc phải sinh mã mới.
4. `ALTER COLUMN … SET NOT NULL` + unique index.

Sau backfill, file zip cũ (folder `MTX#124/`, …) vẫn khớp nếu trước đó khớp qua token đầu tên.

---

## 6. Tích hợp FE

### 6.1 Đổi hàm khớp zip

```ts
// utils/zipUtilityBills.ts — MỚI
const codeOfProperty = (p) => (p.propertyCode ?? '').toLowerCase();
```

### 6.2 Fallback giai đoạn chuyển tiếp (tùy chọn)

Nếu BE chưa deploy hết môi trường:

```ts
const codeOfProperty = (p) =>
  (p.propertyCode || p.propertyName.trim().split(/\s+/)[0]).toLowerCase();
```

### 6.3 Không đổi

- Cấu trúc zip (folder theo mã nhà).
- Luồng OCR, bảng đối chiếu, phát hành hàng loạt.
- API `POST /api/v1/admin/utility-bills`.

### 6.4 Checklist FE

- [ ] Type `PropertyResponse` thêm `propertyCode?: string`
- [ ] `codeOfProperty` dùng `propertyCode` (có fallback nếu cần)
- [ ] Bảng đối chiếu zip hiển thị `propertyCode` thay vì parse tên
- [ ] Không cần đổi format folder zip nếu đã đặt theo mã cũ

---

## 7. Kiểm chứng

1. Tạo nhà không gửi `propertyCode` → BE trả mã tự sinh, không trùng.
2. Tạo nhà `propertyCode: "MTX#124"` khi đã có `mtx#124` → `409`.
3. `GET /api/v1/properties` → mọi phần tử có `propertyCode` khác rỗng.
4. Sau backfill: số nhà = số `propertyCode` phân biệt.
5. `PUT` đổi `propertyName` → `propertyCode` không đổi.
6. Import zip folder `MTX#124/` khớp nhà có `propertyCode: "mtx#124"`.

---

## 8. File code liên quan

| File | Vai trò |
|------|---------|
| `entity/Property.java` | Cột `propertyCode` |
| `dto/response/PropertyResponse.java` | Trả mã trong API |
| `dto/request/PropertyCreateRequest.java` | Nhận mã tùy chọn (admin tạo) |
| `dto/request/PropertyDraftRequest.java` | Nhận mã tùy chọn (onboarding) |
| `service/PropertyCodeService.java` | Sinh / validate / unique |
| `util/PropertyCodeHelper.java` | Normalize, extract từ tên, format `mtx#N` |
| `service/impl/PropertyServiceImpl.java` | Gán mã khi `POST /properties` |
| `service/impl/PropertyOnboardingServiceImpl.java` | Gán mã khi `POST /properties/draft` |
| `repository/PropertyRepository.java` | `existsByPropertyCode`, `findAllPropertyCodes` |
| `config/DatabaseSchemaMigration.java` | Migration + backfill |

---

## Phụ lục — Giá trị ngoài import zip

- In hồ sơ / hợp đồng: dùng mã ngắn thay vì tên nhà dài.
- Mọi luồng import theo lô sau này dùng chung một khoá.
- Tra cứu / báo lỗi: `"nhà mtx#124"` ổn định hơn tên có thể bị sửa.

---

*Tài liệu as-built — cập nhật khi có thay đổi API.*
