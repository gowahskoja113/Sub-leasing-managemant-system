# BE — Import ảnh từ file ZIP (bước 2 sau Excel)

**Ngày:** 20/06/2026  
**Luồng:** Import Excel trước → import file ZIP ảnh sau

---

## Luồng 2 bước

1. **Bước 1 — Excel** (như cũ)  
   `POST /api/v1/import/onboarding-excel?dryRun=false`  
   Tạo căn + `InboundContract.contractCode` trong DB.

2. **Bước 2 — ZIP ảnh** (mới)  
   `POST /api/v1/import/property-images-zip?dryRun=false`  
   BE giải nén zip, lưu ảnh local, gán URL vào `Property.imageUrls` theo mã hợp đồng.

---

## Cấu trúc file ZIP

```
anh-cac-toa-nha.zip
├── HD-HCM-WH-RENO-01/          ← tên folder = contractCode trong Excel
│   ├── 01-mat-tien.jpg
│   └── 02-phong-khach.jpg
├── HD-HCM-ROOM-RENO-01/
│   └── 01-tong-the.png
└── ...
```

Hoặc có thêm **một cấp folder tổng** (tên bất kỳ):

```
zip-root/
└── import-media/
    ├── HD001/
    │   └── mat-tien.jpg
    └── HD002/
        └── 1.webp
```

### Quy ước

| Quy tắc | Chi tiết |
|---------|----------|
| Khớp mã HĐ | Tên folder con = `contractCode` (trim, **không phân biệt hoa/thường**) |
| Định dạng ảnh | `.jpg`, `.jpeg`, `.png`, `.webp` |
| Thứ tự | Sắp xếp theo tên file (prefix `01-`, `02-`…) |
| Bỏ qua | `__MACOSX`, `.DS_Store`, file không phải ảnh, lồng sâu > 1 cấp contractCode |
| Ghi đè | Mỗi lần import zip **thay thế** toàn bộ `imageUrls` của căn (không append) |

---

## API

### Kiểm tra zip (không ghi DB)

```http
POST /api/v1/import/property-images-zip?dryRun=true
Authorization: Bearer <ADMIN token>
Content-Type: multipart/form-data

file = anh-cac-toa-nha.zip
```

### Import ảnh thật

```http
POST /api/v1/import/property-images-zip?dryRun=false
```

### Response mẫu

```json
{
  "dryRun": false,
  "contractsInZip": 3,
  "contractsMatched": 2,
  "contractsNotFound": 1,
  "imagesAttached": 5,
  "results": [
    {
      "status": "ATTACHED",
      "contractCode": "HD001",
      "propertyId": 12,
      "propertyName": "Biệt thự A",
      "imagesAttached": 2,
      "message": null
    },
    {
      "status": "NOT_FOUND",
      "contractCode": "HD999",
      "propertyId": null,
      "propertyName": null,
      "imagesAttached": 0,
      "message": "Không tìm thấy căn với mã hợp đồng này (import Excel trước?)"
    }
  ],
  "warnings": [
    "Mã hợp đồng \"HD999\" có 1 ảnh trong zip nhưng không tìm thấy trong DB — bỏ qua"
  ]
}
```

**`status` từng căn:** `ATTACHED` | `PREVIEW` (dryRun) | `NOT_FOUND`

---

## Lưu trữ & URL ảnh

BE lưu file trên disk (không upload Cloudinary):

| Config | Mặc định |
|--------|----------|
| `app.upload.property-images.dir` | `uploads/properties` |
| `app.upload.property-images.public-base-url` | `http://localhost:8080` |

URL ghi vào DB:

```
http://localhost:8080/uploads/properties/HD001/a1b2c3d4-mat-tien.jpg
```

- Ảnh phục vụ public: `GET /uploads/properties/**` (không cần token — guest xem được).
- Thư mục `uploads/` nằm trong `.gitignore`.

**Production:** set env `APP_PUBLIC_BASE_URL=https://api.example.com` để URL trong DB trỏ đúng domain.

---

## Giới hạn upload

```yaml
spring.servlet.multipart:
  max-file-size: 200MB
  max-request-size: 210MB
```

Nginx/gateway: `client_max_body_size 210m;`

---

## File code liên quan

| File | Vai trò |
|------|---------|
| `BulkImportController` | Endpoint `/property-images-zip` |
| `BulkPropertyImageImportServiceImpl` | Logic gán ảnh theo contractCode |
| `PropertyImageZipParser` | Giải nén + validate cấu trúc zip |
| `LocalPropertyImageStorage` | Lưu file, trả public URL |
| `WebMvcConfig` | Serve static `/uploads/properties/**` |
| `SecurityConfig` | permitAll GET uploads |

---

## Checklist test

- [ ] Import Excel thành công (có `contractCode` trong DB)
- [ ] `dryRun=true` với zip hợp lệ → `PREVIEW`, không đổi DB
- [ ] `dryRun=false` → `imageUrls` có URL, mở URL trên browser thấy ảnh
- [ ] Mã HĐ trong zip không có trong DB → `NOT_FOUND`, không crash
- [ ] Zip sai cấu trúc / không có ảnh → 400 + message rõ

---

## Ghi chú FE

- Màn import: **2 bước tách biệt** — Excel xong mới chọn file `.zip`.
- Không dùng folder picker cho bước 2 (BE nhận **một file zip**).
- Có thể nén folder `import-media/` thành zip trước khi upload.
