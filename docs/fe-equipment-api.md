# API Thiết bị — Hướng dẫn tích hợp FE

Tài liệu mô tả luồng thiết bị sau khi FE tách quy trình **bàn giao** và **mua mới**.

---

## Tổng quan 2 nguồn thiết bị

| `source` | Mô tả | Giá (`price`) | Manifest bắt buộc? |
|---|---|---|---|
| `INITIAL_HANDOVER` | Thiết bị có sẵn khi bàn giao | Luôn `0` — BE bỏ qua `price` từ FE | **Có** — phải khai báo manifest trước |
| `PURCHASED` | Thiết bị mua mới trong quá trình cải tạo | **Bắt buộc `> 0`** trên mỗi lần gán | **Không** — gán trực tiếp qua API assign |

**Khấu hao:** Tổng chi phí thiết bị mua mới = `SUM(price)` của tất cả equipment có `source = PURCHASED` đã gán vào nhà/phòng. Giá được lấy từ bản ghi equipment thực tế, không từ manifest.

---

## 1. Thiết bị bàn giao (`INITIAL_HANDOVER`)

### Bước 1 — Khai báo manifest

```
PUT /api/v1/properties/{propertyId}/equipment-manifest
```

```json
{
  "items": [
    {
      "catalogId": 1,
      "quantity": 3,
      "status": "NEW",
      "source": "INITIAL_HANDOVER",
      "price": 0
    }
  ]
}
```

> `price` với `INITIAL_HANDOVER` sẽ bị BE ghi đè thành `0`.

### Bước 2 — Gán vào phòng

```
POST /api/v1/properties/{propertyId}/equipments/assign
```

```json
{
  "catalogId": 1,
  "quantity": 1,
  "status": "NEW",
  "source": "INITIAL_HANDOVER",
  "roomId": 12,
  "houseArea": "BEDROOM"
}
```

- `price` **không cần gửi** (hoặc gửi cũng bị bỏ qua).
- Phải gán đủ số lượng trong manifest trước khi `submit-to-host`.

---

## 2. Thiết bị mua mới (`PURCHASED`)

Gán trực tiếp, **không cần** bước manifest.

```
POST /api/v1/properties/{propertyId}/equipments/assign
```

```json
{
  "catalogId": 5,
  "quantity": 2,
  "status": "NEW",
  "source": "PURCHASED",
  "price": 1500000,
  "roomId": 12,
  "houseArea": "LIVING_ROOM",
  "note": "Mua tại Điện máy X"
}
```

### Field `price` (mới)

| Field | Type | Bắt buộc | Mô tả |
|---|---|---|---|
| `price` | `number` (BigDecimal) | **Có** khi `source = PURCHASED` | Đơn giá **mỗi** thiết bị (VND). Phải `> 0` |
| `note` | `string` | Không | Ghi chú tùy chọn |

- `quantity = 2` + `price = 1500000` → tổng cộng `3.000.000` vào chi phí khấu hao.
- Response trả về equipment cuối cùng được tạo, có `price` đã lưu.

### Ví dụ response

```json
{
  "id": 101,
  "propertyId": 1,
  "roomId": 12,
  "catalogId": 5,
  "catalogName": "Máy lạnh 1HP",
  "houseArea": "LIVING_ROOM",
  "source": "PURCHASED",
  "status": "NEW",
  "price": 1500000,
  "note": "Mua tại Điện máy X"
}
```

---

## 3. Danh sách thiết bị & kho

```
GET /api/v1/properties/{propertyId}/equipments
```

| `roomId` | Ý nghĩa |
|---|---|
| `null` | Thiết bị đang ở **kho** (chưa gán phòng hoặc đã thu hồi khi xóa phòng) |
| có giá trị | Đang gán tại phòng đó |

### Gán lại từ kho vào phòng khác

```
PATCH /api/v1/properties/{propertyId}/equipments/{equipmentId}/assign
```

```json
{
  "roomId": 15,
  "houseArea": "BEDROOM"
}
```

- Chỉ áp dụng thiết bị đang ở kho (`roomId = null`).
- Giữ nguyên `price` đã lưu (không đổi khi reassign).

---

## 4. Xóa phòng (soft delete) & thu hồi thiết bị

```
DELETE /api/v1/properties/{propertyId}/rooms/{roomId}
```

- Phòng được đánh dấu `is_deleted = true`, ẩn khỏi danh sách.
- Tất cả thiết bị trong phòng tự động về kho (`roomId = null`).
- `price` của thiết bị **không đổi** — vẫn tính vào khấu hao.

---

## 5. Tính khấu hao

Khấu hao dùng công thức:

```
totalInvestment = totalRentAmount + totalRenovationCost + totalEquipmentCost
```

Trong đó:

```
totalEquipmentCost = SUM(equipment.price) WHERE source = 'PURCHASED'
```

API tính khấu hao (gọi khi submit onboarding):

```
POST /api/v1/properties/{propertyId}/depreciation/calculate
```

Kết quả trả về có field `totalEquipmentCost` phản ánh tổng giá thiết bị mua mới đã gán.

---

## 6. Điều kiện submit host

Trước `POST /api/v1/properties/{propertyId}/submit-to-host`:

- **Ít nhất một trong hai:**
  - Có manifest `INITIAL_HANDOVER` **và** đã gán đủ số lượng (nhà chia phòng), **hoặc**
  - Có ít nhất 1 equipment `PURCHASED` đã gán.
- Nhà chia phòng: phải tạo đủ số phòng (`totalRooms`), không tính phòng đã xóa (`is_deleted`).

---

## 7. Enum tham chiếu

### `EquipmentSource`

| Giá trị | Mô tả |
|---|---|
| `INITIAL_HANDOVER` | Có sẵn khi bàn giao |
| `PURCHASED` | Mua mới |

### `EquipmentStatus` (khi assign)

| Giá trị | Ghi chú |
|---|---|
| `NEW` | Hợp lệ |
| `GOOD` | Hợp lệ |

### `HouseArea` (tùy chọn)

`BEDROOM`, `LIVING_ROOM`, `KITCHEN`, `BATHROOM`, `BALCONY`, `COMMON_AREA`, ...

---

## 8. Lỗi thường gặp

| HTTP | Message | Nguyên nhân |
|---|---|---|
| 400 | `Thiết bị mua mới (PURCHASED) phải có price > 0` | Thiếu `price` hoặc `price <= 0` |
| 400 | `... chưa có trong manifest bàn giao` | Gán `INITIAL_HANDOVER` nhưng chưa tạo manifest |
| 400 | `đã gán X/Y — không thể gán thêm` | Vượt số lượng manifest (handover hoặc purchased có manifest cũ) |
| 404 | `Không tìm thấy phòng...` | `roomId` không tồn tại hoặc phòng đã bị xóa mềm |

---

## 9. Checklist FE

- [ ] Form mua mới: gửi `source: "PURCHASED"` + `price` (đơn giá) mỗi lần assign
- [ ] Form bàn giao: giữ luồng manifest → assign, **không** cần `price`
- [ ] Hiển thị kho: filter equipment có `roomId === null`
- [ ] Hiển thị tổng chi phí TB mua mới: sum `price` where `source === 'PURCHASED'`
- [ ] Sau xóa phòng: refresh danh sách equipment (thiết bị về kho)
