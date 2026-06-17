# Kịch bản Test — Import Excel Onboarding (SLMS2026)

API: `POST /api/v1/import/onboarding-excel`  
File mẫu chuẩn: `docs/SLMS2026_v1.xlsx`  
Tài liệu kỹ thuật: `docs/excel-import-backend.md`

---

## 0. Chuẩn bị môi trường (Preconditions)

| # | Điều kiện | Cách kiểm tra |
|---|-----------|---------------|
| P0 | App chạy, DB PostgreSQL kết nối OK | Health / login thành công |
| P1 | Có tài khoản **ADMIN** + JWT token | `POST /api/v1/auth/login` |
| P2 | `MasterDataSeeder` đã chạy (catalog + mã cải tạo) | `GET /renovation-categories`, `GET /equipment-catalog` |
| P3 | Zone **Hà Nội** (level 1 — Tỉnh/Thành phố) tồn tại | `GET /api/v1/zones` hoặc DB |
| P4 | Zone **Cầu Giấy** (level 2 — Quận/Huyện, parent = Hà Nội) tồn tại | Tra children của Hà Nội |
| P5 | Mã HĐ `HD-2026-VILLACG` **chưa** có trong DB | `inbound_contracts.contract_code` |

**Tạo Zone nếu thiếu (ADMIN):**

```json
POST /api/v1/zones
{ "name": "Hà Nội", "level": 1, "description": "Test import" }

POST /api/v1/zones
{ "name": "Cầu Giấy", "level": 2, "parentId": "<uuid-hà-nội>" }
```

**Lấy token:**

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"<admin>","password":"<password>"}'
```

---

## 1. Test Happy Path

### TC-01 — Dry-run file mẫu hợp lệ

| Mục | Nội dung |
|-----|----------|
| **Mục tiêu** | Validate file không ghi DB |
| **Input** | `SLMS2026_v1.xlsx`, `dryRun=true` |
| **Kỳ vọng HTTP** | `200` |
| **Kỳ vọng body** | `dryRun=true`, `contractsProcessed=1`, `renovationLinesImported=2`, `equipmentRowsImported=3`, `results=[]` |
| **Kỳ vọng DB** | Không thay đổi (đếm property / inbound_contract trước và sau giống nhau) |

```bash
curl -X POST "http://localhost:8080/api/v1/import/onboarding-excel?dryRun=true" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@docs/SLMS2026_v1.xlsx"
```

---

### TC-02 — Import thật 1 hợp đồng (file mẫu)

| Mục | Nội dung |
|-----|----------|
| **Mục tiêu** | Import end-to-end 1 căn nhà |
| **Input** | `SLMS2026_v1.xlsx`, `dryRun=false` |
| **Kỳ vọng HTTP** | `200` |
| **Kỳ vọng body** | `contractsProcessed=1`, `finalStatus=RENOVATION_COMPLETED`, có `propertyId` |

**Kiểm tra DB / API sau import:**

| Đối tượng | Kỳ vọng |
|-----------|---------|
| `properties` | 1 record mới, `property_name=Vinhomes Villa Cầu Giấy`, `whole_house=true`, `has_renovation=true`, `status=RENOVATION_COMPLETED` |
| `inbound_contracts` | `contract_code=HD-2026-VILLACG`, `total_rent_amount=600000000` |
| `renovation_lines` | 2 dòng (PAINTING, FLOORING) |
| `rooms` | Phòng `101`, `201` (DRAFT hoặc theo flow whole house) |
| `equipment_manifests` | Gom nhóm theo catalog/source/status/price |
| `equipments` | 3 thiết bị (tổng quantity các dòng sheet 3) |
| Equipment `LIVING_ROOM` | `house_area=LIVING_ROOM`, `room_id` null |
| Equipment phòng `101` | `room_id` trỏ phòng 101 |

```bash
GET /api/v1/properties/{propertyId}/onboarding-summary
GET /api/v1/properties/{propertyId}/renovation-lines
GET /api/v1/properties/{propertyId}/equipment-manifest
GET /api/v1/properties/{propertyId}/rooms   # nếu có endpoint
```

---

### TC-03 — Import 2 hợp đồng trong 1 file

| Mục | Nội dung |
|-----|----------|
| **Chuẩn bị** | Copy dòng sheet 1 → dòng 3 với mã `HD-2026-APT01`; thêm dòng sheet 2/3 tương ứng |
| **Kỳ vọng** | `contractsProcessed=2`, 2 `propertyId` khác nhau |
| **Kiểm tra** | Mỗi mã HĐ chỉ nhận đúng dòng renovation/equipment của nó |

---

## 2. Test Validation — Sheet 1

Mỗi case: sửa **1 lỗi** trên bản copy file → `dryRun=true` → kỳ vọng `400`.

| ID | Thao tác sửa file | Field lỗi | Message kỳ vọng (gần đúng) |
|----|-------------------|-----------|----------------------------|
| TC-S1-01 | Xóa `Mã hợp đồng` dòng 2 | Mã hợp đồng | không được để trống |
| TC-S1-02 | 2 dòng cùng `HD-2026-VILLACG` | Mã hợp đồng | trùng trong file |
| TC-S1-03 | Import file mẫu lần 2 (mã đã tồn tại DB) | Mã hợp đồng | đã tồn tại trong hệ thống |
| TC-S1-04 | `Ngày kết thúc` = `2026-06-01`, bắt đầu = `2031-05-31` | Ngày kết thúc | phải sau ngày bắt đầu |
| TC-S1-05 | `Hình thức thuê` = `RENT_ALL` | Hình thức thuê | WHOLE_HOUSE hoặc INDIVIDUAL_ROOM |
| TC-S1-06 | `Có cải tạo không` = `YES` | Có cải tạo không | TRUE hoặc FALSE |
| TC-S1-07 | `Quận/Huyện` = `Quận XYZ` | Zone | Không tìm thấy Quận/Huyện |
| TC-S1-08 | `Tổng tiền thuê` = `0` | Tổng tiền thuê | phải lớn hơn 0 |
| TC-S1-09 | `Diện tích (m²)` = `-1` | Diện tích | phải lớn hơn 0 |
| TC-S1-10 | `Tỷ lệ chi phí dự phòng` = `150` | Tỷ lệ... | từ 0 đến 100 |

---

## 3. Test Validation — Sheet 2

| ID | Thao tác | Kỳ vọng |
|----|----------|---------|
| TC-S2-01 | `Mã danh mục cải tạo` = `DM99` | Không tìm thấy mã danh mục |
| TC-S2-02 | `Mã hợp đồng thuê` = `HD-KHONG-TON-TAI` | Không tìm thấy mã ở sheet 1 |
| TC-S2-03 | `Chi phí cải tạo` = `0` | Chi phí phải lớn hơn 0 |
| TC-S2-04 | Sheet 1 `Có cải tạo không=FALSE`, giữ dòng sheet 2 | Hợp đồng khai báo FALSE nhưng sheet 2 vẫn có chi phí |
| TC-S2-05 | Sheet 1 `TRUE`, xóa hết dòng sheet 2 | TRUE nhưng sheet 2 không có dòng cải tạo |

---

## 4. Test Validation — Sheet 3

| ID | Thao tác | Kỳ vọng |
|----|----------|---------|
| TC-S3-01 | Điền cả `Số phòng` và `Khu vực chung` | Chỉ chọn một trong hai |
| TC-S3-02 | Bỏ trống cả `Số phòng` và `Khu vực chung` | Phải điền một trong hai |
| TC-S3-03 | `Tên Catalog` = `Bình nóng lạnh` | Không tìm thấy catalog (DB là `Nóng lạnh`) |
| TC-S3-04 | `Nguồn gốc` = `PURCHASED`, `Đơn giá` = `0` | PURCHASED phải có đơn giá > 0 |
| TC-S3-05 | `Nguồn gốc` = `INITIAL_HANDOVER`, `Đơn giá` = `5000000` | HANDOVER phải có đơn giá = 0 |
| TC-S3-06 | `Trạng thái` = `BROKEN` | Chỉ chấp nhận NEW hoặc GOOD |
| TC-S3-07 | `Khu vực chung` = `GARDEN` | Giá trị enum không hợp lệ |
| TC-S3-08 | `Số lượng` = `0` | Số lượng phải nguyên dương |

---

## 5. Test File / Parser

| ID | Input | Kỳ vọng |
|----|-------|---------|
| TC-F-01 | Upload file `.pdf` | 422 / BusinessException: chỉ hỗ trợ xlsx |
| TC-F-02 | Upload file rỗng | File không được để trống |
| TC-F-03 | Xóa sheet `1. Hop_Dong_Thue` | Thiếu sheet bắt buộc |
| TC-F-04 | Đổi tên sheet 1 thành `Hop_Dong_Thue` (bỏ prefix) | Thiếu sheet bắt buộc |
| TC-F-05 | Xóa cột header `Mã hợp đồng` | Thiếu cột bắt buộc |
| TC-F-06 | Sheet 1 chỉ có header, không có dòng data | Không có dòng hợp đồng nào để import |

---

## 6. Test Bảo mật & Quyền

| ID | Thao tác | Kỳ vọng |
|----|----------|---------|
| TC-SEC-01 | Không gửi `Authorization` | `401` |
| TC-SEC-02 | Token role **MANAGER** / **OWNER** | `403` |
| TC-SEC-03 | Token **ADMIN** hợp lệ | `200` (với file hợp lệ) |

---

## 7. Test Transaction (Rollback)

| ID | Mục tiêu | Cách làm | Kỳ vọng |
|----|----------|----------|---------|
| TC-TX-01 | Lỗi giữa chừng rollback toàn bộ | File 2 HĐ: HĐ1 hợp lệ, HĐ2 lỗi Zone → **không dùng dryRun** | `400`, **không** có record mới nào trong DB (kể cả HĐ1) |
| TC-TX-02 | Dry-run không rollback vì không ghi | `dryRun=true` với file lỗi | `400`, DB không đổi |
| TC-TX-03 | Dry-run file OK | `dryRun=true` | `200`, DB không đổi |

> Lưu ý: Toàn bộ `importWorkbook` nằm trong **một `@Transactional`** — một lỗi runtime sau khi validate (hiếm) cũng rollback cả batch.

---

## 8. Test Nghiệp vụ sau Import

Sau **TC-02** thành công, kiểm tra các bước tiếp theo **ngoài** import:

| ID | API | Kỳ vọng |
|----|-----|---------|
| TC-POST-01 | `POST /properties/{id}/submit-to-host` | `200`, status → `PENDING_HOST_REVIEW` |
| TC-POST-02 | `POST /properties/{id}/host-confirm` | Cần body `contingencyPercent`; có depreciation result |
| TC-POST-03 | Gán operation manager | status → `ACTIVE` |

---

## 9. Test Case đặc biệt theo file mẫu

| Dòng sheet 3 | Nội dung | Điểm cần verify |
|--------------|----------|-----------------|
| R2 | Phòng `101`, HANDOVER, GOOD, giá 0 | `equipments.price = 0`, manifest bàn giao |
| R3 | Phòng `201`, PURCHASED, NEW, 7.500.000 | Giá đúng, phòng 201 tồn tại |
| R4 | `LIVING_ROOM`, PURCHASED, NEW | `house_area=LIVING_ROOM`, không cần `room_id` |

---

## 10. Ma trận ưu tiên

| Ưu tiên | Test ID | Lý do |
|---------|---------|-------|
| **P0** | TC-01, TC-02, TC-S2-04, TC-S3-01, TC-S3-04, TC-SEC-03 | Luồng chính + rule nghiệp vụ cốt lõi |
| **P1** | TC-03, TC-S1-03, TC-S2-01, TC-S3-03, TC-TX-01 | Multi-record, master data, rollback |
| **P2** | TC-F-*, TC-SEC-01/02, TC-POST-* | Parser edge, security, post-import |

---

## 11. Checklist nhanh sau mỗi lần test import thật

- [ ] Response `contractsProcessed` khớp số dòng sheet 1
- [ ] `renovationLinesImported` khớp số dòng sheet 2
- [ ] `equipmentRowsImported` khớp số dòng sheet 3
- [ ] `finalStatus` = `RENOVATION_COMPLETED`
- [ ] Không trùng `contract_code` khi import lại cùng file
- [ ] Onboarding summary hiển thị đủ HĐ + cải tạo + manifest

---

## 12. Dọn dữ liệu test (Cleanup)

Sau test import thật, xóa hoặc disable property test để chạy lại TC-02:

```sql
-- Chỉ dùng trên DB dev — điều chỉnh theo schema thực tế
DELETE FROM equipments WHERE property_id IN (SELECT id FROM properties WHERE property_name LIKE '%Villa%');
-- ... hoặc disable qua API POST /properties/{id}/disable nếu chưa ACTIVE
```

Hoặc đổi mã HĐ trong file test: `HD-2026-TEST-001`, `HD-2026-TEST-002`, ...
