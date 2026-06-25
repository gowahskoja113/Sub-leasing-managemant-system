# Hướng dẫn FE — Onboarding, Import Excel, Cải tạo & Thiết bị

> **Base URL:** `{API_HOST}/api/v1`  
> **Quyền import:** role `ADMIN` (JWT `Authorization: Bearer …`)  
> **Template Excel:** thư mục `docs/`  
> **Cập nhật:** 2026-06-25

---

## Mục lục

1. [Tóm tắt nhanh](#1-tóm-tắt-nhanh)
2. [Khái niệm FE cần nhớ](#2-khái-niệm-fe-cần-nhớ)
3. [Luồng thao tác UI](#3-luồng-thao-tác-ui)
4. [API Import Excel](#4-api-import-excel)
5. [API đọc dữ liệu nhà](#5-api-đọc-dữ-liệu-nhà)
6. [Cải tạo bổ sung (session v2+)](#6-cải-tạo-bổ-sung-session-v2)
7. [Cột Excel — TB mua mới](#7-cột-excel--tb-mua-mới)
8. [Lỗi validation](#8-lỗi-validation)
9. [TypeScript types](#9-typescript-types)
10. [Gợi ý UI](#10-gợi-ý-ui)

---

## 1. Tóm tắt nhanh

| Việc | Method | Endpoint | File Excel |
|------|--------|----------|------------|
| Đợt 1 — HĐ thuê + TB bàn giao | `POST` | `/import/lease-excel?dryRun=` | `SLMS2026_import_dot1_khoi_tao.xlsx` |
| Upload ảnh | `POST` | `/import/property-images-zip?dryRun=` | Zip `{contractCode}/anh.jpg` |
| Đợt 2 — Khai thác + cải tạo + TB mua | `POST` | `/import/renovation-excel?dryRun=` | `SLMS2026_import_dot2_cai_tao.xlsx` |
| Cải tạo bổ sung | `POST` | `/import/renovation-supplement-excel?dryRun=` | `SLMS2026_import_cai_tao_bo_sung.xlsx` |
| Mở session cải tạo mới | `POST` | `/properties/{id}/renovation/start` | — |
| Rollback import sai | `DELETE` | `/import/onboarding-excel/contracts/{contractCode}` | — |
| Chi tiết nhà | `GET` | `/properties/{id}` | — |
| Lịch sử cải tạo | `GET` | `/properties/{id}/renovation/sessions` | — |
| TB vận hành (gắn phòng) | `GET` | `/properties/{id}/equipments` | — |

**Luôn gọi `dryRun=true` trước**, xem `errors[]`, rồi mới `dryRun=false`.

Tất cả upload dùng `multipart/form-data`, field `file`.

---

## 2. Khái niệm FE cần nhớ

### 2.1 Ba loại thiết bị trên UI

| Loại | Nguồn BE | API / field | Ghi chú UI |
|------|----------|-------------|------------|
| TB bàn giao chủ | `INITIAL_HANDOVER` (chỉ hiển thị) | `handoverEquipments` | Không gắn phòng vận hành. Vị trí trong `note` |
| TB vận hành | `PURCHASED` + `INITIAL_HANDOVER` đã gán phòng | `GET …/equipments` | Dùng cho sơ đồ phòng / kho |
| TB theo đợt cải tạo | `PURCHASED` gắn `renovationSession` | `renovationSessions[].equipments` | Xem lịch sử từng đợt |

### 2.2 Hai lớp “phiên bản” — **không trộn lẫn**

```
┌─────────────────────────────────────────────────────────────┐
│  RenovationSession (chi phí cải tạo)                          │
│  v1 DISABLED  →  v2 ACTIVE   (sau cải tạo bổ sung)          │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  Equipment PURCHASED (đồ thật trong nhà)                      │
│  THEM_MOI  → giữ TB cũ ACTIVE + thêm TB mới ACTIVE          │
│  THAY_THE  → TB cũ DISABLED + TB mới ACTIVE (cùng vị trí)   │
└─────────────────────────────────────────────────────────────┘
```

| Trường | Ý nghĩa | Khi nào đổi |
|--------|---------|-------------|
| `RenovationSession.status` | `IN_PROGRESS` \| `ACTIVE` \| `DISABLED` | Đợt cải tạo mới hoàn thành → đợt cũ `DISABLED` |
| `Equipment.operationalStatus` | `ACTIVE` \| `DISABLED` | Chỉ khi import `Hành động = THAY_THE` |
| `currentEffective` | `true` = đang có hiệu lực | Map trực tiếp từ `status` / `operationalStatus` |

### 2.3 Ví dụ nghiệp vụ

**THAY_THẾ** — v1 mua 1 máy lạnh, v2 mua máy mới thay:

- Excel: `Hành động = THAY_THE`, cùng catalog `Điều hòa`, cùng `Khu vực chung` hoặc `Số phòng`
- Kết quả: máy v1 `operationalStatus: DISABLED`, máy v2 `ACTIVE`, gắn `renovationVersionLabel: "v2"`

**THÊM_MỚI** — v1 nội thất cơ bản, v2 mua thêm ghế/bàn:

- Excel: `Hành động = THEM_MOI` (hoặc để trống)
- Kết quả: toàn bộ đồ v1 vẫn `ACTIVE`, đồ v2 thêm `ACTIVE`

---

## 3. Luồng thao tác UI

```
Bước 1  POST /import/lease-excel
        → Property DRAFT → UNDER_RENOVATION (HD-WH-RENO-*)
        → hoặc PENDING_HOST_REVIEW (HD-WH-NORENO-*, không có đợt 2)

Bước 2  POST /import/property-images-zip

Bước 3  POST /import/renovation-excel
        → Cấu hình NGUYEN_CAN / THEO_PHONG
        → Cải tạo + TB mua (THEM_MOI mặc định)
        → auto định giá + gửi Host → PENDING_HOST_REVIEW

─── Sau khi nhà ACTIVE ───

Bước A  POST /properties/{id}/renovation/start
        → ACTIVE → UNDER_RENOVATION, session ≥ 2

Bước B  POST /import/renovation-supplement-excel
        → completeRenovation → ACTIVE (không gửi Host lại)
```

| Sau bước | Mã HĐ demo | `Property.status` |
|----------|------------|-------------------|
| Đợt 1, chờ đợt 2 | `HD-WH-RENO-*` | `UNDER_RENOVATION` |
| Đợt 1, không cải tạo | `HD-WH-NORENO-*` | `PENDING_HOST_REVIEW` |
| Đợt 2 xong | có trong file đợt 2 | `PENDING_HOST_REVIEW` |
| Cải tạo bổ sung xong | sau `start-renovation` | `ACTIVE` |

---

## 4. API Import Excel

### 4.1 Response chung — `BulkImportResponse`

```json
{
  "dryRun": true,
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

| Field | Đợt 1 | Đợt 2 | Bổ sung |
|-------|-------|-------|---------|
| `renovationLinesImported` | `0` | Sheet `3. Hop_Dong_Cai_Tao` | Sheet `1. Hop_Dong_Cai_Tao` |
| `equipmentRowsImported` | Sheet `2. Thiet_Bi_Ban_Giao` | Sheet `4. Thiet_Bi_Mua_Moi` | Sheet `2. Thiet_Bi_Mua_Moi` |

`importStatus`: `IMPORTED` \| `SKIPPED` (trùng HĐ / đã xử lý đợt 2).

---

### 4.2 `POST /import/lease-excel` — Đợt 1

| Sheet | Bắt buộc |
|-------|----------|
| `1. Hop_Dong_Thue` | Có |
| `2. Thiet_Bi_Ban_Giao` | Không |

- Luôn tạo nhà **nguyên căn** (`wholeHouse: true`).
- TB bàn giao chỉ lưu hiển thị, **không** tạo `equipments` vận hành.

---

### 4.3 `POST /import/renovation-excel` — Đợt 2

| Sheet | Bắt buộc |
|-------|----------|
| `1. Cau_Hinh_Khai_Thac` | Có |
| `2. Danh_Sach_Phong` | Có nếu `THEO_PHONG` |
| `3. Hop_Dong_Cai_Tao` | Không |
| `4. Thiet_Bi_Mua_Moi` | Không |

**Tiên quyết:** HĐ đã import đợt 1, `Property.status = UNDER_RENOVATION`.

**Sau import thật:** `completeRenovation` → định giá → `submitToHost` → `PENDING_HOST_REVIEW`.

---

### 4.4 `POST /import/renovation-supplement-excel` — Cải tạo bổ sung

| Sheet | Bắt buộc |
|-------|----------|
| `1. Hop_Dong_Cai_Tao` | Không (cần ≥ 1 dòng cải tạo hoặc TB) |
| `2. Thiet_Bi_Mua_Moi` | Không |

**Tiên quyết:** đã gọi `POST /properties/{id}/renovation/start` (session ≥ 2).

**Sau import thật:** `completeRenovation` → `ACTIVE`. **Không** gửi Host. Manifest TB mua **cộng dồn**.

| Lỗi `message` | Nguyên nhân |
|---------------|-------------|
| Phải gọi start-renovation trước… | Chưa mở session bổ sung |
| Nhà đang trong đợt cải tạo bổ sung — dùng renovation-supplement-excel | Gọi nhầm `renovation-excel` |

---

### 4.5 `POST /import/property-images-zip`

Zip: `{contractCode}/ten-anh.jpg` — gọi sau đợt 1.

---

### 4.6 `DELETE /import/onboarding-excel/contracts/{contractCode}`

Xóa cứng Property + toàn bộ dữ liệu onboarding theo mã HĐ.

---

## 5. API đọc dữ liệu nhà

### 5.1 `GET /properties/{id}`

```json
{
  "id": 5,
  "propertyName": "Villa Thảo Điền View",
  "status": "ACTIVE",
  "wholeHouse": true,
  "hasRenovation": true,
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
  ],
  "activeRenovationSession": {
    "id": 2,
    "sessionNumber": 2,
    "versionLabel": "v2",
    "status": "ACTIVE",
    "currentEffective": true,
    "startDate": "2027-01-15",
    "endDate": "2027-02-20",
    "disabledAt": null,
    "totalCost": 11500000,
    "lines": [
      { "id": 5, "categoryName": "Sơn sửa", "cost": 8000000, "note": "Cải tạo bổ sung lần 2" }
    ],
    "equipments": [
      {
        "id": 42,
        "catalogId": 3,
        "catalogName": "Điều hòa",
        "roomId": null,
        "roomNumber": null,
        "houseArea": "LIVING_ROOM",
        "source": "PURCHASED",
        "status": "NEW",
        "operationalStatus": "ACTIVE",
        "currentEffective": true,
        "price": 18500000,
        "note": "Thay điều hòa mới phòng khách",
        "warrantyMonths": 36,
        "warrantyStartDate": "2027-01-01",
        "warrantyEndDate": "2030-12-31",
        "disabledAt": null
      }
    ]
  },
  "renovationSessions": [
    {
      "sessionNumber": 2,
      "versionLabel": "v2",
      "status": "ACTIVE",
      "currentEffective": true,
      "totalCost": 11500000,
      "equipments": []
    },
    {
      "sessionNumber": 1,
      "versionLabel": "v1",
      "status": "DISABLED",
      "currentEffective": false,
      "disabledAt": "2027-02-20T10:00:00",
      "totalCost": 95000000,
      "equipments": [
        {
          "catalogName": "Điều hòa",
          "operationalStatus": "DISABLED",
          "currentEffective": false,
          "disabledAt": "2027-02-20T10:00:00"
        }
      ]
    }
  ]
}
```

**Cách hiển thị gợi ý:**

- Tab **Lịch sử cải tạo:** list `renovationSessions` (đã sort mới nhất trước).
- Badge đợt: `versionLabel` + `status` (`ACTIVE` = xanh, `DISABLED` = xám gạch).
- Trong mỗi đợt: bảng `lines` (chi phí) + `equipments` (TB mua đợt đó).
- TB `operationalStatus === 'DISABLED'`: hiển thị “Đã thay thế” / gạch ngang, không tính vào đồ đang dùng.

---

### 5.2 `GET /properties/{id}/renovation/sessions`

Trả `RenovationSessionResponse[]` — cùng schema như `renovationSessions` ở trên (đủ `lines`, `equipments`, `status`, `versionLabel`).

---

### 5.3 `GET /properties/{id}/equipments`

Danh sách TB vận hành (mọi nguồn đã gán phòng/khu vực):

```json
[
  {
    "id": 40,
    "propertyId": 5,
    "roomId": null,
    "catalogId": 3,
    "catalogName": "Điều hòa",
    "houseArea": "LIVING_ROOM",
    "source": "PURCHASED",
    "status": "NEW",
    "price": 16500000,
    "operationalStatus": "DISABLED",
    "currentEffective": false,
    "renovationSessionNumber": 1,
    "renovationVersionLabel": "v1",
    "disabledAt": "2027-02-20T10:00:00"
  },
  {
    "id": 42,
    "catalogName": "Điều hòa",
    "operationalStatus": "ACTIVE",
    "currentEffective": true,
    "renovationSessionNumber": 2,
    "renovationVersionLabel": "v2"
  },
  {
    "id": 43,
    "catalogName": "Quạt",
    "operationalStatus": "ACTIVE",
    "currentEffective": true,
    "renovationSessionNumber": 2,
    "renovationVersionLabel": "v2"
  }
]
```

**Filter UI đồ đang dùng:** `currentEffective === true` (hoặc `operationalStatus === 'ACTIVE'`).

---

### 5.4 `GET /properties/{id}/handover-equipments`

Chỉ TB bàn giao (tách tab riêng với TB vận hành).

---

## 6. Cải tạo bổ sung (session v2+)

### `POST /properties/{propertyId}/renovation/start`

| Trước | Sau |
|-------|-----|
| `status = ACTIVE` | `status = UNDER_RENOVATION` |
| session 1 `ACTIVE` | session 2 `IN_PROGRESS` (mới) |

### `POST /properties/{propertyId}/renovation/complete`

Đóng session hiện tại. Session cũ `ACTIVE` → `DISABLED`. Nhà về `ACTIVE` (luồng bổ sung) hoặc chuyển bước tiếp (onboarding).

---

## 7. Cột Excel — TB mua mới

Áp dụng sheet `4. Thiet_Bi_Mua_Moi` (đợt 2) và `2. Thiet_Bi_Mua_Moi` (bổ sung).

| Cột | Bắt buộc | Giá trị |
|-----|----------|---------|
| Mã hợp đồng thuê | Có | |
| Số phòng | Một trong hai | Điền **hoặc** Khu vực chung, không cả hai |
| Khu vực chung | Một trong hai | `LIVING_ROOM`, `KITCHEN`, `BATHROOM`, `BALCONY`, `GARAGE`, `OTHER` |
| Tên Catalog thiết bị | Có | Khớp catalog hệ thống |
| Trạng thái thiết bị | Có | `NEW` hoặc `GOOD` |
| **Hành động** | Không | `THEM_MOI` (mặc định) \| `THAY_THE` |
| Số lượng | Có | Số nguyên dương |
| Đơn giá (VNĐ) | Có | > 0 |
| Số tháng bảo hành | Có | > 0 |
| Ngày bắt đầu / hết bảo hành | Có | `YYYY-MM-DD`, ngày hết > ngày bắt |
| Ghi chú lắp đặt | Không | |

**`THEM_MOI`:** thêm TB, giữ TB cũ.  
**`THAY_THE`:** disable đủ `Số lượng` TB `PURCHASED` + `ACTIVE` cùng catalog + vị trí, rồi gán TB mới.

File mẫu bổ sung có 2 dòng demo:

1. `Điều hòa` + `THAY_THE` — thay máy v1  
2. `Quạt` + `THEM_MOI` — thêm 2 quạt, giữ đồ cũ

---

## 8. Lỗi validation

### HTTP `400` — lỗi từng dòng Excel

```json
{
  "status": 400,
  "error": "Bulk import validation failed",
  "message": "File Excel có lỗi validation",
  "errors": [
    {
      "sheet": "2. Thiet_Bi_Mua_Moi",
      "rowNumber": 3,
      "contractCode": "HD-WH-RENO-FURN",
      "field": "Hành động",
      "message": "THAY_THE: không đủ thiết bị ACTIVE tại vị trí (cần 1, hiện có 0)"
    }
  ]
}
```

Hiển thị: `sheet` + dòng `rowNumber` + `field` + `message`.

### HTTP `422` — file sai cấu trúc

Ví dụ: `Thiếu sheet bắt buộc: 1. Hop_Dong_Thue`

### Lỗi thường gặp theo sheet

| Sheet | field | message |
|-------|-------|---------|
| `4. Thiet_Bi_Mua_Moi` | Vị trí | Phải điền Số phòng **hoặc** Khu vực chung |
| `4. Thiet_Bi_Mua_Moi` | Hành động | Giá trị không hợp lệ / không đủ TB để THAY_THE |
| `1. Cau_Hinh_Khai_Thac` | Hình thức khai thác | `NGUYEN_CAN` hoặc `THEO_PHONG` |
| `2. Thiet_Bi_Ban_Giao` | Tên thiết bị | Không tìm thấy catalog |

---

## 9. TypeScript types

```typescript
// ─── Import ───

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

// ─── TB bàn giao ───

interface HandoverEquipment {
  id: number;
  catalogId: number;
  catalogName: string;
  description: string | null;
  roomNumber: null;
  houseArea: null;
  status: string;
  quantity: number;
  note: string | null;
}

// ─── Cải tạo ───

type RenovationSessionStatus = 'IN_PROGRESS' | 'ACTIVE' | 'DISABLED';
type EquipmentOperationalStatus = 'ACTIVE' | 'DISABLED';
type EquipmentImportAction = 'THEM_MOI' | 'THAY_THE';

interface RenovationSessionLine {
  id: number;
  categoryName: string;
  cost: number;
  note: string | null;
}

interface SessionEquipment {
  id: number;
  catalogId: number;
  catalogName: string;
  roomId: number | null;
  roomNumber: string | null;
  houseArea: string | null;
  source: 'PURCHASED' | 'INITIAL_HANDOVER';
  status: string;
  operationalStatus: EquipmentOperationalStatus;
  currentEffective: boolean;
  price: number;
  note: string | null;
  warrantyMonths: number | null;
  warrantyStartDate: string | null;
  warrantyEndDate: string | null;
  disabledAt: string | null;
}

interface RenovationSession {
  id: number;
  sessionNumber: number;
  versionLabel: string; // "v1", "v2", ...
  status: RenovationSessionStatus;
  currentEffective: boolean;
  startDate: string | null;
  endDate: string | null;
  disabledAt: string | null;
  totalCost: number;
  lines: RenovationSessionLine[];
  equipments: SessionEquipment[];
}

// ─── TB vận hành ───

interface Equipment {
  id: number;
  propertyId: number;
  roomId: number | null;
  catalogId: number;
  catalogName: string;
  houseArea: string | null;
  source: 'PURCHASED' | 'INITIAL_HANDOVER';
  status: string;
  price: number;
  note: string | null;
  warrantyMonths: number | null;
  warrantyStartDate: string | null;
  warrantyEndDate: string | null;
  operationalStatus: EquipmentOperationalStatus;
  currentEffective: boolean;
  renovationSessionNumber: number | null;
  renovationVersionLabel: string | null;
  disabledAt: string | null;
}

interface PropertyDetail {
  id: number;
  propertyName: string;
  status: string;
  wholeHouse: boolean;
  hasRenovation: boolean;
  handoverEquipments: HandoverEquipment[];
  activeRenovationSession: RenovationSession | null;
  renovationSessions: RenovationSession[];
}
```

---

## 10. Gợi ý UI

1. **Hai module upload** tách đợt 1 / đợt 2; template tải từ `docs/`.
2. **Dry-run bắt buộc** — hiển thị bảng `errors` theo sheet + dòng.
3. **Tab chi tiết nhà:**
   - TB bàn giao (`handoverEquipments`)
   - TB đang dùng (`equipments` filter `currentEffective`)
   - Lịch sử cải tạo (`renovationSessions`)
4. **Badge phiên bản:** `v1`, `v2` trên cả session và equipment (`renovationVersionLabel`).
5. **THAY_THẾ vs THÊM MỚI:** nếu sau này có form nhập tay (không Excel), gửi `importAction` trong `AssignEquipmentRequest` — enum `THEM_MOI` \| `THAY_THE`.
6. Endpoint cũ `POST /import/onboarding-excel` — **không dùng** trên FE mới.

---

## Phụ lục — Endpoint cũ

| Endpoint | Ghi chú |
|----------|---------|
| `POST /import/onboarding-excel` | 1 file 3 sheet, giữ tương thích BE |

---

*Tài liệu BE chi tiết: `docs/IMPORT_EXCEL_2_DOT.md`*
