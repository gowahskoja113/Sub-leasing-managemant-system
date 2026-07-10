# Thanh lý Hợp đồng thuê (ACTIVE) — Hướng dẫn FE

API chấm dứt HĐ **đang hiệu lực** (trả phòng sớm, vi phạm, thỏa thuận). Khác với `cancel` (chỉ DRAFT / PENDING).

---

## 1. Phân biệt cancel vs terminate

| API | Trạng thái HĐ | Mục đích |
|-----|---------------|----------|
| `POST .../tenant-contracts/{id}/cancel` | `DRAFT`, `PENDING` | Hủy HĐ chưa kích hoạt |
| `POST .../tenant-contracts/{id}/terminate` | `ACTIVE`, `EXPIRED` | Thanh lý / trả phòng |

> HĐ `ACTIVE` **không** gọi được `/cancel` — BE trả lỗi và gợi ý dùng `/terminate`.

---

## 2. API thanh lý

```http
POST /api/v1/tenant-contracts/{contractId}/terminate
Authorization: Bearer {token}
Content-Type: application/json
```

**Phân quyền:** `ROLE_MANAGER`, `ROLE_ADMIN`

### Request body

```json
{
  "type": "EARLY_MOVE_OUT",
  "reason": "Khách báo trả phòng sớm do chuyển công tác",
  "effectiveDate": "2026-07-10",
  "note": "Đã kiểm tra hiện trạng phòng"
}
```

| Field | Bắt buộc | Mô tả |
|-------|----------|-------|
| `type` | Có | Loại thanh lý (enum bên dưới) |
| `reason` | Có | Lý do chi tiết (hiển thị / audit) |
| `effectiveDate` | Không | Ngày chấm dứt thực tế. Mặc định **hôm nay** |
| `note` | Không | Ghi chú nội bộ manager |

### `type` — `ContractTerminationType`

| Giá trị | UI gợi ý |
|---------|----------|
| `EARLY_MOVE_OUT` | Trả phòng / trả nhà sớm |
| `VIOLATION` | Vi phạm hợp đồng |
| `MUTUAL_AGREEMENT` | Hai bên thỏa thuận chấm dứt |
| `OTHER` | Khác |

### Response 200

Trả `TenantContractResponse` đầy đủ:

```json
{
  "id": 42,
  "contractCode": "HD-MT-2026-00042",
  "status": "TERMINATED",
  "endDate": "2026-07-10",
  "terminatedAt": "2026-07-10T15:30:00",
  "terminationType": "EARLY_MOVE_OUT",
  "terminationReason": "Khách báo trả phòng sớm do chuyển công tác",
  "effective": false,
  "effectiveLabel": "Không còn hiệu lực"
}
```

---

## 3. Hành vi BE sau khi terminate

1. `status` → `TERMINATED`
2. `endDate` → `effectiveDate` (ngày kết thúc thực tế)
3. `terminatedAt` → thời điểm xử lý
4. Phòng (`room`) → `AVAILABLE` (nếu đang `RENTED`)
5. Nguyên căn: `property.status` `RENTED` → `ACTIVE`
6. Thiết bị bị DISABLE do HĐ này → restore `ACTIVE`

---

## 4. Lỗi thường gặp

| HTTP | Message | FE hiển thị |
|------|---------|-------------|
| **422** | `Chỉ thanh lý được hợp đồng đang ACTIVE hoặc EXPIRED` | HĐ không ở trạng thái phù hợp |
| **422** | `Hợp đồng đã được thanh lý trước đó` | Đã terminate rồi |
| **422** | `Ngày chấm dứt không được trước ngày bắt đầu hợp đồng` | Chọn lại ngày |
| **422** | `Không thể hủy hợp đồng đã kích hoạt — dùng API thanh lý (/terminate)` | Gọi nhầm `/cancel` |

---

## 5. UI gợi ý (mobile / admin)

```
┌─ Thanh lý hợp đồng ─────────────────────┐
│ Loại:  ( ) Trả phòng sớm                │
│        ( ) Vi phạm                      │
│        ( ) Thỏa thuận                   │
│ Lý do: [________________________]       │
│ Ngày chấm dứt: [10/07/2026]             │
│ Ghi chú: [_____________________]        │
│              [ Xác nhận thanh lý ]      │
└─────────────────────────────────────────┘
```

```typescript
async function terminateContract(contractId: number, payload: {
  type: "EARLY_MOVE_OUT" | "VIOLATION" | "MUTUAL_AGREEMENT" | "OTHER";
  reason: string;
  effectiveDate?: string;
  note?: string;
}) {
  const res = await fetch(`/api/v1/tenant-contracts/${contractId}/terminate`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
  if (!res.ok) throw new Error(await res.text());
  return res.json();
}
```

---

## 6. Test curl

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"type":"VIOLATION","reason":"Nuôi thú cảnh trái quy định","effectiveDate":"2026-07-10"}' \
  http://localhost:8080/api/v1/tenant-contracts/42/terminate
```

---

## 7. Checklist FE

- [ ] Nút "Thanh lý HĐ" chỉ hiện khi `status === "ACTIVE"` hoặc `"EXPIRED"`
- [ ] Nút "Hủy HĐ" chỉ cho `DRAFT` / `PENDING`
- [ ] Form có `type` + `reason` bắt buộc
- [ ] Sau terminate: refresh chi tiết HĐ + trạng thái phòng
- [ ] Hiển thị `terminationType`, `terminationReason`, `terminatedAt` trên màn lịch sử HĐ
