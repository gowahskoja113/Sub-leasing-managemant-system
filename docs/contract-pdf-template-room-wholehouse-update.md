# Cập nhật xuất PDF hợp đồng — Template phòng / nguyên căn & nội thất

**Ngày cập nhật:** 2026-07-16  
**Phạm vi:** Import Excel HĐ nháp hàng loạt, API `POST .../draft-document`, render PDF từ template Word.

---

## 1. Tóm tắt

Trước đây backend dùng **một** template chung `tenant-apartment-draft-template.docx`. Đã chuyển sang **hai template riêng** theo loại thuê, đồng thời sửa placeholder `${equipmentSnapshot}` (mục **6.4. Bàn giao phòng và thiết bị**) và ép font **Times New Roman** trên file PDF đầu ra.

| Hạng mục | Trước | Sau |
|----------|-------|-----|
| Template thuê phòng | Template chung | `Template_contract_room.docx` |
| Template nguyên căn | Template chung | `Template_contract_wholehouse.docx` |
| `${equipmentSnapshot}` | Chỉ đọc cột DB, dễ trống | Resolve 3 bước (DB → thiết bị HĐ → inventory phòng/nhà) |
| Font PDF | Có thể lệch (Arial / fallback) | Times New Roman (DOCX + embed TTF khi convert) |

---

## 2. Template Word

### 2.1. File mẫu

| Loại thuê | Đường dẫn classpath | Điều kiện chọn |
|-----------|---------------------|----------------|
| **Theo phòng** | `templates/contract/Template_contract_room.docx` | `TenantContract.room != null` |
| **Nguyên căn** | `templates/contract/Template_contract_wholehouse.docx` | `TenantContract.room == null` |

### 2.2. Placeholder (cả hai template)

Hai file dùng cùng bộ placeholder:

```
${contractCode} ${signPlace} ${signDay} ${signMonth} ${signYear}
${tenantFullName} ${tenantCccd} ${tenantDob} ${tenantCccdIssueDate} ${tenantCccdIssuePlace}
${tenantAddress} ${tenantPhone} ${householdMembers}
${rentalUnit} ${areaSize} ${leaseDurationMonths} ${startDate} ${endDate}
${rentAmount} ${rentAmountInWords} ${deposit} ${depositInWords}
${equipmentSnapshot}
```

Mục **6.4** trong template phòng:

> *6.4. Bàn giao phòng và thiết bị cho thuê, công tơ điện, nước của phòng cho thuê đúng ngày theo thỏa thuận. Nội thất có thêm: ${equipmentSnapshot}*

---

## 3. Logic chọn template (BE)

**Class:** `TenantContractDocumentServiceImpl`

```
renderDraftDocument(contractId)
        │
        ▼
load TenantContract
        │
        ▼
resolveTemplateClasspath(contract)
        │
        ├─ room != null  → roomTemplateClasspath
        └─ room == null  → wholeHouseTemplateClasspath
        │
        ▼
DocxTemplateRenderer.render(template, variables)
        │
        ▼
DocxToPdfConverter.convert(docx)  → byte[] PDF
```

**Hằng số:** `ContractTemplateConstants.TEMPLATE_ROOM`, `TEMPLATE_WHOLE_HOUSE`

---

## 4. Cấu hình `application.yaml` (tuỳ chọn)

Mặc định đã trỏ đúng file mới. Chỉ override khi đổi đường dẫn:

```yaml
app:
  upload:
    contract-documents:
      room-template-classpath: templates/contract/Template_contract_room.docx
      whole-house-template-classpath: templates/contract/Template_contract_wholehouse.docx
```

Property cũ `templateClasspath` (`tenant-apartment-draft-template.docx`) **deprecated** — không còn dùng khi render PDF.

---

## 5. `${equipmentSnapshot}` — cách lấy dữ liệu

### 5.1. Vấn đề cũ

- PDF chỉ đọc `TenantContract.equipmentSnapshot` từ DB.
- Import Excel không gửi `equipmentSnapshot` trong request → nếu bước gắn thiết bị lúc tạo HĐ chưa persist đủ, PDF hiển thị **"Không có."** dù phòng đã có thiết bị trong hệ thống.

### 5.2. Cách xử lý mới

**Method:** `ContractEquipmentService.resolveEquipmentSnapshot(TenantContract contract)`

Thứ tự ưu tiên:

| Bước | Nguồn | Mô tả |
|:----:|-------|--------|
| 1 | `contract.equipmentSnapshot` | Giá trị đã lưu (nếu không rỗng) |
| 2 | `contract.selectedEquipments` | Build lại từ liên kết `tenant_contract_equipment` |
| 3 | Inventory ACTIVE | Query `findActiveForTenantPlacement(propertyId, roomId)` → format text |

**Format text** (giữ nguyên convention cũ):

```
Giường (Tốt) x1, Tủ lạnh (Mới) x1
Lắp thêm: Máy lạnh (Mới) x1 — 3.500.000đ   ← nếu có thiết bị lắp thêm
```

Nếu sau 3 bước vẫn rỗng → PDF in **"Không có."**

### 5.3. Phạm vi thiết bị theo loại thuê

| Loại thuê | `roomId` truy vấn | Thiết bị lấy |
|-----------|-------------------|--------------|
| Theo phòng | ID phòng | Thiết bị thuộc phòng đó **+** thiết bị khu vực chung (`room_id IS NULL`) |
| Nguyên căn | `null` | Toàn bộ thiết bị ACTIVE của property |

**Điều kiện DB:** `equipments.operational_status = 'ACTIVE'`, nguồn không phải `ADDED_BY_TENANT` (inventory có sẵn).

### 5.4. Import Excel HĐ nháp

Luồng import (`BulkTenantDraftContractImportServiceImpl`) **không đổi API** — vẫn gọi `TenantOnboardingService.onboardTenant(..., draft=true)`.

Khi tạo HĐ, BE vẫn gọi `resolveAndApplyHandover(null, null, null, null)` → mặc định chọn **hết** inventory trong phạm vi và ghi `equipmentSnapshot`.

Khi xuất PDF sau import, `resolveEquipmentSnapshot` là lớp bảo vệ thêm nếu snapshot DB trống.

---

## 6. Font Times New Roman trên PDF

### 6.1. Giai đoạn fill DOCX — `DocxTemplateRenderer`

- Thay placeholder `${...}` trên body, header, footer, bảng (kể cả bảng lồng).
- Sau khi fill: **ép mọi run** sang `Times New Roman` (ascii, hAnsi, eastAsia, cs).
- Bỏ rule layout cũ ghi đè `"Nội thất có thêm: …"` thành `"Không có."` sau khi đã điền placeholder.

### 6.2. Giai đoạn convert PDF — `DocxToPdfConverter`

- `IFontProvider` luôn embed file TTF Times New Roman hệ thống (Windows: `C:/Windows/Fonts/times.ttf`, …).
- Fallback Linux/Docker: Liberation Serif / DejaVu Serif.
- Font PDF gắn family name `Times New Roman`.

---

## 7. API (không đổi contract)

Endpoint xuất PDF **giữ nguyên**:

```http
POST /api/v1/tenant-contracts/{contractId}/draft-document
Authorization: Bearer {token}
```

| | |
|--|--|
| Điều kiện | `status === "DRAFT"` |
| Response 200 | Binary PDF (`application/pdf`) |
| Template | Tự chọn phòng / nguyên căn theo HĐ |

Import Excel:

```http
POST /api/v1/import/tenant-draft-contracts-excel?dryRun=false
```

Chi tiết cột Excel: [`FE-import-tenant-draft-contracts.md`](./FE-import-tenant-draft-contracts.md)

---

## 8. File code đã chỉnh

| File | Thay đổi |
|------|----------|
| `ContractTemplateConstants.java` | Thêm `TEMPLATE_ROOM`, `TEMPLATE_WHOLE_HOUSE` |
| `ContractDocumentUploadProperties.java` | `roomTemplateClasspath`, `wholeHouseTemplateClasspath` |
| `TenantContractDocumentServiceImpl.java` | Chọn template; dùng `resolveEquipmentSnapshot` |
| `ContractEquipmentService.java` | Thêm `resolveEquipmentSnapshot` |
| `ContractEquipmentServiceImpl.java` | Implement resolve + `buildInventorySnapshot` |
| `DocxTemplateRenderer.java` | Header/footer, bảng lồng, ép font; bỏ rule ghi đè nội thất |
| `DocxToPdfConverter.java` | Set family Times New Roman trên Font PDF |
| `TenantContractActionController.java` | Cập nhật comment API |
| `ContractDraftPdfRenderTest.java` | Test render cả 2 template |
| `ContractEquipmentServiceImplTest.java` | Test fallback inventory |

**Template Word (resource):**

- `src/main/resources/templates/contract/Template_contract_room.docx`
- `src/main/resources/templates/contract/Template_contract_wholehouse.docx`

---

## 9. Test

```bash
mvn test "-Dtest=ContractDraftPdfRenderTest,ContractEquipmentServiceImplTest,DocxTemplateRendererLayoutTest"
```

| Test | Kiểm tra |
|------|----------|
| `ContractDraftPdfRenderTest` | Render room + wholehouse → PDF hợp lệ; placeholder `equipmentSnapshot` được thay |
| `ContractEquipmentServiceImplTest` | Snapshot fallback từ inventory khi DB trống |
| `DocxTemplateRendererLayoutTest` | Layout tenant block sau normalize |

---

## 10. Checklist kiểm tra thủ công

- [ ] Import Excel tạo HĐ **theo phòng** → `POST draft-document` → PDF dùng mẫu phòng (tiêu đề / điều khoản phòng)
- [ ] Import Excel tạo HĐ **nguyên căn** → PDF dùng mẫu nguyên căn
- [ ] Phòng đã có thiết bị ACTIVE trong DB → mục 6.4 hiển thị danh sách (vd: `Giường (Tốt) x1, …`)
- [ ] Phòng không có thiết bị → mục 6.4 hiển thị **Không có.**
- [ ] Toàn bộ nội dung PDF dùng font Times New Roman (so với Word gốc)

---

## 11. Troubleshooting

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
|-------------|------------------------|------------|
| `${equipmentSnapshot}` còn nguyên trên PDF | Template Word tách placeholder qua nhiều run XML | Giữ placeholder trong **một** run text liền (như file mẫu hiện tại) |
| Mục 6.4 = "Không có." | Không có thiết bị ACTIVE trong phạm vi phòng/nhà | Import thiết bị đợt 1/2 trước; kiểm tra `room_id`, `operational_status` |
| PDF font khác Word | Môi trường server thiếu TTF Times New Roman | Cài font trên server hoặc dùng fallback Liberation/DejaVu (đã có trong code) |
| Sai template (phòng vs nguyên căn) | HĐ tạo không gán `room_id` đúng | Kiểm tra cột **Loại thuê** / **Số phòng** trong Excel import |

---

## 12. Tài liệu liên quan

- [`FE-import-tenant-draft-contracts.md`](./FE-import-tenant-draft-contracts.md) — Import Excel HĐ nháp
- [`FE-contract-pdf-document.md`](./FE-contract-pdf-document.md) — Luồng FE upload / xem PDF
- [`FE-contract-handover-equipment.md`](./FE-contract-handover-equipment.md) — Nội thất bàn giao & snapshot
- [`tenant-contract-template-spec.md`](./tenant-contract-template-spec.md) — Spec placeholder đầy đủ
