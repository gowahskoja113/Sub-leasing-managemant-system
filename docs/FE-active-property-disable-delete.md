# BE ĐÃ SỬA — Vô hiệu hóa / Xóa tòa nhà ACTIVE (khi không còn khách thuê)

> Ghi chú cho team FE. BE đã cập nhật; FE đọc file này để tích hợp / xác nhận luồng trên màn Khởi tạo tòa nhà.
> Ngày phát hiện: 2026-06-18 · BE sửa: 2026-06-18
> Màn liên quan: `/admin/buildings` — `TaoDraftPage.tsx`

## 1. Triệu chứng (trước khi sửa BE)

Màn **Khởi tạo tòa nhà** (`/admin/buildings`):

- Nhà trạng thái **`ACTIVE`** (đang kinh doanh) **không vô hiệu hóa / xóa được** dù không còn khách thuê.
- FE đã bật nút **Vô hiệu hóa** + **Xóa** và chặn client-side (`ensureVacant`), nhưng BE vẫn trả lỗi:
  - `POST /properties/{id}/disable` → `"Không thể disable nhà đang ACTIVE"`
  - `DELETE /properties/{id}` → **422** `"Không thể xóa căn nhà đang ACTIVE. Vui lòng disable hoặc thanh lý hợp đồng trước."`

→ Luồng FE bị kẹt ở bước gọi API dù đã kiểm tra phòng trống phía client.

## 2. Yêu cầu nghiệp vụ

Nhà **`ACTIVE`** được **vô hiệu hóa** và **xóa** như các trạng thái khác, với điều kiện duy nhất:

| Loại nhà | Điều kiện |
|---|---|
| Chia phòng (`wholeHouse = false`) | Không còn phòng `RENTED` |
| Nguyên căn (`wholeHouse = true`) | Không còn hợp đồng tenant **ACTIVE** (thuê nguyên căn, `room == null`) |

Ngoài ra, **xóa** vẫn bị chặn nếu nhà **đã có chỉ số điện/nước** (monthly reading) — dùng cho dọn dữ liệu onboarding/import sai, không phải nhà đã vận hành thực tế.

## 3. BE đã sửa gì

### 3.1. `POST /api/v1/properties/{id}/disable`

- **Trước:** Chặn cứng khi `status = ACTIVE`.
- **Sau:** Cho phép `ACTIVE → DISABLED`.
- Trước khi disable, BE gọi `assertNoActiveTenants(propertyId)` (kiểm tra khách thuê server-side).

File: `PropertyOnboardingServiceImpl.disableProperty()`

### 3.2. `DELETE /api/v1/properties/{id}`

- **Trước:** Chặn cứng khi `status = ACTIVE` (422).
- **Sau:** Cho phép xóa nhà ACTIVE nếu pass `assertNoActiveTenants` **và** chưa có chỉ số điện/nước.

File: `PropertyDeletionServiceImpl.purgeProperty()`

### 3.3. Kiểm tra khách thuê dùng chung (`assertNoActiveTenants`)

```java
// Nhà nguyên căn
if (existsByPropertyIdAndRoomIsNullAndStatus(propertyId, ACTIVE)) {
    throw "Còn khách thuê — không thể vô hiệu/xóa";
}

// Nhà chia phòng
long rentedCount = countByPropertyIdAndStatus(propertyId, RENTED);
if (rentedCount > 0) {
    throw "Còn {rentedCount} phòng đang có khách thuê — không thể vô hiệu/xóa";
}
```

File: `PropertyDeletionServiceImpl.assertNoActiveTenants()`

## 4. Hành vi API sau sửa (FE cần biết)

### Vô hiệu hóa — `POST /api/v1/properties/{propertyId}/disable`

BE lưu `previousStatus = status` trước khi chuyển sang `DISABLED` (cột `previous_status` trong DB, không trả về API).

| Tình huống | Kết quả |
|---|---|
| ACTIVE, không còn khách thuê | **200** — `status` → `DISABLED` |
| ACTIVE, còn phòng `RENTED` (chia phòng) | **422** — `Còn N phòng đang có khách thuê — không thể vô hiệu/xóa` |
| ACTIVE, còn HĐ nguyên căn ACTIVE | **422** — `Còn khách thuê — không thể vô hiệu/xóa` |
| Các status khác (DRAFT, PENDING, …), không còn khách | **200** — `DISABLED` (như cũ) |

### Xóa — `DELETE /api/v1/properties/{id}`

| Tình huống | Kết quả |
|---|---|
| ACTIVE, không khách, chưa có chỉ số điện/nước | **204** No Content |
| ACTIVE, còn khách thuê | **422** — message giống disable |
| ACTIVE (hoặc bất kỳ status), đã có chỉ số điện/nước | **422** — `Không thể xóa căn nhà đã có chỉ số điện nước. Chỉ dùng cho dữ liệu onboarding/import sai.` |
| DISABLED / DRAFT, không khách, chưa có chỉ số | **204** (như cũ) |

### Kích hoạt lại — `POST /api/v1/properties/{propertyId}/enable`

| Tình huống | Kết quả |
|---|---|
| DISABLED (đã disable từ ACTIVE) | **200** — `status` → `ACTIVE` (khôi phục đúng trạng thái trước disable) |
| DISABLED (đã disable từ DRAFT / PENDING / …) | **200** — `status` → trạng thái tương ứng trước disable |
| DISABLED nhưng không có `previousStatus` (dữ liệu cũ) | **200** — `status` → `DRAFT` |
| Không phải DISABLED | **422** — `Chỉ có thể enable khi nhà đang ở trạng thái DISABLED` |

### Xóa purge (onboarding) — `DELETE /api/v1/properties/{propertyId}/purge`

Cùng logic `assertNoActiveTenants` + ràng buộc chỉ số điện/nước. Dùng cho luồng import / admin onboarding, không phải endpoint FE màn Khởi tạo tòa nhà đang gọi.

## 5. Khớp với FE (`ensureVacant` trên `TaoDraftPage.tsx`)

FE **đã làm đúng hướng** — giữ nguyên, BE giờ khớp:

| Bước FE | API / logic |
|---|---|
| Nhà chia phòng: gọi `GET /properties/{id}/rooms`, đếm `RENTED` | Khớp BE `countByPropertyIdAndStatus(..., RENTED)` |
| Nhà nguyên căn: xác nhận thủ công trong dialog | BE vẫn kiểm tra HĐ ACTIVE server-side — nếu còn khách, API trả lỗi dù user bấm OK |
| Vô hiệu hóa | `POST /properties/{id}/disable` |
| Xóa | `DELETE /properties/{id}` |

**Gợi ý hiển thị lỗi:** map `err.response.data.message` từ BE (message tiếng Việt cố định ở trên). Không cần hard-code lại message disable/xóa ACTIVE cũ.

## 6. Luồng dự phòng khi xóa thẳng ACTIVE thất bại

Nhà **đã vận hành** thường **có chỉ số điện/nước** → `DELETE` sẽ fail dù không còn khách.

Luồng thay thế (FE đã có nhánh DISABLED):

1. **Vô hiệu hóa** — `POST /properties/{id}/disable` → `ACTIVE` → `DISABLED`
2. Nếu cần xóa hẳn và BE cho phép (không có chỉ số điện/nước): **Xóa vĩnh viễn** — `DELETE /properties/{id}`

Nếu bước 2 vẫn 422 vì chỉ số điện/nước → chỉ **disable**, không purge — đúng nghiệp vụ.

## 7. Tiêu chí chấp nhận (FE test)

- [ ] Nhà ACTIVE chia phòng, tất cả phòng ≠ `RENTED` → **Vô hiệu hóa** thành công, card chuyển `DISABLED`.
- [ ] Nhà ACTIVE chia phòng, còn ≥1 phòng `RENTED` → FE chặn `ensureVacant`; nếu gọi API trực tiếp → 422 message `Còn N phòng...`.
- [ ] Nhà ACTIVE nguyên căn, không HĐ tenant ACTIVE → **Vô hiệu hóa** thành công.
- [ ] Nhà ACTIVE nguyên căn, còn HĐ ACTIVE → 422 `Còn khách thuê — không thể vô hiệu/xóa`.
- [ ] Nhà ACTIVE onboarding (chưa có chỉ số điện/nước), không khách → **Xóa** thành công 204.
- [ ] Nhà ACTIVE đã có chỉ số điện/nước → **Xóa** 422; **Vô hiệu hóa** vẫn OK nếu không còn khách.
- [ ] Nhà ACTIVE → **Vô hiệu hóa** → **Kích hoạt lại** → `status` quay về `ACTIVE`; các module (cấu hình khai thác, Host, …) hiển thị nhất quán.

## 8. Tham chiếu endpoint

| Mục đích | Method + Path |
|---|---|
| Danh sách phòng (ensureVacant) | `GET /api/v1/properties/{propertyId}/rooms` |
| Vô hiệu hóa | `POST /api/v1/properties/{propertyId}/disable` |
| Khôi phục sau disable | `POST /api/v1/properties/{propertyId}/enable` |
| Xóa tòa nhà (màn Khởi tạo) | `DELETE /api/v1/properties/{id}` |
| Xóa purge (import/onboarding) | `DELETE /api/v1/properties/{propertyId}/purge` |

## 9. File BE đã đổi

| File | Thay đổi |
|---|---|
| `PropertyOnboardingServiceImpl.java` | Bỏ chặn ACTIVE ở `disableProperty`; `assertNoActiveTenants`; lưu/khôi phục `previousStatus` khi disable/enable |
| `Property.java` | Thêm field `previousStatus` |
| `DatabaseSchemaMigration.java` | Thêm cột `previous_status` nếu chưa có |
| `PropertyDeletionServiceImpl.java` | Bỏ chặn ACTIVE ở `purgeProperty`; thêm `assertNoActiveTenants` |
| `PropertyDeletionService.java` | Khai báo `assertNoActiveTenants(Long propertyId)` |
