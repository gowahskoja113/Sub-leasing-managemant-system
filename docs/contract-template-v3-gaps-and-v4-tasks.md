# Contract Template v3 — Lỗi có thể xảy ra & nhiệm vụ BE/FE cho v4

> Rà soát `docs/Contract_Template_v3.docx` so với BE hiện tại (`TenantContractDocumentServiceImpl`, `DocxTemplateRenderer`).  
> Mục tiêu: checklist sửa template **v4** và việc BE/FE cần làm (bắt buộc / tuỳ chọn).

**Tham chiếu:** [`contract-template-content.md`](./contract-template-content.md) · [`FE-tenant-contract-document-export.md`](./FE-tenant-contract-document-export.md)

---

## 1. Tóm tắt nhanh v3

| Hạng mục | Trạng thái v3 |
|----------|----------------|
| Header (ngày ký, số HĐ) | OK — `${signPlace}`, `${signDay/Month/Year}`, `${contractCode}` |
| Bên thuê (I) — placeholder | OK — `${tenantFullName}`, `${tenantCccd}`, `${tenantPhone}`, `${tenantAddress}` |
| Điều 1–4, PL1–2 | OK — khớp BE |
| Bên cho thuê (II) — hard-code | **Tạm** — sửa nội dung Word sau (mẫu tổ chức + chấm) |
| PL3 thành viên | **Lỗi** — dùng `${member.*}`, BE không hỗ trợ |
| Chữ ký Bên A | **Lỗi** — `${lessorName}` BE không map |
| File BE đang load | `resources/templates/contract/tenant-rental-template.docx` — **chưa** trỏ v3 |

---

## 2. Lỗi / rủi ro khi in từ v3

### 2.1. Nghiêm trọng (file in sai — còn `${...}`)

| # | Triệu chứng | Nguyên nhân | Cách nhận biết |
|---|-------------|-------------|----------------|
| E1 | PL3 in nguyên `${member.fullName}`, `${member.relation}`… | Template dùng cú pháp loop `member.*`; BE chỉ map `${householdMembers}` (text) | Mở DOCX output, Ctrl+F `${` |
| E2 | Chữ ký Bên A còn `${lessorName}` | BE cố ý không map lessor (hard-code Word) | Cuối file HĐ, cột Bên cho thuê |

### 2.2. Trung bình (in được nhưng thiếu / sai nội dung)

| # | Triệu chứng | Nguyên nhân |
|---|-------------|-------------|
| E3 | Dòng **Địa chỉ** Bên thuê trống | `${tenantAddress}` — BE gán `""`, chưa có field onboarding |
| E4 | Phí DV / điện / nước trống | Property chưa có `serviceFee`, `electricityUnitPrice`, `waterUnitPrice` |
| E5 | **Ngày thanh toán cọc** trống | `${paidAt}` — HĐ xuất trước khi `paymentStatus = PAID` |
| E6 | **Số tháng thuê** lệch kỳ vọng | BE tính `ChronoUnit.MONTHS.between(start, end)` — có thể khác “12 tháng” calendar |
| E7 | Số HĐ / ngày ký không đổi giữa các lần xuất | Lần xuất sau ghi đè file cùng tên; `documentGeneratedAt` cập nhật |

### 2.3. Nhẹ (không crash — trình bày / nghiệp vụ)

| # | Triệu chứng | Ghi chú |
|---|-------------|---------|
| E8 | Mục I = Bên thuê, II = Bên cho thuê (ngược spec) | Sửa Word v4 / hard-code sau |
| E9 | Dòng chấm dài thừa sau “Hai bên chúng tôi gồm:” | Xóa trên Word |
| E10 | STK dưới Bên thuê (chấm) — BE không điền | Xóa hoặc hard-code |
| E11 | `${householdMembers}` xuống dòng xấu | Renderer chèn `\n` — Word có thể gom một đoạn |
| E12 | Mất bold/italic ở dòng có placeholder | `DocxTemplateRenderer` replace cả paragraph |
| E13 | Placeholder tách nhiều run Word | Gõ `${var}` **một lần** liền — nếu tách run có thể không thay |
| E14 | Dev: mở `documentUrl` localhost trên máy thật fail | Cần IP LAN / domain prod trong `publicBaseUrl` |

### 2.4. Không phải lỗi BE

| # | Mô tả |
|---|--------|
| E15 | Email — v3 không có `${tenantEmail}` | Đúng ý product; BE vẫn map `""` nếu template cũ còn key này |
| E16 | Auto xuất fail (template/disk) | Luồng thanh toán/confirm vẫn OK; chỉ `documentUrl` null — FE poll + fallback POST |

---

## 3. Placeholder: v3 vs BE

### 3.1. Khớp (v3 có — BE map)

```
contractCode, signPlace, signDay, signMonth, signYear,
tenantFullName, tenantCccd, tenantAddress, tenantPhone,
propertyType, propertyName, propertyAddress, zoneName, roomNumber,
areaSize, totalFloor, propertyDescription, roomConditionNote,
rentAmount, rentAmountInWords, deposit, depositInWords, depositMonths,
serviceFee, electricityUnitPrice, waterUnitPrice,
paymentMethod, paidAt, leaseDurationMonths, startDate, endDate, moveInDate,
initialElectricReading, initialWaterReading, equipmentSnapshot
```

### 3.2. v3 có — BE không map (→ lỗi E1, E2)

| Placeholder v3 | Hướng v4 |
|----------------|----------|
| `${member.fullName}`, `${member.relation}`, `${member.cccd}`, `${member.phone}`, `${member.dateOfBirth}` | **Xóa** — thay `${householdMembers}` |
| `${lessorName}` | **Xóa** — hard-code tên Bên A trong Word |

### 3.3. BE có — v3 chưa dùng

| Placeholder BE | Ghi chú v4 |
|----------------|------------|
| `${householdMembers}` | **Thêm** vào PL3 (1 dòng hoặc đoạn) |
| `${tenantEmail}` | **Không thêm** (option bỏ hẳn trên BE nếu chắc không dùng) |

---

## 4. Nhiệm vụ cho v4 — Backend (BE)

### 4.1. Bắt buộc

| # | Việc | Chi tiết |
|---|------|----------|
| BE-1 | **Deploy template v4** | Copy `Contract_Template_v4.docx` → `src/main/resources/templates/contract/tenant-rental-template.docx` (hoặc đổi `app.upload.contract-documents.template-classpath`) |
| BE-2 | Giữ map `${householdMembers}` | `formatHouseholdMembers()` — không đổi trừ khi v4 dùng bảng Word thật |
| BE-3 | Smoke test xuất HĐ | Sau deploy: xuất 1 HĐ → file **không còn** `${`; PL3 có text thành viên hoặc “Không có thành viên ở cùng.” |

### 4.2. Tuỳ chọn (match v4 / product)

| # | Việc | Option |
|---|------|--------|
| BE-O1 | **Xóa** `vars.put("tenantEmail", "")` | Nếu v4 không còn `${tenantEmail}` |
| BE-O2 | **Xóa** `ContractLessorProperties` fields cũ (name, bank…) | Chỉ giữ `signPlace` nếu v4 dùng `${signPlace}` |
| BE-O3 | **Thêm** `tenantAddress` | Field onboarding + map DB → `${tenantAddress}` |
| BE-O4 | **Thêm** `${lessorName}` từ config | Chỉ nếu v4 quyết điền Bên A bằng BE thay vì hard-code |
| BE-O5 | **Nâng cấp renderer** | poi-tl + loop bảng PL3 thay `${householdMembers}` text — nếu v4 giữ bảng |
| BE-O6 | Validate trước xuất | Warn/log nếu placeholder trong template không có trong map (dev tool) |
| BE-O7 | Format PL3 | Xuất `\r\n` hoặc nhiều paragraph thay `\n` cho Word đẹp hơn |

### 4.3. Không cần làm (trừ đổi nghiệp vụ)

- API response thêm email — **không** cần nếu không dùng email
- PDF export — phase sau
- Map lessor* — **không** nếu v4 hard-code Bên cho thuê

---

## 5. Nhiệm vụ cho v4 — Frontend (FE mobile / web)

### 5.1. Bắt buộc (đã có spec — xác nhận sau v4)

| # | Việc | Chi tiết |
|---|------|----------|
| FE-1 | Poll `documentUrl` sau thanh toán | `GET /tenant-contracts/{id}` — không gọi POST document trong happy-path |
| FE-2 | Nút “Xem hợp đồng” | Chỉ enable khi `documentUrl != null` |
| FE-3 | Mở file DOCX | Tải `documentUrl` + share/open (Expo FileSystem + Sharing) |
| FE-4 | Prod base URL | Dùng `documentUrl` từ API; BE `publicBaseUrl` HTTPS thật |

### 5.2. Tuỳ chọn

| # | Việc | Option |
|---|------|--------|
| FE-O1 | **Xóa** UI field Email trên form onboarding | Khớp v4 không có email |
| FE-O2 | **Thêm** field Địa chỉ thường trú | Nếu BE-O3 bật → gửi trong `OnboardTenantRequest` |
| FE-O3 | Hiển thị cảnh báo PL3 trống | Nếu `householdMembers` rỗng — HĐ vẫn ghi “Không có thành viên…” — FE không bắt buộc |
| FE-O4 | Fallback `POST .../document` | Manager khi poll timeout / `documentUrl` null |
| FE-O5 | **Xóa** hiển thị email trong chi tiết HĐ | Nếu không có field |
| FE-O6 | Preview trước confirm | Chỉ show link sau `paymentStatus === PAID` (bản nháp) |

---

## 6. Checklist template Word v4 (copy từ v3)

```
Header
  [x] ${signPlace}, ngày ${signDay} tháng ${signMonth} năm ${signYear}
  [x] Số: ${contractCode}

Bên thuê (placeholder)
  [x] ${tenantFullName}, ${tenantCccd}, ${tenantPhone}
  [ ] ${tenantAddress} — giữ nếu sau này BE-O3; xóa nếu không dùng
  [ ] Xóa dòng STK / chấm thừa dưới Bên thuê

Bên cho thuê
  [ ] Hard-code (sửa sau) — KHÔNG ${lessorName} ở chữ ký

Điều 1–4 + PL1–2
  [x] Giữ nguyên placeholder như v3

PL3
  [ ] XÓA bảng ${member.*}
  [ ] THÊM ${householdMembers}

Chữ ký
  [ ] ${tenantFullName} (Bên B)
  [ ] Bên A: tên in sẵn — KHÔNG ${lessorName}

Deploy
  [ ] Copy v4 → resources/templates/contract/tenant-rental-template.docx
  [ ] Test: Ctrl+F "${" trong file output = 0 kết quả
```

---

## 7. Ma trận quyết định v4 (team chốt nhanh)

| Câu hỏi | Khuyến nghị | BE | FE | Word v4 |
|---------|-------------|----|----|---------|
| PL3 dạng gì? | Text `${householdMembers}` | Giữ code hiện tại | Không đổi | Thay bảng member.* |
| Bên cho thuê? | Hard-code Word | Không map lessor* | — | Sửa nội dung sau |
| Email? | Không dùng | Xóa tenantEmail (option) | Xóa field (option) | Không có dòng Email |
| Địa chỉ khách? | Phase sau | Thêm field (option) | Thêm input (option) | Giữ `${tenantAddress}` |
| Chữ ký Bên A? | Hard-code | — | — | Xóa `${lessorName}` |

---

## 8. Test plan sau khi lên v4

### BE

- [ ] Onboard web → auto `documentUrl`
- [ ] Mobile: PayOS PAID → poll có `documentUrl`
- [ ] Confirm ACTIVE → xuất lại, `documentGeneratedAt` mới hơn
- [ ] HĐ có / không có `householdMembers`
- [ ] Thuê phòng vs nguyên căn (`roomNumber` = `—`)
- [ ] File output: không còn `${`

### FE

- [ ] Manager: nút xem HĐ sau thanh toán
- [ ] Tenant: `GET /me/tenant-contracts` + mở file
- [ ] Device thật mở DOCX (Android + iOS)

---

## 9. File code liên quan

| File | Vai trò |
|------|---------|
| `TenantContractDocumentServiceImpl.java` | Map biến template |
| `DocxTemplateRenderer.java` | Replace `${}` |
| `VietnameseNumberToWords.java` | Bằng chữ tiền |
| `ContractDocumentUploadProperties.java` | Path template |
| `ContractLessorProperties.java` | Chỉ `signPlace` |
| `docs/Contract_Template_v3.docx` | Bản hiện tại |
| `docs/Contract_Template_v4.docx` | Bản sắp tới (chưa có) |

---

*Tài liệu này cập nhật theo rà soát v3 — chỉnh lại khi có file v4 thực tế.*
