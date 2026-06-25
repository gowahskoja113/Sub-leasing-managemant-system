# API Import Excel 2 đợt — Hướng dẫn FE

> **📘 Tài liệu đầy đủ cho FE:** [`FE_ONBOARDING_IMPORT.md`](./FE_ONBOARDING_IMPORT.md) — luồng UI, API đọc nhà, session cải tạo, TB `THEM_MOI`/`THAY_THE`, TypeScript types.  
> File này giữ bản tóm tắt endpoint import; chi tiết xem file trên.

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
│  → Luôn nguyên căn; TB bàn giao chỉ hiển thị                 │
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
│  → Cấu hình NGUYEN_CAN/THEO_PHONG + cải tạo + TB mua mới    │
│  → auto định giá + gửi Host → PENDING_HOST_REVIEW            │
│  File: docs/SLMS2026_import_dot2_cai_tao.xlsx                │
└─────────────────────────────────────────────────────────────┘

Rollback (nếu import sai): DELETE /import/onboarding-excel/contracts/{contractCode}
```

**Cải tạo bổ sung (sau này, nhà đã ACTIVE):**

```
POST /properties/{id}/start-renovation     → mở session cải tạo mới
POST /import/renovation-supplement-excel   → file SLMS2026_import_cai_tao_bo_sung.xlsx
→ completeRenovation → ACTIVE (không gửi Host)
```

**Lưu ý trạng thái sau mỗi bước:**

| Sau bước | Mã HĐ demo | Trạng thái Property |
|----------|------------|---------------------|
| Đợt 1 — chờ đợt 2 | `HD-WH-RENO-*` | `UNDER_RENOVATION` |
| Đợt 1 — auto gửi Host (không có đợt 2) | `HD-WH-NORENO-*` | `PENDING_HOST_REVIEW` |
| Đợt 2 thành công | các mã có trong file đợt 2 | `PENDING_HOST_REVIEW` |
| Cải tạo bổ sung | sau `start-renovation` | `ACTIVE` |

---

## 2. Xác thực (chung)

```http
Authorization: Bearer <access_token>
```

Tất cả upload dùng **`multipart/form-data`**, không gửi JSON body.

---

## 3. `POST /import/lease-excel` — Đợt 1

### Request

| Thành phần | Giá trị |
|------------|---------|
| Method | `POST` |
| URL | `/api/v1/import/lease-excel` |
| Query | `dryRun` — `true` \| `false` (mặc định `false`) |
| Form field | `file` — file `.xlsx` hoặc `.xls` |

**Sheet BE đọc:**

| Sheet | Bắt buộc |
|-------|----------|
| `1. Hop_Dong_Thue` | Có |
| `2. Thiet_Bi_Ban_Giao` | Không (TB chủ bàn giao — chỉ hiển thị) |

> Đợt 1 **luôn** tạo Property nguyên căn. Không có sheet danh sách phòng.

### Response thành công — `200 OK`

```json
{
  "dryRun": true,
  "contractsProcessed": 3,
  "contractsSkipped": 1,
  "renovationLinesImported": 0,
  "equipmentRowsImported": 5,
  "results": [
    {
      "importStatus": "IMPORTED",
      "contractCode": "HD-WH-RENO-FURN",
      "propertyId": null,
      "propertyName": "Villa Thảo Điền View",
      "finalStatus": null,
      "message": "Dry run — sẽ được tạo mới"
    }
  ],
  "errors": []
}
```

| Field | Đợt 1 |
|-------|-------|
| `renovationLinesImported` | Luôn `0` |
| `equipmentRowsImported` | Số dòng sheet `2. Thiet_Bi_Ban_Giao` |

---

## 4. `POST /import/renovation-excel` — Đợt 2

### Request

| Form field | `file` — `SLMS2026_import_dot2_cai_tao.xlsx` |
| Query | `dryRun` |

**Sheet BE đọc:**

| Sheet | Bắt buộc |
|-------|----------|
| `1. Cau_Hinh_Khai_Thac` | Có (ít nhất 1 dòng / mã HĐ) |
| `2. Danh_Sach_Phong` | Có nếu `THEO_PHONG` |
| `3. Hop_Dong_Cai_Tao` | Không |
| `4. Thiet_Bi_Mua_Moi` | Không |

**Điều kiện tiên quyết:** mã HĐ đã import đợt 1, Property đang `UNDER_RENOVATION`.

**Sau import thật:** BE tự `completeRenovation` → định giá → `submitToHost` → `PENDING_HOST_REVIEW`.

### Response thành công — `200 OK`

```json
{
  "dryRun": false,
  "contractsProcessed": 2,
  "contractsSkipped": 0,
  "renovationLinesImported": 4,
  "equipmentRowsImported": 7,
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
| `renovationLinesImported` | Tổng dòng sheet `3. Hop_Dong_Cai_Tao` |
| `equipmentRowsImported` | Tổng dòng sheet `4. Thiet_Bi_Mua_Moi` |

---

## 4b. `POST /import/renovation-supplement-excel` — Cải tạo bổ sung

Dùng khi nhà **đã ACTIVE**, sau khi gọi `POST /properties/{propertyId}/start-renovation`.

### Request

| Form field | `file` — `SLMS2026_import_cai_tao_bo_sung.xlsx` |
| Query | `dryRun` |

**Sheet BE đọc:**

| Sheet | Bắt buộc |
|-------|----------|
| `1. Hop_Dong_Cai_Tao` | Không (ít nhất 1 dòng cải tạo hoặc TB / mã HĐ) |
| `2. Thiet_Bi_Mua_Moi` | Không |

**Tiên quyết:** Property `UNDER_RENOVATION` với **session cải tạo ≥ 2** (đã gọi `start-renovation`).

**Sau import thật:** `completeRenovation` → `ACTIVE`. **Không** gửi Host. TB mua **cộng dồn** manifest.

### Lỗi thường gặp

| `message` | Nguyên nhân |
|-----------|-------------|
| Phải gọi start-renovation trước… | Dùng nhầm API hoặc chưa mở session bổ sung |
| Nhà đang trong đợt cải tạo bổ sung — dùng renovation-supplement-excel | Gọi `renovation-excel` khi đã ở session ≥ 2 |

> Chi tiết response `renovationSessions`, `equipments`, `operationalStatus`: xem [`FE_ONBOARDING_IMPORT.md`](./FE_ONBOARDING_IMPORT.md) §5.

---

## 5. `POST /import/property-images-zip` — Upload ảnh

Gọi **sau đợt 1**. Zip: `{contractCode}/ten-anh.jpg`. Query `dryRun`.

---

## 6. `DELETE /import/onboarding-excel/contracts/{contractCode}` — Rollback

Xóa cứng Property và dữ liệu liên quan theo mã hợp đồng.

---

## 7. API đọc dữ liệu

> Đầy đủ: [`FE_ONBOARDING_IMPORT.md`](./FE_ONBOARDING_IMPORT.md) §5 — `GET /properties/{id}`, `/renovation/sessions`, `/equipments`.

### TB bàn giao — `GET /properties/{id}` → `handoverEquipments`

```json
{
  "handoverEquipments": [
    {
      "id": 1,
      "catalogId": 3,
      "catalogName": "Điều hòa",
      "description": "Máy lạnh 2HP cũ",
      "roomNumber": null,
      "houseArea": null,
      "status": "GOOD",
      "quantity": 2,
      "note": "Tầng 1, phòng khách — Chủ bàn giao"
    }
  ]
}
```

> TB bàn giao: `roomNumber` / `houseArea` luôn `null`. Vị trí ghi trong `note` (từ cột Mô tả vị trí).

---

## 8. Lỗi validation Excel — `400 Bad Request`

```json
{
  "status": 400,
  "error": "Bulk import validation failed",
  "message": "File Excel có lỗi validation",
  "errors": [
    {
      "sheet": "1. Cau_Hinh_Khai_Thac",
      "rowNumber": 3,
      "contractCode": "HD-WH-RENO-SPLIT",
      "field": "Số phòng khai thác",
      "message": "Phải tạo đủ 5 phòng chi tiết (hiện có 3)"
    }
  ]
}
```

### Lỗi parse — `422 Unprocessable Entity`

| `error` thường gặp | API |
|--------------------|-----|
| `Thiếu sheet bắt buộc: 1. Cau_Hinh_Khai_Thac` | renovation |
| `Thiếu sheet bắt buộc: 1. Hop_Dong_Thue` | lease |

---

## 9. Bảng lỗi validation theo sheet

### Đợt 1 — `2. Thiet_Bi_Ban_Giao`

| field | message |
|-------|---------|
| `Mã hợp đồng thuê` | Không tìm thấy mã ở sheet 1 |
| `Tên thiết bị` | không tìm thấy catalog |
| `Trạng thái thiết bị` | `NEW`, `GOOD`, `DAMAGED`, `BROKEN` |
| `Số lượng` | số nguyên dương |

> Không còn validate `Số phòng` / `Khu vực chung` ở đợt 1.

### Đợt 2 — `1. Cau_Hinh_Khai_Thac`

| field | message |
|-------|---------|
| `Mã hợp đồng thuê` | Chưa khởi tạo / phải `UNDER_RENOVATION` |
| `Hình thức khai thác` | `NGUYEN_CAN` hoặc `THEO_PHONG` |
| `Số phòng khai thác` | Bắt buộc khi `THEO_PHONG` |

### Đợt 2 — `2. Danh_Sach_Phong`

| field | message |
|-------|---------|
| `Số phòng khai thác` | Phải tạo đủ N phòng chi tiết |
| `Số phòng` | Nhà nguyên căn không cần sheet này |

### Đợt 2 — `4. Thiet_Bi_Mua_Moi`

| Cột | Bắt buộc | Ghi chú |
|-----|----------|---------|
| `Hành động` | Không (mặc định `THEM_MOI`) | `THEM_MOI` — thêm TB, giữ TB cũ ACTIVE. `THAY_THE` — disable đủ số lượng TB PURCHASED ACTIVE cùng catalog + vị trí rồi gán TB mới. |

| field | message |
|-------|---------|
| `Số phòng` | Phải khớp sheet danh sách phòng (nếu THEO_PHONG) |
| `Hành động` | `THAY_THE`: không đủ TB ACTIVE tại vị trí |
| `Số tháng bảo hành` | phải lớn hơn 0 |
| `Ngày bắt đầu/kết thúc bảo hành` | YYYY-MM-DD; ngày hết sau ngày bắt đầu |

File **cải tạo bổ sung** (`2. Thiet_Bi_Mua_Moi`) dùng cùng cột `Hành động`.

---

## 10. Gợi ý UI/UX

1. **Luôn dry-run trước** rồi import thật.
2. **Hai module upload riêng** + template tải từ `docs/`.
3. Module đợt 1: không hiển thị tùy chọn chia phòng.
4. Module đợt 2: giải thích `NGUYEN_CAN` vs `THEO_PHONG` từ sheet cấu hình.
5. Chi tiết nhà: tab TB bàn giao tách khỏi TB vận hành (`PURCHASED`).

---

## 11. Endpoint cũ

| Endpoint | Ghi chú |
|----------|---------|
| `POST /import/onboarding-excel` | 1 file 3 sheet — giữ tương thích, FE mới **không** dùng |

---

## 12. TypeScript types

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

*Tài liệu cập nhật 2026-06-25 — đồng bộ quy trình đợt 1 nguyên căn, đợt 2 cấu hình khai thác.*
