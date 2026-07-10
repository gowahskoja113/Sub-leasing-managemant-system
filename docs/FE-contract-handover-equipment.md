# Thiết bị bàn giao theo Hợp đồng thuê — Hướng dẫn FE

Tài liệu mô tả luồng chọn thiết bị bàn giao khi tạo/sửa HĐ thuê, gồm **có sẵn (EXISTING)** và **lắp thêm (ADDED)**.

**Nguyên tắc:**
- `selectedEquipmentIds` → thiết bị **có sẵn** trong nhà (checkbox từ `contract-available-equipments`)
- `addedEquipments` / `addedEquipmentIds` → thiết bị **lắp thêm** (deal riêng, chủ mua)
- BE **gộp cả hai** vào `equipmentSnapshot` (1 field duy nhất cho DOCX) và `equipmentList`

> **Không** gửi `equipmentSnapshot` JSON tự build nữa — trừ legacy tạm thời.

---

## 1. Tóm tắt nhanh

| Loại | FE gửi | Nguồn dữ liệu |
|------|--------|---------------|
| Có sẵn | `selectedEquipmentIds: number[]` | `GET contract-available-equipments` |
| Lắp thêm (chưa có trong DB) | `addedEquipments: AddedEquipment[]` | Form nhập tay mobile |
| Lắp thêm (đã POST trước) | `addedEquipmentIds: number[]` | `POST /properties/{id}/equipments` |

| FE đọc | Ý nghĩa |
|--------|---------|
| `equipmentList` | Tất cả thiết bị bàn giao (EXISTING + ADDED), có `source` và `scope` |
| `equipmentSnapshot` | Text BE sinh cho DOCX |
| `selectedExistingIds` | ID có sẵn đã chọn |
| `selectedAddedIds` | ID lắp thêm đã gắn HĐ |

---

## 2. Câu hỏi thường gặp

### 2.1. Thiết bị lắp thêm (ADDED) gửi field nào?

**Hai cách (chọn một hoặc kết hợp):**

#### Cách A — Inline khi lưu HĐ (khuyến nghị cho mobile)

Gửi object trong body HĐ, **chưa cần** tạo trước trong DB:

```json
{
  "selectedEquipmentIds": [12, 15],
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

BE sẽ:
1. Tạo `Equipment` với `source = ADDED_BY_TENANT`
2. Gắn vào `tenant_contract_equipments`
3. Gộp vào `equipmentSnapshot`

#### Cách B — Tạo trước qua API equipments

```http
POST /api/v1/properties/{propertyId}/equipments
{ "equipmentName": "Tủ lạnh", "category": "Điện lạnh", "cost": 3500000, "roomId": 5 }
→ { "id": 99, ... }
```

Sau đó gửi khi lưu HĐ:

```json
{
  "selectedEquipmentIds": [12],
  "addedEquipmentIds": [99]
}
```

### 2.2. BE có gộp ADDED vào `equipmentSnapshot` không?

**Có — 1 field duy nhất**, format 2 dòng:

```
Giường (Tốt) x1, Điều hòa (Mới) x1
Lắp thêm: Tủ lạnh (Mới) x1 — 3.500.000đ
```

- Dòng 1: thiết bị **có sẵn** (`selectedEquipmentIds`)
- Dòng 2: thiết bị **lắp thêm** (prefix `Lắp thêm:`)

`equipmentList` cũng gộp cả hai; phân biệt bằng `source: "EXISTING" | "ADDED"`.

### 2.3. `contract-available-equipments` có phân biệt phòng vs khu vực chung?

**Có.** Mỗi item có:

| Field | Giá trị | Ý nghĩa |
|-------|---------|---------|
| `scope` | `"ROOM"` | Thuộc phòng cụ thể |
| `scope` | `"SHARED"` | Khu vực chung (`room = null`) |
| `roomNumber` | `"101"` | Khi `scope = ROOM` |
| `houseArea` | `"LIVING_ROOM"` | Khi `scope = SHARED` (tuỳ căn) |

**Response mẫu:**

```json
[
  {
    "id": 12,
    "name": "Giường",
    "condition": "GOOD",
    "quantity": 1,
    "source": "EXISTING",
    "scope": "ROOM",
    "roomNumber": "101",
    "houseArea": null,
    "cost": null
  },
  {
    "id": 18,
    "name": "Máy giặt",
    "condition": "GOOD",
    "quantity": 1,
    "source": "EXISTING",
    "scope": "SHARED",
    "roomNumber": null,
    "houseArea": "OTHER",
    "cost": null
  }
]
```

> `contract-available-equipments` **chỉ** trả EXISTING. Thiết bị ADDED không xuất hiện ở đây.

---

## 3. API

### 3.1. Lấy thiết bị có sẵn (checkbox)

```http
GET /api/v1/properties/{propertyId}/contract-available-equipments?roomId={roomId}
```

### 3.2. Tạo / cập nhật HĐ

```http
POST /api/v1/properties/{propertyId}/rooms/{roomId}/tenant-contract
PUT  /api/v1/tenant-contracts/{contractId}
```

**Body đầy đủ (ví dụ):**

```json
{
  "fullName": "Nguyễn Văn A",
  "cccd": "001234567890",
  "phoneNumber": "0901234567",
  "moveInDate": "2026-07-15",
  "rentAmount": 5000000,
  "deposit": 5000000,
  "endDate": "2027-07-14",
  "draft": true,

  "selectedEquipmentIds": [12, 18],
  "addedEquipments": [
    { "name": "Tủ lạnh", "category": "Điện lạnh", "cost": 3500000 }
  ]
}
```

### 3.3. Quy tắc `null` vs `[]` khi PUT draft

| Field | `null` (không gửi) | `[]` (mảng rỗng) |
|-------|-------------------|------------------|
| `selectedEquipmentIds` | Giữ nguyên lựa chọn cũ | Không nhận thiết bị có sẵn nào |
| `addedEquipments` | Giữ nguyên lắp thêm cũ | Xóa toàn bộ lắp thêm |
| `addedEquipmentIds` | Giữ nguyên lắp thêm cũ | Xóa toàn bộ lắp thêm |

---

## 4. UI gợi ý (mobile)

```
┌─ Thiết bị có sẵn ────────────────────────────┐
│ [ROOM 101]  ☑ Giường (Tốt)                  │
│ [ROOM 101]  ☐ Điều hòa (Mới)                │
│ [DÙNG CHUNG] ☐ Máy giặt (Tốt)               │
└─────────────────────────────────────────────┘

┌─ Lắp thêm (chủ mua theo deal) ──────────────┐
│ + Tủ lạnh — 3.500.000đ                      │
│ + Bàn làm việc — 1.200.000đ                 │
└─────────────────────────────────────────────┘
```

**TypeScript:**

```typescript
type AddedEquipment = {
  name: string;
  category?: string;
  cost?: number;
  roomId?: number;
  condition?: "NEW" | "GOOD" | "MAINTENANCE" | "BROKEN";
};

type AvailableEquipment = {
  id: number;
  name: string;
  condition: string;
  source: "EXISTING";
  scope: "ROOM" | "SHARED";
  roomNumber?: string;
  houseArea?: string;
};

// Save
await putDraft(contractId, {
  selectedEquipmentIds: [...selectedExistingIds],
  addedEquipments: addedItems.map((x) => ({
    name: x.name,
    category: x.category,
    cost: x.cost,
    roomId: x.roomId,
    condition: x.condition ?? "NEW",
  })),
});
```

**Nhóm UI theo scope:**

```typescript
const roomItems = available.filter((e) => e.scope === "ROOM");
const sharedItems = available.filter((e) => e.scope === "SHARED");
```

---

## 5. Migration từ JSON `equipmentSnapshot` cũ (mobile)

| Trước (FE tự build) | Sau (gửi BE) |
|---------------------|--------------|
| `{ type: "EXISTING", id: 12 }` | `selectedEquipmentIds: [12]` |
| `{ type: "ADDED", name, cost }` | `addedEquipments: [{ name, cost, ... }]` |
| Gộp JSON → `equipmentSnapshot` | **Không gửi** `equipmentSnapshot` — BE tự sinh |

---

## 6. DISABLE thiết bị

| Loại | Khi HĐ ACTIVE |
|------|---------------|
| EXISTING không chọn | DISABLE (khách không nhận) |
| ADDED | **Không** bị disable — là thiết bị mới tạo cho deal |

---

## 7. Checklist FE

- [ ] Tách UI: **Có sẵn** (checkbox) vs **Lắp thêm** (form thêm dòng)
- [ ] Nhóm checkbox theo `scope` ROOM / SHARED
- [ ] Gửi `selectedEquipmentIds` + `addedEquipments` (hoặc `addedEquipmentIds`)
- [ ] Bỏ gửi `equipmentSnapshot` JSON
- [ ] Đọc `equipmentList[].source` để render 2 section khi xem HĐ
- [ ] Tenant handover dùng `equipmentList`, không parse snapshot

---

## 8. Test curl

```bash
# Lấy có sẵn (có scope)
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/properties/1/contract-available-equipments?roomId=5"

# Draft: giường có sẵn + tủ lạnh lắp thêm
curl -X PUT -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"selectedEquipmentIds":[12],"addedEquipments":[{"name":"Tủ lạnh","category":"Điện lạnh","cost":3500000}]}' \
  http://localhost:8080/api/v1/tenant-contracts/42
```
