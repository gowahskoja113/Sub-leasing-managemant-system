# Template hợp đồng thuê — Tóm tắt nội dung & hướng triển khai code

Tài liệu này mô tả **các phần cần có trong hợp đồng thuê** (để thiết kế file Word mẫu) và **cách backend Java lấy dữ liệu để điền vào DOCX** khi xuất file sau luồng onboarding khách.

---

## 1. Cấu trúc hợp đồng (các phần trong template)

### 1.1. Phần đầu (Header)

| Mục | Mô tả | Placeholder gợi ý | Nguồn dữ liệu SLMS |
|-----|--------|-------------------|---------------------|
| Quốc hiệu, tiêu ngữ | Cố định trong mẫu | — | Text cố định trong template |
| Địa điểm, ngày ký | VD: TP. HCM, ngày … tháng … năm … | `${signPlace}`, `${signDay}`, `${signMonth}`, `${signYear}` | Config + `paidAt` hoặc ngày `confirm` |
| Tiêu đề | HỢP ĐỒNG THUÊ NHÀ Ở | — | Text cố định |
| Số hợp đồng | Mã duy nhất | `${contractCode}` | `TenantContract.contractCode` |
| Căn cứ pháp lý | BLDS, Luật KD BĐS, NĐ 96/2024… | — | Text cố định |

---

### 1.2. Thông tin các bên

#### Bên cho thuê (Bên A)

> **Hard-code trong file Word** — không dùng placeholder `${lessor*}`. In sẵn tên, CCCD, địa chỉ, SĐT, STK trên mẫu (dòng chấm `......`). Xem [`contract-template-content.md`](./contract-template-content.md).

| Mục | Placeholder | Nguồn SLMS | Ghi chú |
|-----|-------------|------------|---------|
| Tên, CCCD, địa chỉ, SĐT, STK | — | Hard-code Word | BE không điền |
| Địa điểm ký (header) | `${signPlace}` | `app.contract.lessor.signPlace` | Mặc định TP. HCM |

#### Bên thuê (Bên B) — khách onboarding

| Mục | Placeholder | Nguồn SLMS | Ghi chú |
|-----|-------------|------------|---------|
| Họ tên | `${tenantFullName}` | `User.fullName` (tenant) | Có sẵn |
| CCCD | `${tenantCccd}` | `Tenant.cccd` | Có sẵn |
| Ngày sinh | `${tenantDob}` | `Tenant.dateOfBirth` / draft | Có sẵn |
| Ngày cấp CCCD | `${tenantCccdIssueDate}` | `Tenant.cccdIssueDate` / draft | Có sẵn |
| Nơi cấp CCCD | `${tenantCccdIssuePlace}` | `Tenant.cccdIssuePlace` / draft | Có sẵn |
| Số điện thoại | `${tenantPhone}` | `User.phoneNumber` | Có sẵn |
| Địa chỉ thường trú | `${tenantAddress}` | — | Nên bổ sung form onboarding |
| Email (tuỳ chọn) | `${tenantEmail}` | `User.email` | Tuỳ chọn |

> **Lưu ý:** Mẫu `Contract_Template.docx` hiện tại theo **bên thuê là tổ chức** (GCNĐKKD, người đại diện). Luồng SLMS tạo **cá nhân** — nên dùng mẫu bên thuê cá nhân.

#### Thành viên ở cùng (Phụ lục hoặc bảng lặp)

| Mục | Nguồn SLMS |
|-----|------------|
| Họ tên | `HouseholdMember.fullName` |
| Quan hệ | `HouseholdMember.relation` |
| CCCD | `HouseholdMember.cccd` |
| SĐT | `HouseholdMember.phone` |
| Ngày sinh | `HouseholdMember.dateOfBirth` |

---

### 1.3. Thông tin nhà / phòng cho thuê (Điều 1)

| Mục | Placeholder | Nguồn SLMS |
|-----|-------------|------------|
| Tên căn / tòa nhà | `${propertyName}` | `Property.propertyName` |
| Loại (nguyên căn / phòng) | `${propertyType}` | `Property.wholeHouse` → map text |
| Địa chỉ | `${propertyAddress}` | `Property.address` |
| Khu vực | `${zoneName}` | `Property.zone` |
| Số phòng (nếu thuê phòng) | `${roomNumber}` | `Room.roomNumber` |
| Diện tích (m²) | `${areaSize}` | `Property.areaSize` |
| Số tầng | `${totalFloor}` | `Property.totalFloor` |
| Mô tả / hiện trạng | `${propertyDescription}` | `Property.descriptions` |
| Ghi chú bàn giao | `${roomConditionNote}` | `TenantContract.roomConditionNote` |

---

### 1.4. Giá thuê & thanh toán (Điều 2–3)

| Mục | Placeholder | Nguồn SLMS |
|-----|-------------|------------|
| Giá thuê / tháng (số) | `${rentAmount}` | `TenantContract.rentAmount` |
| Giá thuê (bằng chữ) | `${rentAmountInWords}` | Tính từ `rentAmount` (utility Java) |
| Tiền cọc (số) | `${deposit}` | `TenantContract.deposit` |
| Tiền cọc (bằng chữ) | `${depositInWords}` | Tính từ `deposit` |
| Số tháng cọc | `${depositMonths}` | `TenantContract.depositMonths` |
| Phí dịch vụ | `${serviceFee}` | `Property.serviceFee` |
| Đơn giá điện (VNĐ/kWh) | `${electricityUnitPrice}` | `Property.electricityUnitPrice` |
| Đơn giá nước (VNĐ/m³) | `${waterUnitPrice}` | `Property.waterUnitPrice` |
| Phương thức thanh toán | `${paymentMethod}` | Template / "Chuyển khoản PayOS" |
| Ngày thanh toán cọc | `${paidAt}` | `TenantContract.paidAt` |

---

### 1.5. Thời hạn & bàn giao (Điều 4)

| Mục | Placeholder | Nguồn SLMS |
|-----|-------------|------------|
| Ngày bắt đầu | `${startDate}` | `TenantContract.startDate` |
| Ngày kết thúc | `${endDate}` | `TenantContract.endDate` (có thể null) |
| Thời hạn (số tháng) | `${leaseDurationMonths}` | Tính từ start–end |
| Ngày vào ở / bàn giao | `${moveInDate}` | `TenantContract.moveInDate` |

---

### 1.6. Bàn giao điện, nước, thiết bị (Phụ lục 1)

| Mục | Nguồn SLMS |
|-----|------------|
| Chỉ số điện đầu kỳ | `TenantContract.initialElectricReading` |
| Chỉ số nước đầu kỳ | `TenantContract.initialWaterReading` |
| Ảnh đồng hồ điện | `TenantContract.electricMeterImageUrl` |
| Ảnh đồng hồ nước | `TenantContract.waterMeterImageUrl` |
| Danh mục thiết bị | `TenantContract.equipmentSnapshot` |
| Ảnh hiện trạng | `TenantContract.roomConditionUrls` |

---

### 1.7. Điều khoản chung (Điều 5–12)

Nội dung **cố định** trong template Word, không cần placeholder:

- Mục đích sử dụng nhà ở
- Quyền và nghĩa vụ hai bên
- Bảo trì, sửa chữa
- Chấm dứt hợp đồng, thời gian báo trước
- Phạt vi phạm, bồi thường
- Giải quyết tranh chấp
- Số bản hợp đồng, hiệu lực

---

### 1.8. Phần ký tên

| Mục | Placeholder |
|-----|-------------|
| Bên cho thuê — họ tên in sẵn | `${lessorName}` |
| Bên thuê — họ tên in sẵn | `${tenantFullName}` |
| Chỗ ký tay | Để trống trên giấy |

---

## 2. Phụ lục khuyến nghị

| Phụ lục | Nội dung |
|---------|----------|
| **PL1 — Biên bản bàn giao** | Chỉ số điện/nước, ghi chú, ảnh |
| **PL2 — Danh mục thiết bị** | `equipmentSnapshot` |
| **PL3 — Người ở cùng** | Bảng `householdMembers` |
| **PL4 — Ảnh hiện trạng** | URL hoặc in kèm riêng |

---

## 3. Checklist placeholder cho file Word

Thay các dòng `......` trong mẫu bằng:

```
${contractCode}
${signPlace} ${signDay} ${signMonth} ${signYear}

${lessorName} ${lessorIdNumber} ${lessorAddress} ${lessorPhone}
${lessorBankAccount} ${lessorBankName}

${tenantFullName} ${tenantCccd} ${tenantPhone} ${tenantAddress}

${propertyName} ${propertyAddress} ${roomNumber} ${areaSize} ${propertyType}

${rentAmount} ${rentAmountInWords}
${deposit} ${depositInWords} ${depositMonths}
${serviceFee} ${electricityUnitPrice} ${waterUnitPrice}

${startDate} ${endDate} ${leaseDurationMonths} ${moveInDate} ${paidAt}

${initialElectricReading} ${initialWaterReading}
${roomConditionNote} ${equipmentSnapshot}
```

Đặt file template tại: `src/main/resources/templates/contract/tenant-rental-template.docx`

---

## 4. Dữ liệu đã có vs cần bổ sung

| Đã có khi onboarding | Nên bổ sung |
|----------------------|-------------|
| Khách: tên, CCCD, SĐT | Địa chỉ thường trú khách |
| Nhà: tên, địa chỉ, diện tích, phí DV, giá điện/nước | Thông tin đầy đủ bên cho thuê (config) |
| HĐ: giá, cọc, ngày, chỉ số, ảnh, thiết bị | STK ngân hàng (tuỳ chọn) |
| Thành viên ở cùng | — |
| Mã HĐ, ngày thanh toán PayOS | — |

---

## 5. Cách code Java — có phải GET thông tin rồi ghi vào DOCX?

**Đúng.** Luồng kỹ thuật không phải “in PDF trực tiếp từ DB”, mà là:

```
Client gọi API
    → Backend GET/load dữ liệu từ DB (theo contractId)
    → Map sang Map<String, Object> (placeholder → giá trị)
    → Đọc file template .docx
    → Thay placeholder → file .docx đã điền
    → Trả byte[] cho client tải / in
    → (Tuỳ chọn) Convert DOCX → PDF
```

### 5.1. Bước 1 — API endpoint

```
GET /api/v1/tenant-contracts/{id}/document
Accept: application/vnd.openxmlformats-officedocument.wordprocessingml.document
```

- Quyền: `MANAGER`, `ADMIN`
- Điều kiện: `paymentStatus = PAID` hoặc `status = ACTIVE` (tuỳ nghiệp vụ)

### 5.bước 2 — Load dữ liệu từ DB (GET thông tin)

Service query **một lần** (hoặc repository có `@EntityGraph`) để lấy:

| Entity | Trường dùng cho HĐ |
|--------|---------------------|
| `TenantContract` | contractCode, rentAmount, deposit, dates, chỉ số, snapshot… |
| `Tenant` + `User` | fullName, cccd, phoneNumber |
| `Property` | propertyName, address, areaSize, wholeHouse, phí DV… |
| `Room` | roomNumber (nullable) |
| `InboundContract` | ownerName (bên cho thuê) |
| `HouseholdMember` | list thành viên |
| Config | lessorAddress, lessorPhone, lessorBank… |

**Không** cần client gửi lại toàn bộ form onboarding — backend đã lưu trong `TenantContract` khi onboard.

### 5.3. Bước 3 — Build biến cho template

```java
Map<String, Object> data = new HashMap<>();
data.put("contractCode", contract.getContractCode());
data.put("tenantFullName", tenantUser.getFullName());
data.put("rentAmount", formatVnd(contract.getRentAmount()));
data.put("rentAmountInWords", NumberToVietnameseWords.convert(contract.getRentAmount()));
// ... các field còn lại
data.put("householdMembers", members); // list cho bảng lặp
```

Format thêm: ngày (`dd/MM/yyyy`), tiền VNĐ, số bằng chữ tiếng Việt.

### 5.4. Bước 4 — Điền template DOCX

Project **đã có Apache POI** (`poi-ooxml`). Có thể:

| Cách | Mô tả |
|------|--------|
| **poi-tl** (khuyến nghị) | Template Word dùng `${tenBien}`, hỗ trợ loop bảng |
| **Apache POI thuần** | Replace text trong paragraph — dễ vỡ format Word |

```java
// Ví dụ ý tưởng (poi-tl)
XWPFTemplate template = XWPFTemplate.compile("templates/contract/tenant-rental-template.docx")
    .render(data);
ByteArrayOutputStream out = new ByteArrayOutputStream();
template.write(out);
return out.toByteArray();
```

### 5.5. Bước 5 — Trả file cho client

```java
return ResponseEntity.ok()
    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"HD-" + contractCode + ".docx\"")
    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
    .body(docxBytes);
```

Mobile/web: tải file → mở Word / share → in hoặc ký.

### 5.6. PDF — khác với DOCX

| | DOCX | PDF |
|---|------|-----|
| Cách làm | Fill template Word → trả `.docx` | Convert từ DOCX hoặc template PDF riêng |
| Độ khó | Thấp (POI / poi-tl) | Cao hơn trên Java server |
| Gợi ý | **Làm trước** | Phase 2: LibreOffice headless, Aspose, hoặc client tự “Print to PDF” |

**Tóm lại:** Code Java chủ yếu là **GET dữ liệu HĐ từ DB → map biến → ghi vào DOCX**. PDF là bước convert thêm, không bắt buộc ngay.

---

## 6. Khi nào gọi API xuất HĐ trong luồng onboarding

| Thời điểm | Mô tả |
|-----------|--------|
| Sau thanh toán cọc (`paymentStatus = PAID`) | Cho phép xem / in bản nháp |
| Sau confirm OTP (`status = ACTIVE`) | Bản chính thức để ký (khuyến nghị) |
| Web (`requireDepositPayment = false`) | HĐ ACTIVE ngay sau onboard → xuất HĐ luôn |

Generate **on-demand** khi user bấm “Xuất / In hợp đồng”, hoặc **tự động** sau confirm / onboard web (ACTIVE). File được **lưu storage** và URL ghi vào DB.

---

## 7. Lưu trữ file & xem lại bất cứ lúc nào

### 7.1. Lưu file (giống ảnh property)

| Mục | Chi tiết |
|-----|----------|
| Storage | Local disk `uploads/contracts/{contractCode}/{contractCode}.docx` |
| Public URL | `{publicBaseUrl}/uploads/contracts/{contractCode}/...` |
| DB | `TenantContract.documentUrl`, `documentGeneratedAt` |
| Config | `app.upload.contract-documents.dir`, `publicBaseUrl` |

### 7.2. Trạng thái hiệu lực

Enum `ContractStatus` đã có: `PENDING`, `ACTIVE`, `EXPIRED`, `TERMINATED`.

| Field API | Ý nghĩa |
|-----------|---------|
| `status` | Trạng thái hệ thống |
| `effective` | `true` = còn hiệu lực (`ACTIVE` + chưa quá `endDate`) |
| `effectiveLabel` | `"Còn hiệu lực"` / `"Không còn hiệu lực"` |

Hệ thống tự chuyển `ACTIVE` → `EXPIRED` khi `endDate` đã qua (khi đọc HĐ).

### 7.3. Ai xem được?

| Vai trò | API |
|---------|-----|
| Manager / Admin | `GET /api/v1/tenant-contracts/{id}`, `GET .../document`, `POST .../document` (xuất) |
| Khách thuê (ROLE_TENANT) | `GET /api/v1/me/tenant-contracts`, `GET /api/v1/tenant-contracts/{id}`, `GET .../document` |
| URL file trực tiếp | `GET /uploads/contracts/**` (public, giống ảnh) |

---

## 8. Tham chiếu code hiện tại

| File | Vai trò |
|------|---------|
| `TenantOnboardingServiceImpl.java` | Tạo HĐ, PayOS, confirm, auto-xuất file |
| `TenantContractDocumentServiceImpl.java` | Fill template, lưu storage, phân quyền xem |
| `TenantContract.java` | Entity + `documentUrl`, `documentGeneratedAt` |
| `TenantContractStatusHelper.java` | `effective`, sync EXPIRED |
| `LocalContractDocumentStorage.java` | Lưu file DOCX |
| `templates/contract/tenant-rental-template.docx` | Template Word (thêm `${placeholder}`) |
| `TenantMyContractController.java` | Khách xem danh sách HĐ của mình |

Config bên cho thuê in trên HĐ: `app.contract.lessor.*` (name, address, phone, bank…).
