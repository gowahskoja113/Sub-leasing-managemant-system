# Thiết bị / nội thất trên Hợp đồng thuê — Hướng dẫn FE

**Cập nhật 2026-07:** Nội thất **có sẵn trong nhà không còn chọn checkbox**.

BE tự lấy **toàn bộ thiết bị ACTIVE** thuộc property (+ phòng nếu thuê theo phòng, kèm thiết bị khu vực chung) rồi ghi vào `equipmentSnapshot` / PDF hợp đồng.

---

## 1. Tóm tắt nhanh

| Loại | FE làm gì |
|------|-----------|
| **Nội thất có sẵn (EXISTING)** | **Không gửi** `selectedEquipmentIds`. Không UI checkbox. BE tự gắn khi tạo/sửa HĐ và khi `POST .../draft-document`. |
| **Lắp thêm (ADDED)** — tuỳ chọn | Vẫn gửi `addedEquipments` / `addedEquipmentIds` nếu có deal lắp thêm ngoài inventory nhà. |

| FE đọc (response HĐ) | Ý nghĩa |
|----------------------|---------|
| `equipmentList` | Thiết bị đã gắn HĐ (EXISTING + ADDED) |
| `equipmentSnapshot` | Text BE sinh cho PDF (`Giường (Tốt) x1, Tủ lạnh (Mới) x1`) |
| `availableEquipmentList` | Inventory nhà (chỉ xem, không cần checkbox) |
| `selectedExistingIds` | ID nội thất có sẵn đã gắn (= gần như toàn bộ available) |

### Body tạo / cập nhật HĐ — nội thất

```json
{
  "rentAmount": 8000000,
  "deposit": 16000000,
  "moveInDate": "2026-07-20",
  "endDate": "2027-07-20"
}
```

Không cần `selectedEquipmentIds`. Không gửi `equipmentSnapshot` tự build.

Nếu vẫn có lắp thêm:

```json
{
  "addedEquipments": [
    {
      "name": "Tủ lạnh",
      "category": "Điện lạnh",
      "cost": 3500000,
      "roomId": 5,
      "condition": "NEW"
    }
  ]
}
```

Format `equipmentSnapshot` (BE sinh):

```
Giường (Tốt) x1, Điều hòa (Mới) x1
Lắp thêm: Tủ lạnh (Mới) x1 — 3.500.000đ
```

---

## 2. Khi nào BE gắn nội thất?

| Thời điểm | Hành vi |
|-----------|---------|
| Tạo HĐ (`onboard` / POST property contracts) | Lấy hết inventory ACTIVE → `equipmentSnapshot` |
| `PUT .../tenant-contracts/{id}` (DRAFT) | Đồng bộ lại inventory mới nhất (giữ ADDED nếu không gửi lại) |
| `POST .../draft-document` | Đồng bộ lại rồi xuất PDF |

Phạm vi inventory:

- Thuê **cả nhà** (`roomId` null): mọi thiết bị ACTIVE của property
- Thuê **phòng**: thiết bị của phòng đó + thiết bị khu vực chung (`room == null`)

---

## 3. FE cần đổi gì?

- [ ] **Xóa UI checkbox** chọn nội thất khi làm HĐ
- [ ] **Không gửi** `selectedEquipmentIds` / `declinedEquipmentIds`
- [ ] Có thể hiện **read-only** danh sách từ `equipmentList` hoặc `availableEquipmentList` để manager xem
- [ ] Giữ form lắp thêm (`addedEquipments`) chỉ nếu product vẫn cần
- [ ] Sau tạo HĐ / trước xuất PDF: đọc `equipmentSnapshot` hoặc `equipmentList` để confirm

### API xem inventory (tuỳ chọn, không bắt buộc cho HĐ)

```http
GET /api/v1/properties/{propertyId}/contract-available-equipments?roomId={roomId}
GET /api/v1/tenant-contracts/{id}/available-equipments
```

Chỉ để **hiển thị**; không dùng làm nguồn checkbox nữa.

Mỗi item có `scope`: `ROOM` | `SHARED`, kèm `roomNumber` / `houseArea`.

---

## 4. Legacy (không dùng)

| Field | Trạng thái |
|-------|------------|
| `selectedEquipmentIds` | Bỏ — nếu gửi sẽ giới hạn subset (không khuyến nghị) |
| `declinedEquipmentIds` | Deprecated |
| `equipmentSnapshot` trong request | Bỏ — BE tự sinh |

| Field ADDED | `null` (không gửi) | `[]` |
|-------------|-------------------|------|
| `addedEquipments` / `addedEquipmentIds` | Giữ lắp thêm cũ | Xóa hết lắp thêm |

> **`selectedEquipmentIds: []`** = không gắn nội thất nào. Hãy **omit** field để BE lấy hết.

---

## 5. FAQ

**Hỏi: Nhà chưa có nội thất trong hệ thống?**  
`equipmentSnapshot` rỗng trên PDF — cần nhập thiết bị vào property trước.

**Hỏi: Thêm nội thất vào nhà sau khi tạo DRAFT?**  
`PUT` draft hoặc `POST draft-document` sẽ đồng bộ lại danh sách mới nhất.

**Hỏi: DISABLE thiết bị khi HĐ ACTIVE?**  
Vì gắn hết EXISTING, không còn “thiết bị không chọn” → không DISABLE vì declined. ADDED không bị DISABLE.

---

## 6. Checklist

- [ ] Bỏ checkbox nội thất có sẵn
- [ ] Omit `selectedEquipmentIds` khi create/update
- [ ] PDF `${equipmentSnapshot}` có đầy đủ nội thất nhà
- [ ] (Tuỳ chọn) UI read-only `equipmentList`

---

## 7. Test curl

```bash
# Xem inventory nhà (read-only)
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/properties/1/contract-available-equipments?roomId=5"

# Cập nhật draft — không gửi selectedEquipmentIds; BE tự gắn hết nội thất
curl -X PUT -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"rentAmount":8000000}' \
  http://localhost:8080/api/v1/tenant-contracts/42
```
