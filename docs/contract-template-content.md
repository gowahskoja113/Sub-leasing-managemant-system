# Nội dung template hợp đồng thuê nhà ở

> File tham chiếu khi chỉnh `src/main/resources/templates/contract/tenant-rental-template.docx`.  
> BE spec kỹ thuật: [`tenant-contract-template-spec.md`](./tenant-contract-template-spec.md) · FE mobile: [`FE-tenant-contract-document-export.md`](./FE-tenant-contract-document-export.md)

---

## Quy ước placeholder

| Ký hiệu | Ý nghĩa |
|---------|---------|
| **Text cố định** | Ghi thẳng trong Word, BE **không** thay |
| `${...}` | BE điền tự động từ DB khi xuất file |

### Phần hard-code trong Word (không dùng `${}`)

**I. BÊN CHO THUÊ NHÀ Ở** — thông tin công ty/chủ nhà in sẵn trên mẫu, **không** map từ backend:

```
I. BÊN CHO THUÊ NHÀ Ở (sau đây gọi tắt là Bên cho thuê):
- Tên cá nhân:.................................................................................................................................
- Thẻ căn cước công dân số:..........................................................................................................
- Địa chỉ:..........................................................................................................................................
- Điện thoại liên hệ:..........................................................................................................................
- Số tài khoản:...........................................................Tại Ngân hàng:..............................................
```

Tương tự **chữ ký Bên cho thuê (Bên A)** ở cuối HĐ: in tên / để trống ký tay trên Word, **không** dùng `${lessorName}`.

---

## Nội dung đầy đủ template

```
CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM
Độc lập - Tự do - Hạnh phúc
________________________
${signPlace}, ngày ${signDay} tháng ${signMonth} năm ${signYear}

HỢP ĐỒNG THUÊ NHÀ Ở
Số: ${contractCode}

- Căn cứ Bộ luật Dân sự ngày 24 tháng 11 năm 2015;
- Căn cứ Luật Kinh doanh bất động sản ngày 28 tháng 11 năm 2023;
- Căn cứ Nghị định số 96/2024/NĐ-CP của Chính phủ quy định chi tiết một số điều của Luật Kinh doanh bất động sản;

Hai bên chúng tôi gồm:

### I. BÊN CHO THUÊ NHÀ Ở (Bên A) — HARD-CODE TRONG WORD (xem trên)

### II. BÊN THUÊ NHÀ Ở (Bên B)
- Họ và tên: ${tenantFullName}
- Thẻ căn cước công dân số: ${tenantCccd}
- Nơi đăng ký thường trú: ${tenantAddress}
- Điện thoại liên hệ: ${tenantPhone}
- Email: ${tenantEmail}

Hai bên chúng tôi thống nhất ký kết hợp đồng cho thuê nhà ở với các nội dung sau đây:

#### Điều 1. Các thông tin về nhà ở cho thuê
1. Loại nhà ở: ${propertyType}
2. Tên căn / tòa nhà: ${propertyName}
3. Vị trí, địa điểm nhà ở (Địa chỉ): ${propertyAddress} (Khu vực: ${zoneName})
4. Số phòng (nếu thuê phòng): ${roomNumber}
5. Diện tích của nhà ở: ${areaSize} m²
6. Số tầng: ${totalFloor}
7. Hiện trạng về chất lượng nhà ở (Mô tả): ${propertyDescription}
8. Ghi chú bàn giao: ${roomConditionNote}

#### Điều 2. Giá thuê nhà ở & các chi phí khác
1. Giá thuê nhà ở là: ${rentAmount} VNĐ/Tháng
   (Bằng chữ: ${rentAmountInWords})
2. Tiền đặt cọc: ${deposit} VNĐ (Số tháng cọc: ${depositMonths} tháng)
   (Bằng chữ: ${depositInWords})
3. Phí dịch vụ: ${serviceFee} VNĐ/tháng
4. Đơn giá điện: ${electricityUnitPrice} VNĐ/kWh
5. Đơn giá nước: ${waterUnitPrice} VNĐ/m³
6. Các chi phí sử dụng điện, nước... do Bên thuê thanh toán theo chỉ số thực tế.

#### Điều 3. Phương thức và thời hạn thanh toán
1. Phương thức thanh toán: ${paymentMethod}
2. Ngày thanh toán cọc: ${paidAt}

#### Điều 4. Thời hạn cho thuê, thời điểm giao, nhận nhà ở cho thuê
1. Thời hạn cho thuê nhà ở: ${leaseDurationMonths} tháng (Từ ngày ${startDate} đến ngày ${endDate}).
2. Thời điểm giao nhận nhà ở (Ngày vào ở): ${moveInDate}

#### Điều 5. Sử dụng nhà ở thuê — HARD-CODE TRONG WORD
1. Mục đích sử dụng: Nhà ở / Phục vụ nhu cầu sinh hoạt.
2. Hạn chế: Không sử dụng vi phạm pháp luật, không tự ý thay đổi cấu trúc...

*(Điều 6–12: giữ mẫu chuẩn Bộ Xây dựng — HARD-CODE)*

---

### PHỤ LỤC 1: BIÊN BẢN BÀN GIAO CHỈ SỐ ĐẦU KỲ
- Chỉ số điện đầu kỳ: ${initialElectricReading} kWh
- Chỉ số nước đầu kỳ: ${initialWaterReading} m³

### PHỤ LỤC 2: DANH MỤC THIẾT BỊ BÀN GIAO
${equipmentSnapshot}

### PHỤ LỤC 3: DANH SÁCH THÀNH VIÊN Ở CÙNG
${householdMembers}

---

| BÊN THUÊ (Bên B) | BÊN CHO THUÊ (Bên A) |
|:---:|:---:|
| *(Ký và ghi rõ họ tên)* | *(Ký và ghi rõ họ tên)* |
| **${tenantFullName}** | *(hard-code tên Bên A trong Word)* |
```

---

## Bảng map placeholder → nguồn BE

| Placeholder | Nguồn | Ghi chú |
|-------------|--------|---------|
| `signPlace` | `app.contract.lessor.signPlace` | Mặc định `TP. HCM` |
| `signDay/Month/Year` | Ngày xuất file / `paidAt` | Tự tính |
| `contractCode` | `TenantContract` | |
| `tenantFullName` | `User.fullName` | |
| `tenantCccd` | `Tenant.cccd` | |
| `tenantAddress` | — | Trống (chưa có form) |
| `tenantPhone` | `User.phoneNumber` | |
| `tenantEmail` | — | Trống (User chưa có email) |
| `propertyType` | `Property.wholeHouse` | Nguyên căn / Phòng cho thuê |
| `propertyName` | `Property.propertyName` | |
| `propertyAddress` | `Property.address` | |
| `zoneName` | `Property.zone.name` | |
| `roomNumber` | `Room.roomNumber` | `—` nếu nguyên căn |
| `areaSize` | `Property.areaSize` | |
| `totalFloor` | `Property.totalFloor` | |
| `propertyDescription` | `Property.descriptions` | |
| `roomConditionNote` | `TenantContract` | |
| `rentAmount` | `TenantContract.rentAmount` | Format VNĐ |
| `rentAmountInWords` | Utility | Số bằng chữ |
| `deposit` | `TenantContract.deposit` | |
| `depositInWords` | Utility | |
| `depositMonths` | `TenantContract.depositMonths` | |
| `serviceFee` | `Property.serviceFee` | |
| `electricityUnitPrice` | `Property.electricityUnitPrice` | |
| `waterUnitPrice` | `Property.waterUnitPrice` | |
| `paymentMethod` | Constant BE | `Chuyển khoản ngân hàng (PayOS)` |
| `paidAt` | `TenantContract.paidAt` | `dd/MM/yyyy HH:mm` |
| `leaseDurationMonths` | Tính từ start–end | |
| `startDate`, `endDate`, `moveInDate` | `TenantContract` | |
| `initialElectricReading` | `TenantContract` | |
| `initialWaterReading` | `TenantContract` | |
| `equipmentSnapshot` | `TenantContract` | |
| `householdMembers` | `HouseholdMember` list | Text nhiều dòng (chưa loop bảng) |

**Không map (hard-code Word):** toàn bộ mục **I. BÊN CHO THUÊ**, chữ ký Bên A, căn cứ pháp lý, Điều 5–12.
