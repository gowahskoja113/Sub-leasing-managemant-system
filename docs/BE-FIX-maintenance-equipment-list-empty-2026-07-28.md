# BE Fix / phân tích — List thiết bị trống khi tạo maintain + log 403

**Ngày:** 2026-07-28  
**Log:** `message (1).txt`

---

## 1. List thiết bị trống khi tạo maintain

### Kết luận

Hai nguyên nhân hay gặp (không loại trừ nhau):

| Hiện tượng | Nguyên nhân |
|------------|-------------|
| **403 Access Denied** | FE gọi `GET /api/v1/equipment/feature?roomId=` (hoặc endpoint manager khác) bằng token **TENANT** — trước đây `@PreAuthorize` không có `TENANT` → 403. UI thường nuốt lỗi → hiện list trống. |
| **HTTP 200 + `[]`** | Nhà/phòng **không có** thiết bị `operationalStatus=ACTIVE` trong scope HĐ — đúng data trống, không phải crash API. |
| **422 nhiều HĐ** | `GET /tenant/me/equipments` không truyền `contractId` khi account có ≥2 HĐ ACTIVE (trước đây BE bắt buộc) — FE có thể hiện trống. |

API đúng theo doc: `GET /api/v1/tenant/me/equipments` (± `contractId`).

### Fix BE đã làm

1. Cho **TENANT** gọi `GET /api/v1/equipment/feature?roomId=` và `GET /api/v1/equipment/{id}/feature` (có check quyền phòng/thiết bị).
2. `/tenant/me/equipments`: thiếu `contractId` khi nhiều HĐ → lấy HĐ ACTIVE mới nhất (giống dashboard), không fail cứng.
3. Fallback: nếu placement rỗng nhưng HĐ có `selectedEquipments` → trả list từ snapshot HĐ.
4. Log 403 kèm **method + path + query** để lần sau đọc log biết endpoint nào.

---

## 2. Log `message (1).txt`

- **8 lần** `[403] Access denied: Access Denied`.
- Pattern: JWT load user OK → ngay sau đó 403, **không** chạy SQL nghiệp vụ → đúng kiểu `@PreAuthorize` từ chối role.
- Không thấy stack duplicate `full_name` trong file này.

Sau restart, 403 sẽ log dạng: `[403] Access denied on GET /api/v1/....: Access Denied`.

---

## 3. FE checklist

- [ ] Ưu tiên `GET /tenant/me/equipments?contractId=` từ HĐ đang chọn
- [ ] Không coi 403/`[]` như nhau — 403 = sai API/role; `[]` = không có thiết bị
- [ ] Nguyên căn: dùng `propertyId` / HĐ whole-house; `room.id` có thể null
- [ ] Empty-state copy: “Phòng/nhà chưa có thiết bị bàn giao — báo ở Hư hao khác”
