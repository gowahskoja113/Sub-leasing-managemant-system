# Import Excel — Hợp đồng thuê nháp (DRAFT) hàng loạt

> File mẫu: [`SLMS2026_import_tenant_draft_contracts.xlsx`](./SLMS2026_import_tenant_draft_contracts.xlsx)  
> Tái tạo: `node scripts/generate-tenant-draft-import-excel.mjs`  
> BĐS demo lấy từ [`SLMS2026_import_matrix_dot1.xlsx`](./SLMS2026_import_matrix_dot1.xlsx) / [`SLMS2026_import_matrix_dot2.xlsx`](./SLMS2026_import_matrix_dot2.xlsx).

---

## 1. Mục đích

Import một file Excel → tạo **nhiều hợp đồng thuê nháp (`DRAFT`)** cùng lúc (tương đương nhiều lần gọi `POST .../tenant-contract` với `draft: true`), rồi gán / gửi cho manager đón khách.

**Điều kiện:** BĐS đã tồn tại và **ACTIVE** trong hệ thống.

---

## 2. API

```http
POST /api/v1/import/tenant-draft-contracts-excel?dryRun=false
Authorization: Bearer {admin|managerToken}
Content-Type: multipart/form-data

file: <xlsx>
```

| Query | Mặc định | Ý nghĩa |
|-------|----------|---------|
| `dryRun` | `false` | `true` = chỉ validate, không ghi DB |

**Auth:** `ADMIN` hoặc `MANAGER`

### Response (thành công)

Cùng shape `BulkImportResponse` như import đợt 1/2:

```json
{
  "dryRun": false,
  "contractsProcessed": 5,
  "contractsSkipped": 0,
  "renovationLinesImported": 0,
  "equipmentRowsImported": 0,
  "results": [
    {
      "importStatus": "IMPORTED",
      "contractCode": "HD-MT-2026-00012",
      "propertyId": 42,
      "propertyName": "MTX#01 NORENO trống",
      "finalStatus": "DRAFT",
      "message": "Đã tạo HĐ nháp cho Nguyễn Văn An — nguyên căn"
    }
  ],
  "errors": []
}
```

### Response (lỗi validation — HTTP 400)

```json
{
  "message": "File Excel có lỗi validation",
  "errors": [
    {
      "sheet": "1. Hop_Dong_Nhap_Khach",
      "rowNumber": 3,
      "contractCode": "row-3",
      "field": "Mã HĐ inbound",
      "message": "Không tìm thấy HĐ inbound 'HD-XXX' trong hệ thống"
    }
  ]
}
```

---

## 3. File Excel

### Sheet

| Sheet | BE đọc | Mô tả |
|-------|--------|--------|
| `0. Huong_Dan` | Không | Hướng dẫn |
| `0. Tham_Chieu_BDS` | Không | Mã HĐ inbound từ matrix |
| **`1. Hop_Dong_Nhap_Khach`** | **Có** | Mỗi dòng = 1 HĐ nháp |

### Cột sheet `1. Hop_Dong_Nhap_Khach`

| Cột | Bắt buộc | Ghi chú |
|-----|:--------:|---------|
| **Mã HĐ inbound** | ★ | Ưu tiên — = mã hợp đồng đợt 1 (`HD-MTX-…`) |
| **Mã BĐS** | ★ | ID số trong DB (nếu không dùng mã inbound) |
| **Tên tòa nhà** | ★ | Fallback — lỗi nếu trùng tên |
| **Loại thuê** | Khuyến nghị | `NGUYEN_CAN` / `THEO_PHONG` |
| **Số phòng** | Khi theo phòng | vd `101` (cùng ma trận đợt 2) |
| **Họ tên khách thuê** | **Có** | |
| **CCCD** | **Có** | |
| **Số điện thoại** | **Có** | |
| **Ngày sinh** | Không | `YYYY-MM-DD` hoặc `DD/MM/YYYY` |
| **Ngày cấp CCCD** | Không | Field mới |
| **Nơi cấp CCCD** | Không | Field mới |
| **Ngày vào ở** | **Có** | |
| **Ngày kết thúc** | **Có** | Sau ngày vào ở; ≤ 5 năm |
| **Giá thuê/tháng** | **Có** | > 0 |
| **Số tháng cọc** | Không | 1 hoặc 2 |
| **Tiền cọc** | ★ | ≥ 0; nếu trống + có số tháng → `giá × tháng` |
| **Ngày đón khách dự kiến** | Không | |
| **SĐT quản lý đón khách** | Không | SĐT hoặc UUID của MANAGER/ADMIN |

★ Cần **ít nhất một** trong: Mã HĐ inbound / Mã BĐS / Tên tòa nhà.  
★ Tiền cọc bắt buộc **hoặc** tính được từ Số tháng cọc.

---

## 4. Map BĐS

Thứ tự ưu tiên:

1. `Mã HĐ inbound` → `InboundContract.contractCode` → `Property`
2. `Mã BĐS` → `Property.id`
3. `Tên tòa nhà` → `Property.propertyName` (không được trùng)

BĐS phải `status = ACTIVE`.

---

## 5. Field CCCD mới (CRUD + import)

Đã bổ sung vào form tạo/sửa nháp và entity:

| Field API | Excel | Lưu DRAFT | Lưu khi ACTIVE |
|-----------|-------|-----------|----------------|
| `cccdIssueDate` | Ngày cấp CCCD | `draft_tenant_cccd_issue_date` | `Tenant.cccd_issue_date` |
| `cccdIssuePlace` | Nơi cấp CCCD | `draft_tenant_cccd_issue_place` | `Tenant.cccd_issue_place` |

PDF nháp map: `${tenantCccdIssueDate}`, `${tenantCccdIssuePlace}` (kèm `${tenantDob}`).

---

## 6. Luồng demo đề xuất

1. Import matrix đợt 1 + đợt 2 → Host duyệt → nhà `ACTIVE`
2. (Tuỳ chọn) `dryRun=true` với file mẫu
3. `POST /tenant-draft-contracts-excel` → tạo các HĐ `DRAFT`
4. Manager mở danh sách nháp → onboarding / thu cọc như luồng tay

---

## 7. FE checklist

- [ ] Upload file `.xlsx` + nút Dry-run
- [ ] Hiển thị bảng `results` + `errors` theo dòng
- [ ] Link / form tải file mẫu từ docs
- [ ] Form tạo nháp tay: thêm Ngày cấp CCCD + Nơi cấp CCCD
- [ ] Response contract: đọc `tenantCccdIssueDate`, `tenantCccdIssuePlace`
