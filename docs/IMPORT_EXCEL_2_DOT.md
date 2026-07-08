# Import Excel 2 đợt — Quy trình & Đặc tả (SLMS2026)

> **Một file duy nhất** cho FE / BE / Admin — copy chat khác để code.  
> **Ngày chốt:** 2026-06-25  
> **File mẫu:**  
> - Đợt 1: [`SLMS2026_import_dot1_khoi_tao.xlsx`](./SLMS2026_import_dot1_khoi_tao.xlsx)  
> - Đợt 2: [`SLMS2026_import_dot2_cai_tao.xlsx`](./SLMS2026_import_dot2_cai_tao.xlsx)  
> - Cải tạo bổ sung: [`SLMS2026_import_cai_tao_bo_sung.xlsx`](./SLMS2026_import_cai_tao_bo_sung.xlsx)  
> **Tái tạo mẫu:** `node scripts/generate-import-excel-v2.mjs`

---

## 1. Luồng tổng thể

```
┌─────────────────────────────────────────────────────────────┐
│  ĐỢT 1 — Khởi tạo nhà (HĐ thuê từ chủ)                      │
│  File: SLMS2026_import_dot1_khoi_tao.xlsx                   │
│  API:  POST /api/v1/import/lease-excel?dryRun=             │
│  → Property + HĐ thuê — LUÔN nguyên căn                     │
│  → TB bàn giao (chỉ hiển thị, không gắn phòng vận hành)     │
│  → KHÔNG tạo phòng khai thác, KHÔNG gửi Host (trừ NORENO)   │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Upload ảnh ZIP                                              │
│  API:  POST /api/v1/import/property-images-zip?dryRun=      │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  ĐỢT 2 — Cấu hình khai thác + cải tạo                       │
│  File: SLMS2026_import_dot2_cai_tao.xlsx                    │
│  API:  POST /api/v1/import/renovation-excel?dryRun=         │
│  → Quyết định NGUYEN_CAN / THEO_PHONG + tạo phòng (nếu có)  │
│  → Cải tạo + TB mua mới (bảo hành)                          │
│  → completeRenovation → auto định giá → submit Host         │
└─────────────────────────────────────────────────────────────┘

Rollback: DELETE /api/v1/import/onboarding-excel/contracts/{contractCode}
```

| Đợt | Module UI | API |
|-----|-----------|-----|
| 1 | Khởi tạo nhà | `POST /api/v1/import/lease-excel?dryRun=` |
| — | Upload ảnh | `POST /api/v1/import/property-images-zip?dryRun=` |
| 2 | Cấu hình khai thác | `POST /api/v1/import/renovation-excel?dryRun=` |
| 2+ | Cải tạo bổ sung | `POST /api/v1/import/renovation-supplement-excel?dryRun=` |

**Cải tạo bổ sung (lần 2, 3…):** nhà đã `ACTIVE` → gọi `POST /api/v1/properties/{id}/start-renovation` → import file `SLMS2026_import_cai_tao_bo_sung.xlsx` (2 sheet: cải tạo + TB mua). Không gửi Host lại.

---

## 2. Nguyên tắc nghiệp vụ

| Giai đoạn | Quy tắc |
|-----------|---------|
| **Đợt 1** | Thuê nhà gốc từ chủ → luôn import **nguyên căn** (`wholeHouse=true`) |
| **Đợt 1** | `Tổng số phòng` = đặc tính vật lý căn nhà thuê, không phải số phòng khai thác |
| **Đợt 1** | TB chủ bàn giao: ghi nhận + hiển thị, **không** gán phòng vận hành |
| **Đợt 2** | Quyết định **NGUYEN_CAN** hay **THEO_PHONG** (sheet cấu hình) |
| **Đợt 2** | THEO_PHONG: tạo đủ phòng khai thác + gán TB mua vào phòng (nếu có) |
| **Đợt 2** | TB mua mới: bắt buộc thông tin bảo hành (tháng + ngày BĐ/KT) |

---

## 3. File Đợt 1 — `SLMS2026_import_dot1_khoi_tao.xlsx`

### 3.1. Sheet

| Sheet | BE đọc | Ghi chú |
|-------|--------|---------|
| `0. Huong_Dan` | Không | Hướng dẫn |
| `0. Ma_Tran_Trường_Hop` | Không | 5 trường hợp demo |
| `0. Danh_Muc_Tham_Khao` | Không | Catalog TB |
| **`1. Hop_Dong_Thue`** | **Bắt buộc** | HĐ thuê + nhà (luôn nguyên căn) |
| **`2. Thiet_Bi_Ban_Giao`** | Tùy chọn | TB chủ bàn giao — chỉ hiển thị |

**Không còn** sheet `3. Danh_Sach_Phong` ở đợt 1.

### 3.2. `1. Hop_Dong_Thue` — 13 cột

| Cột | Bắt buộc | Quy tắc |
|-----|:--------:|---------|
| Mã hợp đồng | Có | Khóa duy nhất |
| Tên tòa nhà | Có | |
| Địa chỉ chi tiết | Có | Số nhà, đường |
| Quận/Huyện | Có | Zone level 2 |
| Tỉnh/Thành phố | Có | Zone level 1 |
| Diện tích (m²) | Có | > 0 |
| Tổng số tầng | Có | > 0 |
| Tổng số phòng | Có | > 0 — đặc tính vật lý |
| Tên chủ nhà | Có | |
| Tổng tiền thuê | Có | > 0 |
| Ngày bắt đầu | Có | `YYYY-MM-DD` |
| Ngày kết thúc | Có | Sau ngày bắt đầu |
| Mô tả chi tiết | Có | |

### 3.3. `2. Thiet_Bi_Ban_Giao`

| Cột | Bắt buộc | Quy tắc |
|-----|:--------:|---------|
| Mã hợp đồng thuê | Có | Khớp sheet 1 |
| Tên thiết bị | Có | Khớp `EquipmentCatalog.name` |
| Mô tả chi tiết | Không | |
| Mô tả vị trí | Không | Text tự do (tầng, khu vực…) |
| Trạng thái thiết bị | Có | `NEW`, `GOOD`, `DAMAGED`, `BROKEN` |
| Số lượng | Có | > 0 |
| Ghi chú | Không | |

**Rule:** Lưu `handover_equipments`. Không `assignEquipment`, không khấu hao.

### 3.4. Sau đợt 1

- Có cải tạo (`HD-*-RENO-*`, không NORENO): `UNDER_RENOVATION`, chờ đợt 2
- Không cải tạo (`HD-*-NORENO-*`): auto `submitToHost` → `PENDING_HOST_REVIEW`

---

## 4. File Đợt 2 — `SLMS2026_import_dot2_cai_tao.xlsx`

### 4.1. Sheet

| Sheet | BE đọc | Ghi chú |
|-------|--------|---------|
| `0. Huong_Dan` | Không | |
| `0. Ma_Tran_Trường_Hop` | Không | |
| `0. Danh_Muc_Tham_Khao` | Không | |
| **`1. Cau_Hinh_Khai_Thac`** | **Bắt buộc** | 1 dòng / mã HĐ |
| **`2. Danh_Sach_Phong`** | Bắt buộc nếu `THEO_PHONG` | Đủ N phòng = `Số phòng khai thác` |
| **`3. Hop_Dong_Cai_Tao`** | Tùy chọn | Chi phí cải tạo |
| **`4. Thiet_Bi_Mua_Moi`** | Tùy chọn | TB mua + bảo hành |

Mã HĐ **phải đã import đợt 1**, Property `UNDER_RENOVATION`.

### 4.2. `1. Cau_Hinh_Khai_Thac`

| Cột | Bắt buộc | Quy tắc |
|-----|:--------:|---------|
| Mã hợp đồng thuê | Có | Khớp đợt 1 |
| Hình thức khai thác | Có | `NGUYEN_CAN` hoặc `THEO_PHONG` |
| Số phòng khai thác | Có nếu THEO_PHONG | > 0, khớp số dòng sheet 2 |

### 4.3. `2. Danh_Sach_Phong` — chỉ `THEO_PHONG`

| Cột | Bắt buộc | Quy tắc |
|-----|:--------:|---------|
| Mã hợp đồng thuê | Có | |
| Số phòng | Có | Duy nhất/HĐ |
| Tầng | Có | 1…`Tổng số tầng` (từ Property) |
| Diện tích phòng (m²) | Có | > 0 |
| Ghi chú | Không | |

### 4.4. `3. Hop_Dong_Cai_Tao`

| Cột | Bắt buộc | Quy tắc |
|-----|:--------:|---------|
| Mã hợp đồng thuê | Có | |
| Mã danh mục cải tạo | Có | `PAINTING`, `PLUMBING`, … |
| Tên danh mục (Gợi ý) | Không | |
| Chi phí cải tạo (VNĐ) | Có | > 0 |
| Ghi chú chi tiết | Không | |

### 4.5. `4. Thiet_Bi_Mua_Moi`

| Cột | Bắt buộc | Quy tắc |
|-----|:--------:|---------|
| Mã hợp đồng thuê | Có | |
| Số phòng | Một trong hai | Bắt buộc nếu THEO_PHONG có TB |
| Khu vực chung | Một trong hai | Dùng khi NGUYEN_CAN |
| Tên Catalog thiết bị | Có | |
| Trạng thái thiết bị | Có | `NEW`, `GOOD` |
| Số lượng | Có | > 0 |
| Đơn giá (VNĐ) | Có | > 0 |
| Số tháng bảo hành | Có | > 0 |
| Ngày bắt đầu bảo hành | Có | `YYYY-MM-DD` |
| Ngày hết bảo hành | Có | Sau ngày bắt đầu |
| Ghi chú lắp đặt | Không | |

### 4.6. Sau đợt 2

```
Cấu hình khai thác → tạo phòng (nếu THEO_PHONG)
  → addRenovationLine → gán TB PURCHASED → completeRenovation
  → depreciation/calculate → submit-to-host → PENDING_HOST_REVIEW
```

---

## 5. Ma trận trường hợp demo

| Mã HĐ | Đợt 1 TB bàn giao | Đợt 2 hình thức | Đợt 2 cải tạo | Đợt 2 mua TB |
|-------|:-----------------:|:---------------:|:------------:|:------------:|
| `HD-WH-RENO-FURN` | Có | NGUYEN_CAN | Có | Có |
| `HD-WH-NORENO-FURN` | Có | — | — | — |
| `HD-WH-RENO-NOFURN` | Không | NGUYEN_CAN | Có | Không |
| `HD-WH-NORENO-NOFURN` | Không | — | — | — |
| `HD-WH-RENO-SPLIT` | Có | THEO_PHONG (5 phòng) | Có | Có |

> `HD-*-NORENO-*`: không có dòng trong file đợt 2 — gửi Host sau đợt 1.

---

## 6. File cải tạo bổ sung — `SLMS2026_import_cai_tao_bo_sung.xlsx`

Dùng khi nhà **đã vận hành** (`ACTIVE`), sau `start-renovation`.

| Sheet | BE đọc |
|-------|--------|
| `1. Hop_Dong_Cai_Tao` | Tùy chọn (cùng cột như sheet 3 đợt 2) |
| `2. Thiet_Bi_Mua_Moi` | Tùy chọn (cùng cột như sheet 4 đợt 2) |

**Không có** sheet cấu hình khai thác / danh sách phòng.

**Tiên quyết:** `POST /properties/{id}/start-renovation` → `UNDER_RENOVATION`, session ≥ 2.

**Sau import:** `completeRenovation` → `ACTIVE` (không `submitToHost`). TB mua **cộng dồn**, không xóa manifest cũ.

---

*Tài liệu đồng bộ `scripts/generate-import-excel-v2.mjs`.*
