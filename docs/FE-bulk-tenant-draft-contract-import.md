# Import Hàng Loạt HĐ Nháp — Hướng Dẫn FE

Tài liệu này mô tả luồng **import hàng loạt hợp đồng thuê nháp (`DRAFT`)** từ file Excel để FE hoặc QA có thể follow đúng với backend hiện tại.

**Tham chiếu thêm:**
- File mẫu: `docs/SLMS2026_import_tenant_draft_contracts.xlsx`
- Luồng onboarding sau import: `docs/FE-tenant-onboarding-otp-flow.md`

---

## 1. Mục tiêu

API này dùng để tạo nhanh nhiều hợp đồng nháp cho khách thuê trên các BĐS đã có sẵn trong hệ thống.

Kết quả mỗi dòng hợp lệ:
- tạo `tenant_contract` ở trạng thái `DRAFT`
- lưu thông tin khách chính vào draft
- gán manager đón khách nếu có
- giữ hợp đồng ở trạng thái chờ tiếp tục onboarding, xuất file, thanh toán cọc và OTP

---

## 2. API

**Method:** `POST`  
**Path:** `/api/v1/import/tenant-draft-contracts-excel`  
**Auth:** `ADMIN` hoặc `MANAGER`  
**Content-Type:** `multipart/form-data`

### Params

| Tên | Kiểu | Bắt buộc | Ý nghĩa |
|-----|------|----------|---------|
| `file` | file | Có | File Excel import |
| `dryRun` | boolean | Không | `true` = chỉ validate, không ghi DB |

### Ví dụ request

```http
POST /api/v1/import/tenant-draft-contracts-excel?dryRun=true
Authorization: Bearer {token}
Content-Type: multipart/form-data
```

Form-data:

```text
file = SLMS2026_import_tenant_draft_contracts.xlsx
```

---

## 3. Khi nào FE nên dùng `dryRun`

Khuyến nghị luồng FE:

1. User chọn file Excel
2. FE gọi `dryRun=true`
3. Nếu có lỗi validation, hiển thị lỗi theo từng dòng
4. Nếu pass, hỏi user xác nhận import thật
5. FE gọi lại `dryRun=false`

Điều này giúp tránh tạo dở nhiều hợp đồng nháp khi file có lỗi.

---

## 4. Cấu trúc file Excel

Sheet dữ liệu backend đang đọc là:

`1. Hop_Dong_Nhap_Khach`

### Header chuẩn

Thứ tự cột hiện tại trong file mẫu:

| STT | Header |
|-----|--------|
| 1 | `Mã HĐ inbound` |
| 2 | `Mã BĐS` |
| 3 | `Tên tòa nhà` |
| 4 | `Loại thuê` |
| 5 | `Số phòng` |
| 6 | `Họ tên khách thuê` |
| 7 | `CCCD` |
| 8 | `Số điện thoại` |
| 9 | `Ngày sinh` |
| 10 | `Ngày cấp CCCD` |
| 11 | `Nơi cấp CCCD` |
| 12 | `Hộ khẩu thường trú` |
| 13 | `Ngày vào ở` |
| 14 | `Ngày kết thúc` |
| 15 | `Giá thuê/tháng` |
| 16 | `Số tháng cọc` |
| 17 | `Tiền cọc` |
| 18 | `Ngày đón khách dự kiến` |

> Cột `SĐT quản lý đón khách` đã bị bỏ. Quản lý phụ trách hợp đồng luôn tự động = **Operation Manager của nhà** (`Property.operationManagerId`), không nhập trong Excel nữa.

### Các cột bắt buộc tối thiểu

- `Họ tên khách thuê`
- `CCCD`
- `Số điện thoại`
- `Ngày vào ở`
- `Ngày kết thúc`
- `Giá thuê/tháng`

### Chọn BĐS để map

Mỗi dòng cần xác định được đúng BĐS bằng **ít nhất một** trong ba cách:

1. `Mã HĐ inbound`  
2. `Mã BĐS`  
3. `Tên tòa nhà`

Khuyến nghị FE/ops ưu tiên:

1. `Mã HĐ inbound`
2. `Mã BĐS`
3. `Tên tòa nhà` chỉ dùng khi chắc chắn không trùng

---

## 5. Ý nghĩa từng cột quan trọng

### 5.1. `Loại thuê`

Giá trị hợp lệ:
- `NGUYEN_CAN`
- `THEO_PHONG`
- `PHONG`
- `ROOM`
- `WHOLE_HOUSE`

Nếu để trống:
- có `Số phòng` → backend tự hiểu là thuê theo phòng
- không có `Số phòng` → backend suy luận theo cấu hình BĐS

### 5.2. `Số phòng`

- Bắt buộc nếu thuê theo phòng
- Phải tồn tại trong BĐS tương ứng
- Nếu thuê nguyên căn thì để trống

### 5.3. `Tiền cọc` và `Số tháng cọc`

- Nếu có `Tiền cọc` → backend dùng trực tiếp
- Nếu `Tiền cọc` trống nhưng có `Số tháng cọc` và `Giá thuê/tháng` → backend tự tính:

```text
deposit = rentAmount * depositMonths
```

### 5.4. `Hộ khẩu thường trú`

Đây là field mới đã được hỗ trợ.

Backend sẽ:
- đọc từ cột `Hộ khẩu thường trú`
- lưu vào draft contract khi hợp đồng còn `DRAFT`
- khi confirm tạo tenant chính thức, giá trị này đi vào profile tenant
- dùng để render `HKTT` trong file hợp đồng

### 5.5. `Ngày cấp CCCD` / `Nơi cấp CCCD`

Hai field này đã được backend hỗ trợ đầy đủ trong import.

Nếu import cho khách đã tồn tại theo số điện thoại:
- backend vẫn cho phép cập nhật lại
- có thể ghi đè dữ liệu cũ bằng dữ liệu mới từ file Excel

---

## 6. Rule validate chính của backend

### 6.1. Validate theo dữ liệu khách

- `Họ tên khách thuê` không được trống
- `CCCD` không được trống
- `Số điện thoại` không được trống

### 6.2. Validate ngày

- `Ngày vào ở` phải parse được
- `Ngày kết thúc` phải parse được
- `Ngày kết thúc` phải sau `Ngày vào ở`
- thời hạn thuê tối đa 5 năm

### 6.3. Validate tiền

- `Giá thuê/tháng` phải lớn hơn 0
- `Tiền cọc` không được âm

### 6.4. Validate BĐS/phòng

- BĐS phải tồn tại
- BĐS phải ở trạng thái `ACTIVE`
- nếu theo phòng thì phòng phải tồn tại
- phòng không được đang `RENTED`
- không được trùng khoảng thời gian với hợp đồng hiệu lực khác
- trong cùng một file, không được import trùng cùng một BĐS/phòng

### 6.5. Quản lý phụ trách

Không còn nhập quản lý trong file. BE tự gán quản lý phụ trách = **Operation Manager của nhà**
(`Property.operationManagerId`). Vì nhà chỉ `ACTIVE` sau khi đã gán Operation Manager, nên mọi
BĐS import được (đã `ACTIVE`) đều chắc chắn có quản lý.

---

## 7. Response khi `dryRun=true`

Nếu file hợp lệ, backend trả `200 OK` với danh sách preview.

Ví dụ rút gọn:

```json
{
  "dryRun": true,
  "contractsProcessed": 5,
  "contractsSkipped": 0,
  "results": [
    {
      "importStatus": "IMPORTED",
      "contractCode": "(dry-run)",
      "propertyId": 12,
      "propertyName": "MTX#07 THEO_PHONG cải tạo",
      "finalStatus": "DRAFT",
      "message": "Sẽ tạo DRAFT cho Lê Minh Châu — phòng 101"
    }
  ],
  "errors": []
}
```

FE nên:
- hiển thị số dòng pass
- hiển thị preview message
- cho user nút xác nhận import thật

---

## 8. Response khi `dryRun=false`

Ví dụ rút gọn:

```json
{
  "dryRun": false,
  "contractsProcessed": 5,
  "contractsSkipped": 0,
  "results": [
    {
      "importStatus": "IMPORTED",
      "contractCode": "HD-MT-2026-00061",
      "propertyId": 12,
      "propertyName": "MTX#07 THEO_PHONG cải tạo",
      "finalStatus": "DRAFT",
      "message": "Đã tạo HĐ nháp cho Lê Minh Châu — phòng 101"
    }
  ],
  "errors": []
}
```

FE có thể dùng `results` để:
- hiển thị toast/thông báo thành công
- điều hướng sang màn danh sách hợp đồng nháp
- lọc theo `propertyId` hoặc tên BĐS

---

## 9. Response khi có lỗi validation

Backend ném lỗi validation với danh sách lỗi theo từng dòng/cột.

FE nên hiển thị:
- sheet
- số dòng
- field/cột lỗi
- message

Ví dụ các lỗi thường gặp:

| Message | Ý nghĩa |
|---------|---------|
| `Không tìm thấy HĐ inbound '...' trong hệ thống` | Sai mã inbound |
| `Không tìm thấy BĐS ID ...` | Sai mã BĐS |
| `Có 2 BĐS trùng tên ...` | Không được map theo tên vì trùng |
| `Thuê theo phòng bắt buộc có Số phòng` | Thiếu cột phòng |
| `Phòng đang được cho thuê` | Phòng không còn trống |
| `Phòng đã có hợp đồng chồng lấn thời gian` | Trùng thời gian thuê |
| `BĐS ... chưa ACTIVE` | Nhà chưa sẵn sàng để cho thuê |
| `Không tìm thấy quản lý với SĐT ...` | Sai manager |

---

## 10. Mapping dữ liệu sau import

Mỗi dòng import hợp lệ sẽ tạo hợp đồng với các đặc điểm:

- `status = DRAFT`
- `paymentStatus = PENDING`
- `requireDepositPayment = true`
- có thể có `assignedManager`
- lưu tạm thông tin tenant trong draft:
  - `fullName`
  - `phoneNumber`
  - `cccd`
  - `dateOfBirth`
  - `cccdIssueDate`
  - `cccdIssuePlace`
  - `permanentAddress`

Sau đó FE có thể tiếp tục dùng luồng onboarding bình thường:

1. mở chi tiết hợp đồng
2. cập nhật ảnh hiện trạng / chỉ số điện nước / ghi chú
3. xuất file hợp đồng nháp
4. tạo QR thanh toán cọc
5. gửi OTP
6. confirm hợp đồng

---

## 11. Gợi ý UI cho FE

### Màn import

- Upload file `.xlsx`
- Checkbox hoặc toggle `Dry run`
- Nút `Kiểm tra file`
- Nút `Import thật` chỉ bật khi dry-run pass

### Màn kết quả

- Số dòng hợp lệ
- Số dòng lỗi
- Danh sách preview/thành công
- Danh sách lỗi theo dòng

### Gợi ý flow

1. User tải file mẫu
2. User điền Excel
3. FE upload `dryRun=true`
4. FE render lỗi/preview
5. FE upload `dryRun=false`
6. FE cho user mở danh sách HĐ `DRAFT`

---

## 12. File mẫu hiện tại

File mẫu backend đang đồng bộ:

`docs/SLMS2026_import_tenant_draft_contracts.xlsx`

File này đã có:
- cột `Hộ khẩu thường trú`
- dữ liệu mẫu cho 5 khách
- ví dụ cả `NGUYEN_CAN` và `THEO_PHONG`

---

## 13. Checklist FE

- Dùng đúng endpoint `POST /api/v1/import/tenant-draft-contracts-excel`
- Gửi `multipart/form-data`
- Hỗ trợ `dryRun=true` trước khi import thật
- Dùng đúng header cột Excel, đặc biệt:
  - `Ngày cấp CCCD`
  - `Nơi cấp CCCD`
  - `Hộ khẩu thường trú`
- Hiển thị lỗi theo dòng
- Sau import thành công, điều hướng user sang danh sách `DRAFT`

---

## 14. Ví dụ nhanh

```bash
curl -X POST "http://localhost:8080/api/v1/import/tenant-draft-contracts-excel?dryRun=true" \
  -H "Authorization: Bearer {token}" \
  -F "file=@docs/SLMS2026_import_tenant_draft_contracts.xlsx"
```

Import thật:

```bash
curl -X POST "http://localhost:8080/api/v1/import/tenant-draft-contracts-excel?dryRun=false" \
  -H "Authorization: Bearer {token}" \
  -F "file=@docs/SLMS2026_import_tenant_draft_contracts.xlsx"
```
