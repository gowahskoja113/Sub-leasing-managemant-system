# Import Excel 2 đợt — Quy trình & Đặc tả (SLMS2026)

> **Một file duy nhất** cho FE / BE / Admin — copy chat khác để code.  
> **Ngày chốt:** 2026-06-24  
> **File mẫu:**  
> - Đợt 1: [`SLMS2026_import_dot1_khoi_tao.xlsx`](./SLMS2026_import_dot1_khoi_tao.xlsx)  
> - Đợt 2: [`SLMS2026_import_dot2_cai_tao.xlsx`](./SLMS2026_import_dot2_cai_tao.xlsx)  
> **Tái tạo mẫu:** `node scripts/generate-import-excel-v2.mjs`

---

## 1. Luồng tổng thể

```
┌─────────────────────────────────────────────────────────────┐
│  ĐỢT 1 — Khởi tạo nhà                                       │
│  File: SLMS2026_import_dot1_khoi_tao.xlsx                   │
│  API:  POST /api/v1/import/lease-excel?dryRun=             │
│  → Property + HĐ thuê + Phòng + TB bàn giao (chỉ hiển thị)   │
│  → KHÔNG completeRenovation, KHÔNG gửi Host                 │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Upload ảnh ZIP (giữ endpoint cũ)                            │
│  API:  POST /api/v1/import/property-images-zip?dryRun=      │
└──────────────────────────┬──────────────────────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  ĐỢT 2 — Cấu hình khai thác                                 │
│  File: SLMS2026_import_dot2_cai_tao.xlsx                    │
│  API:  POST /api/v1/import/renovation-excel?dryRun=         │
│  → Cải tạo + TB mua mới (bảo hành)                          │
│  → completeRenovation → auto định giá → submit Host         │
│  → PENDING_HOST_REVIEW (bỏ bước Admin thủ công)             │
└─────────────────────────────────────────────────────────────┘

Rollback: DELETE /api/v1/import/onboarding-excel/contracts/{contractCode}
```

| Đợt | Module UI | API |
|-----|-----------|-----|
| 1 | Khởi tạo nhà | `POST /api/v1/import/lease-excel?dryRun=` |
| — | Upload ảnh | `POST /api/v1/import/property-images-zip?dryRun=` |
| 2 | Cấu hình khai thác | `POST /api/v1/import/renovation-excel?dryRun=` |

**Response chung:** `BulkImportResponse` + `BulkImportError[]` (sheet, row, contractCode, field, message).  
**dryRun=true:** chỉ validate, không ghi DB, không gửi Host.

---

## 2. Thay đổi so với import cũ (1 file 3 sheet)

| Hạng mục | Cũ | Mới |
|----------|-----|-----|
| Số file | 1 file, 3 sheet bắt buộc | **2 file**, sheet đọc theo từng đợt |
| TB bàn giao | Sheet 3 chung, gán phòng vận hành | Sheet riêng đợt 1 — **chỉ hiển thị** |
| TB mua mới | Cùng sheet 3 (`PURCHASED`) | Sheet riêng đợt 2 + **bảo hành** |
| Gửi Host | Admin bấm định giá thủ công | **Tự động** sau đợt 2 |

### Cột đã BỎ (không còn trong Excel, không đọc code)

- `Hình thức thuê`
- `Có cải tạo không`
- `Tỷ lệ chi phí dự phòng (%)`
- `Xã/Phường` — chỉ dùng **Quận/Huyện + Tỉnh/Thành phố** (Zone 2 cấp)

---

## 3. File Đợt 1 — `SLMS2026_import_dot1_khoi_tao.xlsx`

### 3.1. Sheet

| Sheet | BE đọc | Ghi chú |
|-------|--------|---------|
| `0. Huong_Dan` | Không | Hướng dẫn |
| `0. Ma_Tran_Trường_Hop` | Không | 6 trường hợp demo |
| `0. Danh_Muc_Tham_Khao` | Không | Catalog TB + mã khu vực |
| **`1. Hop_Dong_Thue`** | **Bắt buộc** | HĐ thuê + nhà |
| **`2. Thiet_Bi_Ban_Giao`** | Tùy chọn | TB chủ bàn giao — chỉ hiển thị |
| **`3. Danh_Sach_Phong`** | Bắt buộc nếu **theo phòng** | Đủ N phòng = `Tổng số phòng` |

### 3.2. `1. Hop_Dong_Thue` — 13 cột

| Cột | Bắt buộc | Quy tắc |
|-----|:--------:|---------|
| Mã hợp đồng | Có | Khóa duy nhất |
| Tên tòa nhà | Có | |
| Địa chỉ chi tiết | Có | Số nhà, đường — không gộp xã/phường |
| Quận/Huyện | Có | Zone level 2 |
| Tỉnh/Thành phố | Có | Zone level 1 |
| Diện tích (m²) | Có | > 0 |
| Tổng số tầng | Có | > 0 |
| Tổng số phòng | Có | > 0 |
| Tên chủ nhà | Có | |
| Tổng tiền thuê | Có | > 0 |
| Ngày bắt đầu | Có | `YYYY-MM-DD` |
| Ngày kết thúc | Có | Sau ngày bắt đầu |
| Mô tả chi tiết | Có | Demo: tag `[NGUYEN_CAN]` / `[THEO_PHONG]` |

### 3.3. `2. Thiet_Bi_Ban_Giao`

| Cột | Bắt buộc | Quy tắc |
|-----|:--------:|---------|
| Mã hợp đồng thuê | Có | Khớp sheet 1 |
| Tên thiết bị | Có | Khớp `EquipmentCatalog.name` |
| Mô tả chi tiết | Không | |
| Số phòng | Một trong hai | Số phòng **hoặc** Khu vực chung |
| Khu vực chung | Một trong hai | `LIVING_ROOM`, `KITCHEN`, `BATHROOM`, `BALCONY`, `GARAGE`, `OTHER` |
| Trạng thái thiết bị | Có | `NEW`, `GOOD`, `DAMAGED`, `BROKEN` |
| Số lượng | Có | > 0 |
| Ghi chú | Không | |

**Rule:** Lưu bảng `handover_equipment` (hoặc tương đương). **Không** `assignEquipment`, **không** khấu hao. FE hiển thị ở chi tiết nhà (`handoverEquipments`).

### 3.4. `3. Danh_Sach_Phong` — chỉ nhà **theo phòng**

| Cột | Bắt buộc | Quy tắc |
|-----|:--------:|---------|
| Mã hợp đồng thuê | Có | |
| Số phòng | Có | Duy nhất/HĐ. Demo: `101`…`110` |
| Tầng | Có | 1…`Tổng số tầng` |
| Diện tích phòng (m²) | Có | > 0 |
| Ghi chú | Không | |

**Rule:** Số dòng **= `Tổng số phòng`**. BE tạo `Room` ở **đợt 1**. Thiếu phòng → lỗi *"Phải tạo đủ N phòng chi tiết"* khi gửi Host.  
Nhà **nguyên căn**: không có sheet này.

### 3.5. Sau đợt 1

- Trạng thái chờ đợt 2 (`DRAFT` / `UNDER_RENOVATION` — BE chọn theo `isOnboardingEditable()`)
- **Nguyên căn vs theo phòng:** BE quyết ở đợt 1 (demo: prefix `HD-WH-*` / `HD-ROOM-*` hoặc tag mô tả)

---

## 4. File Đợt 2 — `SLMS2026_import_dot2_cai_tao.xlsx`

### 4.1. Sheet

| Sheet | BE đọc | Ghi chú |
|-------|--------|---------|
| `0. Huong_Dan` | Không | |
| `0. Ma_Tran_Trường_Hop` | Không | |
| `0. Danh_Muc_Tham_Khao` | Không | Danh mục cải tạo + catalog + khu vực |
| **`1. Hop_Dong_Cai_Tao`** | **Bắt buộc** | Chi phí cải tạo |
| **`2. Thiet_Bi_Mua_Moi`** | Tùy chọn | TB mua + bảo hành |

Mã HĐ **phải đã có ở đợt 1** — không có → lỗi *"Chưa khởi tạo tòa nhà cho mã HĐ này"*.

### 4.2. `1. Hop_Dong_Cai_Tao`

| Cột | Bắt buộc | Quy tắc |
|-----|:--------:|---------|
| Mã hợp đồng thuê | Có | |
| Mã danh mục cải tạo | Có | `PAINTING`, `PLUMBING`, `FLOORING`, `FURNITURE`, `EQUIPMENT`, `STRUCTURAL`, `OTHER` |
| Tên danh mục (Gợi ý) | Không | |
| Chi phí cải tạo (VNĐ) | Có | > 0 |
| Ghi chú chi tiết | Không | |

### 4.3. `2. Thiet_Bi_Mua_Moi`

| Cột | Bắt buộc | Quy tắc |
|-----|:--------:|---------|
| Mã hợp đồng thuê | Có | |
| Số phòng | Một trong hai | |
| Khu vực chung | Một trong hai | |
| Tên Catalog thiết bị | Có | Khớp catalog DB |
| Trạng thái thiết bị | Có | `NEW`, `GOOD`, `DAMAGED`, `BROKEN` |
| Số lượng | Có | > 0 |
| Đơn giá (VNĐ) | Có | > 0 |
| Số tháng bảo hành | Có | > 0 |
| Ngày bắt đầu bảo hành | Có | `YYYY-MM-DD` |
| Ngày hết bảo hành | Có | Sau ngày bắt đầu |
| Ghi chú lắp đặt | Không | |

**Theo phòng + có mua TB:** mỗi phòng trong sheet 3 đợt 1 nên có ≥1 dòng TB (cột `Số phòng` khớp). Demo `HD-ROOM-RENO-FURN`: 10 dòng phòng `101`–`110`.

### 4.4. Sau đợt 2

```
addRenovationLine → gán TB PURCHASED → completeRenovation
  → depreciation/calculate → submit-to-host → PENDING_HOST_REVIEW
```

---

## 5. Ma trận trường hợp (không cần cột Excel riêng)

Suy ra từ **có/không dòng** theo `Mã hợp đồng thuê`:

### Cải tạo × TB mua (đợt 2)

| Sheet cải tạo | Sheet TB mua | Ý nghĩa | Mã demo |
|:-------------:|:------------:|---------|---------|
| Có | Có | Cải tạo + mua nội thất | `HD-WH-RENO-FURN`, `HD-ROOM-RENO-FURN` |
| Có | Không | Chỉ cải tạo | `HD-WH-RENO-NOFURN`, `HD-ROOM-RENO-NOFURN` |
| Không | Không | **Không import đợt 2** | `HD-WH-NORENO-FURN`, `HD-WH-NORENO-NOFURN` |

### TB chủ bàn giao (chỉ đợt 1)

| Trường hợp | Đợt 1 TB bàn giao | Đợt 2 |
|------------|:-----------------:|:-----:|
| Không cải tạo, có TB chủ | Có dòng sheet 2 | Không có dòng |
| Trống hoàn toàn | Không | Không |

### Loại hình

| Loại | Nghiệp vụ | Mã demo |
|------|-----------|---------|
| **Nguyên căn** | 4 tổ hợp cải tạo±, mua TB±, bàn giao± | `HD-WH-*` |
| **Theo phòng** | Mặc định có cải tạo khi vào đợt 2; sheet 3 **đủ N phòng** | `HD-ROOM-*` |

> `HD-WH-NORENO-*`: chưa có flow đợt 2 — BE cần luồng riêng gửi Host.

---

## 6. Quy tắc đọc file

- Dòng **1** = header (tên cột khớp chính xác, `trim`)
- Dữ liệu từ dòng **2**; dòng trống bỏ qua
- Tên sheet phải đúng
- `.xlsx` / `.xls`
- Trùng mã HĐ / địa chỉ → `SKIPPED`
- **Trang trí OK** (màu, bảng Google Sheets, dropdown) nếu **không** đổi tên sheet/cột, **không** chèn dòng trên header

---

## 7. Việc BE cần code

- [ ] Tách reader — không bắt buộc đủ 3 sheet như `ExcelOnboardingWorkbookReader` cũ
- [ ] `POST /api/v1/import/lease-excel` — sheet 1 + 3 (nếu theo phòng) + 2 (tùy chọn)
- [ ] `POST /api/v1/import/renovation-excel` — sheet 1 + 2 (tùy chọn); auto submit Host
- [ ] Entity + API `handoverEquipments` (TB bàn giao, display-only)
- [ ] Migration cột bảo hành trên `equipments` (tháng, ngày bắt đầu, ngày hết)
- [ ] Bỏ đọc `ward`, `Hình thức thuê`, `Có cải tạo`, `Tỷ lệ dự phòng`
- [ ] `wholeHouse` / `hasRenovation`: rule BE (tag/prefix demo + suy từ dòng đợt 2)
- [ ] Tạo phòng từ sheet `3. Danh_Sach_Phong` ở đợt 1
- [ ] Luồng `HD-WH-NORENO-*` gửi Host không qua đợt 2
- [ ] `dryRun` + `BulkImportResponse` / `BulkImportError`
- [ ] Giữ ZIP ảnh + rollback theo mã HĐ

---

## 8. Việc FE cần code

- [ ] 2 module upload riêng + `dryRun` + bảng kết quả/lỗi
- [ ] Template tải 2 file trong `docs/`
- [ ] Bỏ form 3 cột đã xóa
- [ ] Bỏ/ẩn "Định giá & Phê duyệt" cho nhà import đợt 2
- [ ] Chi tiết nhà: TB bàn giao (đợt 1) vs TB vận hành (đợt 2)
- [ ] Chờ BE chốt luồng `NORENO` gửi Host

---

## 9. Dữ liệu demo trong file mẫu

| Mã HĐ | Loại | Phòng | Đợt 1 TB bàn giao | Đợt 2 cải tạo | Đợt 2 mua TB |
|-------|------|:-----:|:-----------------:|:------------:|:------------:|
| `HD-WH-RENO-FURN` | Nguyên căn | — | Có | Có | Có |
| `HD-WH-NORENO-FURN` | Nguyên căn | — | Có | — | — |
| `HD-WH-RENO-NOFURN` | Nguyên căn | — | Không | Có | Không |
| `HD-WH-NORENO-NOFURN` | Nguyên căn | — | Không | — | — |
| `HD-ROOM-RENO-FURN` | Theo phòng | 10 (`101`–`110`) | 10 dòng | Có | 10 dòng |
| `HD-ROOM-RENO-NOFURN` | Theo phòng | 10 (`101`–`110`) | Không | Có | Không |

---

*Tài liệu đồng bộ `scripts/generate-import-excel-v2.mjs`. Đổi cột/sheet → cập nhật script + file MD này.*
