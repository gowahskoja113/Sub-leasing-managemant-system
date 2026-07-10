# Yêu cầu trả phòng (Checkout Request) — Hướng dẫn FE

Luồng **tenant yêu cầu → manager duyệt → hoàn tất → terminate HĐ**.

---

## 1. Sơ đồ trạng thái

```
Tenant tạo          Manager duyệt        Manager hoàn tất
    │                     │                      │
    ▼                     ▼                      ▼
 PENDING ──approve──► APPROVED ──complete──► COMPLETED
    │                                            (+ HĐ TERMINATED)
    ├──reject──► REJECTED
    └──cancel──► REJECTED (tenant hủy)
```

| Status | Ý nghĩa |
|--------|---------|
| `PENDING` | Chờ manager xử lý |
| `APPROVED` | Đã duyệt, chờ ngày trả phòng / biên bản |
| `REJECTED` | Bị từ chối hoặc tenant hủy |
| `COMPLETED` | Đã trả phòng — HĐ đã terminate |

---

## 2. API Tenant

Base: `/api/v1/tenant/me` · Role: `TENANT`

### Tạo yêu cầu

```http
POST /api/v1/tenant/me/checkout-requests
```

```json
{
  "contractId": 42,
  "expectedMoveOutDate": "2026-07-20",
  "reason": "Chuyển công tác",
  "note": "Muốn trả phòng cuối tháng"
}
```

- Chỉ HĐ `ACTIVE` của chính tenant
- Không tạo trùng khi đã có `PENDING` hoặc `APPROVED`

### Danh sách / chi tiết

```http
GET /api/v1/tenant/me/checkout-requests
GET /api/v1/tenant/me/checkout-requests/{id}
```

### Hủy yêu cầu (PENDING)

```http
DELETE /api/v1/tenant/me/checkout-requests/{id}
```

---

## 3. API Manager / Admin

Base: `/api/v1/checkout-requests` · Role: `MANAGER`, `ADMIN`

### Danh sách

```http
GET /api/v1/checkout-requests
GET /api/v1/checkout-requests?status=PENDING
```

### Chi tiết

```http
GET /api/v1/checkout-requests/{id}
```

### Duyệt (PENDING → APPROVED)

```http
POST /api/v1/checkout-requests/{id}/approve
```

```json
{ "managerNote": "Đã liên hệ khách, hẹn ngày 20/07" }
```

### Từ chối (PENDING → REJECTED)

```http
POST /api/v1/checkout-requests/{id}/reject
```

```json
{ "reason": "Chưa đủ thời gian báo trước theo HĐ" }
```

### Hoàn tất (APPROVED → COMPLETED + terminate HĐ)

```http
POST /api/v1/checkout-requests/{id}/complete
```

```json
{
  "actualMoveOutDate": "2026-07-15",
  "note": "Đã kiểm tra hiện trạng phòng"
}
```

**BE tự gọi** `terminateActiveContract` với:
- `type`: `EARLY_MOVE_OUT`
- `effectiveDate`: `actualMoveOutDate` (mặc định hôm nay)
- Giải phóng phòng + restore thiết bị DISABLE

> Manager cũng có thể terminate trực tiếp qua `POST /tenant-contracts/{id}/terminate` (vi phạm, không qua checkout request).

---

## 4. Response mẫu

```json
{
  "id": 5,
  "contractId": 42,
  "contractCode": "HD-MT-2026-00042",
  "propertyName": "Nhà A",
  "roomNumber": "101",
  "tenantUserId": "...",
  "tenantFullName": "Nguyễn Văn A",
  "tenantPhone": "0901234567",
  "expectedMoveOutDate": "2026-07-20",
  "reason": "Chuyển công tác",
  "status": "APPROVED",
  "createdAt": "2026-07-10T10:00:00",
  "reviewedAt": "2026-07-10T14:00:00",
  "reviewedByName": "Manager B",
  "managerNote": "Đã liên hệ khách",
  "rejectReason": null,
  "completedAt": null
}
```

---

## 5. UI gợi ý

**Tenant app:** nút "Yêu cầu trả phòng" khi HĐ ACTIVE → form ngày + lý do → theo dõi status.

**Manager app:**
1. Tab "Yêu cầu trả phòng" — filter `PENDING`
2. Duyệt / Từ chối
3. Khi khách dọn xong → **Hoàn tất** (nhập ngày thực tế)

---

## 6. Checklist FE

- [ ] Tenant: tạo / xem / hủy (PENDING) checkout request
- [ ] Manager: list filter theo status
- [ ] Manager: approve / reject / complete
- [ ] Sau `complete`: refresh HĐ (`TERMINATED`) + trạng thái phòng
- [ ] Không gọi `/cancel` trên HĐ ACTIVE — dùng luồng checkout hoặc `/terminate`

---

## 7. Test curl

```bash
# Manager: list pending
curl -H "Authorization: Bearer $MGR_TOKEN" \
  "http://localhost:8080/api/v1/checkout-requests?status=PENDING"

# Approve
curl -X POST -H "Authorization: Bearer $MGR_TOKEN" -H "Content-Type: application/json" \
  -d '{"managerNote":"OK"}' \
  http://localhost:8080/api/v1/checkout-requests/5/approve

# Complete
curl -X POST -H "Authorization: Bearer $MGR_TOKEN" -H "Content-Type: application/json" \
  -d '{"actualMoveOutDate":"2026-07-15"}' \
  http://localhost:8080/api/v1/checkout-requests/5/complete
```
