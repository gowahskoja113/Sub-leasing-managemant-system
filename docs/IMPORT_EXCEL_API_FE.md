# API Import Excel 2 đợt — Hướng dẫn FE

> Tài liệu gửi Frontend: cách gọi API, request/response mẫu, mã lỗi.  
> Đồng bộ với BE: `docs/IMPORT_EXCEL_2_DOT.md`  
> **Base URL:** `{API_HOST}/api/v1`  
> **Quyền:** tất cả endpoint import yêu cầu role **ADMIN** (JWT).

---

## 1. Luồng thao tác UI

```
┌─────────────────────────────────────────────────────────────┐
│  Bước 1 — Module "Khởi tạo nhà"                              │
│  POST /import/lease-excel?dryRun=true   → xem lỗi / preview  │
│  POST /import/lease-excel?dryRun=false  → ghi DB             │
│  File: docs/SLMS2026_import_dot1_khoi_tao.xlsx                │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Bước 2 — Upload ảnh (giữa 2 đợt)                            │
│  POST /import/property-images-zip?dryRun=                    │
│  Zip: {contractCode}/anh.jpg                                 │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Bước 3 — Module "Cấu hình khai thác"                        │
│  POST /import/renovation-excel?dryRun=true                   │
│  POST /import/renovation-excel?dryRun=false                  │
│  → auto định giá + gửi Host → PENDING_HOST_REVIEW            │
│  File: docs/SLMS2026_import_dot2_cai_tao.xlsx                │
└─────────────────────────────────────────────────────────────┘

Rollback (nếu import sai): DELETE /import/onboarding-excel/contracts/{contractCode}
```

**Lưu ý trạng thái sau mỗi bước:**

| Sau bước | Mã HĐ demo | Trạng thái Property |
|----------|------------|---------------------|
| Đợt 1 — chờ đợt 2 | `HD-WH-RENO-*`, `HD-ROOM-*` | `UNDER_RENOVATION` |
| Đợt 1 — auto gửi Host (không có đợt 2) | `HD-WH-NORENO-*` | `PENDING_HOST_REVIEW` |
| Đợt 2 thành công | các mã có dòng trong file đợt 2 | `PENDING_HOST_REVIEW` |

---

## 2. Xác thực (chung)

```http
Authorization: Bearer <access_token>
```

| HTTP | Ý nghĩa | Body mẫu |
|------|---------|----------|
| **403** | Không phải ADMIN | `{ "status": 403, "error": "Forbidden", "message": "..." }` |
| **401** | Token hết hạn / thiếu | Tuỳ cấu hình Spring Security |

Tất cả upload dùng **`multipart/form-data`**, không gửi JSON body.

---

## 3. `POST /import/lease-excel` — Đợt 1

### Request

| Thành phần | Giá trị |
|------------|---------|
| Method | `POST` |
| URL | `/api/v1/import/lease-excel` |
| Query | `dryRun` — `true` \| `false` (mặc định `false`) |
| Content-Type | `multipart/form-data` |
| Form field | `file` — file `.xlsx` hoặc `.xls` |

**Sheet BE đọc:**

| Sheet | Bắt buộc |
|-------|----------|
| `1. Hop_Dong_Thue` | Có |
| `2. Thiet_Bi_Ban_Giao` | Không (TB chủ bàn giao — chỉ hiển thị) |
| `3. Danh_Sach_Phong` | Có nếu nhà **theo phòng** (`HD-ROOM-*` / tag `[THEO_PHONG]`) |

### Ví dụ — JavaScript (fetch)

```javascript
const formData = new FormData();
formData.append('file', fileInput.files[0]);

const res = await fetch(`${API}/import/lease-excel?dryRun=true`, {
  method: 'POST',
  headers: { Authorization: `Bearer ${token}` },
  body: formData,
});
const data = await res.json();
```

### Ví dụ — cURL

```bash
curl -X POST "http://localhost:8080/api/v1/import/lease-excel?dryRun=true" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "file=@docs/SLMS2026_import_dot1_khoi_tao.xlsx"
```

### Response thành công — `200 OK`

```json
{
  "dryRun": true,
  "contractsProcessed": 4,
  "contractsSkipped": 2,
  "renovationLinesImported": 0,
  "equipmentRowsImported": 15,
  "results": [
    {
      "importStatus": "IMPORTED",
      "contractCode": "HD-WH-RENO-FURN",
      "propertyId": null,
      "propertyName": "Villa Thảo Điền View",
      "finalStatus": null,
      "message": "Dry run — sẽ được tạo mới"
    },
    {
      "importStatus": "SKIPPED",
      "contractCode": "HD-WH-NORENO-FURN",
      "propertyId": 12,
      "propertyName": "Nhà phố Gò Vấp",
      "finalStatus": "UNDER_RENOVATION",
      "message": "Mã hợp đồng đã tồn tại — bỏ qua"
    }
  ],
  "errors": []
}
```

**Khi `dryRun=false`**, dòng `IMPORTED` có `propertyId` và `finalStatus` thực tế (`UNDER_RENOVATION` hoặc `PENDING_HOST_REVIEW` với `HD-WH-NORENO-*`).

### Ý nghĩa field response

| Field | Đợt 1 |
|-------|-------|
| `contractsProcessed` | Số HĐ import thành công (không tính SKIPPED) |
| `contractsSkipped` | Số HĐ bỏ qua (trùng mã / trùng địa chỉ) |
| `renovationLinesImported` | Luôn `0` ở đợt 1 |
| `equipmentRowsImported` | Số dòng sheet `2. Thiet_Bi_Ban_Giao` được xử lý |
| `results[].importStatus` | `IMPORTED` \| `SKIPPED` |
| `results[].message` | Lý do skip hoặc ghi chú dry-run |

### Lý do SKIPPED (đợt 1)

| `message` | Nguyên nhân |
|-----------|-------------|
| `Mã hợp đồng đã tồn tại — bỏ qua` | `contractCode` đã có trong DB |
| `Địa chỉ đã được dùng cho tòa nhà khác — bỏ qua` | Full address trùng DB |
| `Địa chỉ bị trùng trong file — bỏ qua` | Hai dòng sheet 1 cùng địa chỉ |

---

## 4. `POST /import/renovation-excel` — Đợt 2

### Request

| Thành phần | Giá trị |
|------------|---------|
| Method | `POST` |
| URL | `/api/v1/import/renovation-excel` |
| Query | `dryRun` — `true` \| `false` |
| Form field | `file` — `SLMS2026_import_dot2_cai_tao.xlsx` |

**Sheet BE đọc:**

| Sheet | Bắt buộc |
|-------|----------|
| `1. Hop_Dong_Cai_Tao` | Có (ít nhất 1 dòng / mã HĐ) |
| `2. Thiet_Bi_Mua_Moi` | Không |

**Điều kiện tiên quyết:** mã HĐ đã import đợt 1, Property đang `UNDER_RENOVATION`.

**Sau import thật (`dryRun=false`):** BE tự `completeRenovation` → tính khấu hao → `submitToHost` → `PENDING_HOST_REVIEW`. FE **không** cần gọi thêm API định giá thủ công.

### Response thành công — `200 OK`

```json
{
  "dryRun": false,
  "contractsProcessed": 2,
  "contractsSkipped": 0,
  "renovationLinesImported": 4,
  "equipmentRowsImported": 12,
  "results": [
    {
      "importStatus": "IMPORTED",
      "contractCode": "HD-WH-RENO-FURN",
      "propertyId": 5,
      "propertyName": "Villa Thảo Điền View",
      "finalStatus": "PENDING_HOST_REVIEW",
      "message": null
    }
  ],
  "errors": []
}
```

| Field | Đợt 2 |
|-------|-------|
| `renovationLinesImported` | Tổng dòng sheet `1. Hop_Dong_Cai_Tao` |
| `equipmentRowsImported` | Tổng dòng sheet `2. Thiet_Bi_Mua_Moi` (TB mua + bảo hành) |

### Lý do SKIPPED (đợt 2)

| `message` | Nguyên nhân |
|-----------|-------------|
| `Đã hoàn thành đợt 2 / đã gửi Host — bỏ qua` | Property đã `PENDING_HOST_REVIEW`, `ACTIVE`, v.v. |

---

## 5. `POST /import/property-images-zip` — Upload ảnh

Gọi **sau đợt 1**, trước hoặc sau đợt 2 đều được (miễn mã HĐ đã tồn tại).

### Request

| Form field | `file` — file `.zip` |
| Query | `dryRun` |

**Cấu trúc zip:**

```
zip-root/
  HD-WH-RENO-FURN/
    phong-khach.jpg
    ngoai-canh.png
  HD-ROOM-RENO-FURN/
    101.jpg
```

Hoặc có thêm folder bọc ngoài: `folder-tong/HD-WH-RENO-FURN/anh.jpg`.

### Response thành công — `200 OK`

```json
{
  "dryRun": false,
  "contractsInZip": 2,
  "contractsMatched": 2,
  "contractsNotFound": 0,
  "imagesAttached": 8,
  "results": [
    {
      "status": "ATTACHED",
      "contractCode": "HD-WH-RENO-FURN",
      "propertyId": 5,
      "propertyName": "Villa Thảo Điền View",
      "imagesAttached": 5,
      "message": null
    },
    {
      "status": "NOT_FOUND",
      "contractCode": "HD-UNKNOWN",
      "propertyId": null,
      "propertyName": null,
      "imagesAttached": 0,
      "message": "Không tìm thấy căn với mã hợp đồng này (import Excel trước?)"
    }
  ],
  "warnings": [
    "Mã hợp đồng \"HD-UNKNOWN\" có 3 ảnh trong zip nhưng không tìm thấy trong DB — bỏ qua"
  ]
}
```

### `results[].status`

| Giá trị | Ý nghĩa |
|---------|---------|
| `ATTACHED` | Đã gán ảnh vào Property (`imageUrls`) |
| `PREVIEW` | `dryRun=true` — chỉ đếm, chưa ghi |
| `NOT_FOUND` | Mã HĐ trong zip không có trong DB |
| `NO_IMAGES` | Folder HĐ không có file ảnh hợp lệ |

Zip rỗng / không có ảnh hợp lệ: vẫn **200**, `contractsInZip: 0`, có `warnings`.

---

## 6. `DELETE /import/onboarding-excel/contracts/{contractCode}` — Rollback

Xóa cứng Property và toàn bộ dữ liệu liên quan theo **mã hợp đồng** (cột Excel).

### Request

```http
DELETE /api/v1/import/onboarding-excel/contracts/HD-WH-RENO-FURN
Authorization: Bearer <token>
```

### Response thành công — `200 OK`

```json
{
  "propertyId": 5,
  "propertyName": "Villa Thảo Điền View",
  "contractCode": "HD-WH-RENO-FURN",
  "equipmentsDeleted": 3,
  "equipmentManifestsDeleted": 2,
  "renovationLinesDeleted": 2,
  "renovationSessionsDeleted": 1,
  "roomsDeleted": 10,
  "depreciationResultsDeleted": 1,
  "monthlyReadingsDeleted": 0
}
```

### Lỗi rollback

| HTTP | `error` / `message` |
|------|---------------------|
| **404** | `Không tìm thấy hợp đồng inbound với mã: ...` |
| **422** | `Còn khách thuê — không thể vô hiệu/xóa` |
| **422** | `Không thể xóa căn nhà đã có chỉ số điện nước...` |

---

## 7. API đọc dữ liệu liên quan (chi tiết nhà)

### `GET /api/v1/properties/{id}` — Property + TB bàn giao

Field mới trên `PropertyResponse`:

```json
{
  "id": 5,
  "propertyName": "Villa Thảo Điền View",
  "status": "UNDER_RENOVATION",
  "wholeHouse": true,
  "hasRenovation": true,
  "handoverEquipments": [
    {
      "id": 1,
      "catalogId": 3,
      "catalogName": "Điều hòa",
      "description": "Máy lạnh 2HP tầng 1",
      "roomNumber": null,
      "houseArea": null,
      "status": "GOOD",
      "quantity": 2,
      "note": "Chủ bàn giao"
    }
  ]
}
```

> `handoverEquipments` = TB đợt 1, **chỉ hiển thị**, không khấu hao.  
> TB vận hành (đợt 2, `PURCHASED`) lấy qua API equipment/onboarding hiện có — có thêm `warrantyMonths`, `warrantyStartDate`, `warrantyEndDate`.

### `GET /api/v1/properties/{propertyId}/handover-equipments`

Trả về mảng `HandoverEquipmentResponse[]` (cùng schema như phần tử trong `handoverEquipments` ở trên).

---

## 8. Lỗi validation Excel — `400 Bad Request`

Khi file có lỗi dòng/cột, BE **không ghi DB** (kể cả `dryRun=false`), trả:

```json
{
  "status": 400,
  "error": "Bulk import validation failed",
  "message": "File Excel có lỗi validation",
  "errors": [
    {
      "sheet": "1. Hop_Dong_Thue",
      "rowNumber": 3,
      "contractCode": "HD-WH-RENO-FURN",
      "field": "Ngày kết thúc",
      "message": "Ngày kết thúc phải sau ngày bắt đầu"
    },
    {
      "sheet": "3. Danh_Sach_Phong",
      "rowNumber": 2,
      "contractCode": "HD-ROOM-RENO-FURN",
      "field": "Tổng số phòng",
      "message": "Phải tạo đủ 10 phòng chi tiết (hiện có 8)"
    }
  ]
}
```

**FE nên:** hiển thị bảng lỗi theo `sheet` + `rowNumber` + `field` + `message`; highlight dòng tương ứng nếu có preview Excel.

### Lỗi parse file — `422 Unprocessable Entity`

Xảy ra **trước** validation (file/sheet/cột sai cấu trúc):

```json
{
  "timestamp": "2026-06-24T10:00:00",
  "status": 422,
  "error": "Thiếu sheet bắt buộc: 1. Hop_Dong_Thue"
}
```

| `error` thường gặp | API |
|--------------------|-----|
| `File Excel không được để trống` | lease / renovation |
| `Chỉ hỗ trợ file .xlsx hoặc .xls` | lease / renovation |
| `Thiếu sheet bắt buộc: ...` | lease / renovation |
| `Sheet ... thiếu cột bắt buộc: ...` | lease / renovation |
| `Không đọc được file Excel: ...` | lease / renovation |
| `File zip không được để trống` | images-zip |
| `File phải là định dạng .zip` | images-zip |

### Upload quá lớn — `413 Payload Too Large`

```json
{
  "status": 413,
  "error": "Payload Too Large",
  "message": "Maximum upload size exceeded"
}
```

---

## 9. Bảng lỗi validation theo sheet (tham khảo UI)

### Đợt 1 — `1. Hop_Dong_Thue`

| field | message |
|-------|---------|
| `Mã hợp đồng` | không được để trống / bị trùng trong file |
| `Tên tòa nhà`, `Địa chỉ chi tiết`, `Quận/Huyện`, `Tỉnh/Thành phố`, `Tên chủ nhà`, `Mô tả chi tiết` | `... không được để trống` |
| `Diện tích (m²)` | phải lớn hơn 0 |
| `Tổng số tầng`, `Tổng số phòng` | phải lớn hơn 0 |
| `Tổng tiền thuê` | phải lớn hơn 0 |
| `Ngày bắt đầu`, `Ngày kết thúc` | không hợp lệ (YYYY-MM-DD) |
| `Ngày kết thúc` | phải sau ngày bắt đầu |
| `Quận/Huyện / Tỉnh/Thành phố` | Không tìm thấy Zone... |

### Đợt 1 — `2. Thiet_Bi_Ban_Giao`

| field | message |
|-------|---------|
| `Mã hợp đồng thuê` | Không tìm thấy mã ở sheet 1 |
| `Tên thiết bị` | không tìm thấy catalog |
| `Trạng thái thiết bị` | `NEW`, `GOOD`, `DAMAGED`, `BROKEN` |
| `Số lượng` | số nguyên dương |

> TB bàn giao **chỉ hiển thị** — không gán phòng/khu vực. Ghi vị trí trong `Mô tả chi tiết` hoặc `Ghi chú` nếu cần.

### Đợt 1 — `3. Danh_Sach_Phong`

| field | message |
|-------|---------|
| `Tổng số phòng` | Phải tạo đủ N phòng chi tiết |
| `Số phòng` | Nhà nguyên căn không cần sheet này |
| `Tầng` | từ 1 đến tổng số tầng |
| `Diện tích phòng (m²)` | phải lớn hơn 0 |

### Đợt 2 — `1. Hop_Dong_Cai_Tao`

| field | message |
|-------|---------|
| `Mã hợp đồng thuê` | Chưa khởi tạo tòa nhà / phải `UNDER_RENOVATION` / phải có ≥1 dòng cải tạo |
| `Mã danh mục cải tạo` | không tìm thấy (`PAINTING`, `PLUMBING`, ...) |
| `Chi phí cải tạo (VNĐ)` | phải lớn hơn 0 |

### Đợt 2 — `2. Thiet_Bi_Mua_Moi`

| field | message |
|-------|---------|
| `Vị trí` | Số phòng hoặc Khu vực chung |
| `Tên Catalog thiết bị` | không tìm thấy catalog |
| `Trạng thái thiết bị` | chỉ `NEW` hoặc `GOOD` |
| `Đơn giá (VNĐ)` | phải lớn hơn 0 |
| `Số tháng bảo hành` | phải lớn hơn 0 |
| `Ngày bắt đầu/kết thúc bảo hành` | YYYY-MM-DD; ngày hết sau ngày bắt đầu |

---

## 10. Gợi ý UI/UX

1. **Luôn dry-run trước:** `dryRun=true` → hiển thị `results` + bảng `errors` (nếu 400) → user sửa file → import thật.
2. **Hai module upload riêng** với template tải về:
   - `docs/SLMS2026_import_dot1_khoi_tao.xlsx`
   - `docs/SLMS2026_import_dot2_cai_tao.xlsx`
3. **Bảng kết quả:** cột `importStatus`, `contractCode`, `propertyName`, `finalStatus`, `message`.
4. **Ẩn "Định giá & Phê duyệt"** cho nhà đã qua đợt 2 (`PENDING_HOST_REVIEW` từ import).
5. **Chi tiết nhà:** tab TB bàn giao (`handoverEquipments`) tách khỏi TB vận hành (equipment `PURCHASED`).
6. **Mã `HD-WH-NORENO-*`:** không có bước đợt 2; sau đợt 1 đã `PENDING_HOST_REVIEW`.

---

## 11. Endpoint cũ (không dùng cho luồng mới)

| Endpoint | Ghi chú |
|----------|---------|
| `POST /import/onboarding-excel` | 1 file 3 sheet — giữ tương thích, FE mới **không** dùng |

---

## 12. TypeScript types (copy cho FE)

```typescript
interface BulkImportError {
  sheet: string;
  rowNumber: number;
  contractCode: string | null;
  field: string | null;
  message: string;
}

interface BulkImportContractResult {
  importStatus: 'IMPORTED' | 'SKIPPED';
  contractCode: string;
  propertyId: number | null;
  propertyName: string | null;
  finalStatus: string | null;
  message: string | null;
}

interface BulkImportResponse {
  dryRun: boolean;
  contractsProcessed: number;
  contractsSkipped: number;
  renovationLinesImported: number;
  equipmentRowsImported: number;
  results: BulkImportContractResult[];
  errors: BulkImportError[];
}

interface BulkImportValidationErrorBody {
  status: 400;
  error: 'Bulk import validation failed';
  message: string;
  errors: BulkImportError[];
}

interface HandoverEquipment {
  id: number;
  catalogId: number;
  catalogName: string;
  description: string | null;
  roomNumber: string | null;
  houseArea: string | null;
  status: string;
  quantity: number;
  note: string | null;
}
```

---

*Tài liệu sinh từ codebase BE — cập nhật khi đổi DTO hoặc endpoint.*
