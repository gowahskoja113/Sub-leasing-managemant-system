# BE DONE — Manager xem lịch sử thu tiền

**Ngày:** 17/08/2026  
**Người gửi:** team BE  
**Người nhận:** team FE (mobile manager + web admin/host)  
**Tham chiếu spec:** `BE-NEED-manager-xem-lich-su-thu-tien-2026-08-17.md`

---

## Tóm tắt

Màn **Thu & Đối soát** trống vì `GET /api/v1/manager/payments` đọc **claim chờ đối soát**, không đọc bảng thanh toán thật. PayOS webhook ghi `tenant_payments` rồi đánh hoá đơn `PAID` — **không sinh claim**.

BE đã thêm endpoint lịch sử thu từ `tenant_payments`. Endpoint claims **giữ nguyên**.

| Hạng mục | Trạng thái BE | FE cần làm |
|----------|---------------|------------|
| `GET /manager/payments` (claims) | Giữ — hàng chờ đối soát | Nút Xác nhận / Từ chối trên `PENDING_VERIFY` |
| `GET /manager/payments/history` | ✅ mới | Nguồn **Đã thu** — bỏ workaround suy từ hoá đơn `PAID` |
| Mask tiền nhà / `HD-ONBOARD-*` | ✅ như invoices | MANAGER: `amount = null`; ADMIN/OWNER: số đầy đủ |
| Lọc `propertyId` / `contractId` / `from` / `to` | ✅ phía BE | Đẩy filter lên query, không tải hết rồi lọc máy |

---

## 1. Hai nguồn — không gộp một list

| Nguồn | API | Bản chất | UI |
|-------|-----|----------|----|
| Cần xử lý | `GET /api/v1/manager/payments` | Claim `PENDING_VERIFY` (khách khai “tôi đã CK”, PayOS chưa xác nhận) | Có nút Xác nhận / Từ chối |
| Đã thu | `GET /api/v1/manager/payments/history` | Từng lần ghi `tenant_payments` (PayOS, tiền mặt, trả hộ, verify claim) | Chỉ đọc — timeline |

Không suy “đã thu” từ `GET /manager/invoices?status=PAID` nữa. Cách tạm đó:

1. Một hoá đơn chỉ ra được một dòng — không tách trả góp / thu thiếu rồi bù.
2. Hoá đơn huỷ/điều chỉnh sau khi đã thu → dòng biến mất.
3. Không lọc khoảng thời gian ở BE.

---

## 2. API mới

```
GET /api/v1/manager/payments/history
Authorization: Bearer {manager|admin|owner}
```

`@PreAuthorize("hasAnyRole('MANAGER','ADMIN','OWNER')")`

### Query

| Param | Bắt buộc | Mặc định | Ý nghĩa |
|-------|----------|----------|---------|
| `propertyId` | ❌ | — | Lọc theo tòa |
| `contractId` | ❌ | — | Lọc theo hợp đồng |
| `from` | ❌ | — | `yyyy-MM-dd` — inclusive, theo `paidAt` |
| `to` | ❌ | — | `yyyy-MM-dd` — inclusive hết ngày |
| `page` | ❌ | `0` | Trang 0-based |
| `size` | ❌ | `20` | Page size |

`from` sau `to` → 400, message `from không được sau to`.

**Phạm vi:** MANAGER chỉ thấy nhà mình (`property.operationManagerId`), giống `GET /manager/invoices`. ADMIN/OWNER xem hết (`managerFilter = null`).

### Response (Spring `Page`)

Cùng shape với `GET /manager/deposits`:

```json
{
  "content": [
    {
      "id": 12,
      "invoiceId": 7,
      "invoiceCode": "HD-ONBOARD-1",
      "invoiceType": "OTHER",
      "contractId": 45,
      "tenantName": "Nguyễn Văn A",
      "propertyName": "Nhà A",
      "roomNumber": "101",
      "amount": null,
      "method": "QR",
      "paidAt": "2026-08-17T14:30:00",
      "transactionId": "VQR-1723890123456"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

| Field | Ý nghĩa |
|-------|---------|
| `id` | PK `tenant_payments` — **không** trùng id claim |
| `invoiceId` / `invoiceCode` / `invoiceType` | Hoá đơn gắn với lần thu |
| `contractId` | Hợp đồng |
| `tenantName` | User tenant; onboard draft thì lấy `draftTenantName` |
| `amount` | Số đã thu. **MANAGER:** `null` nếu RENT hoặc `HD-ONBOARD-*`. ADMIN/OWNER: số thật |
| `method` | Public: `QR` / `CASH` / `BANK_TRANSFER` (`PAYOS` nội bộ → `QR`) |
| `paidAt` | Thời điểm ghi nhận |
| `transactionId` | Mã PayOS / tham chiếu |

Sắp xếp: `paidAt DESC`.

### Ví dụ

```http
GET /api/v1/manager/payments/history
GET /api/v1/manager/payments/history?from=2026-08-01&to=2026-08-17
GET /api/v1/manager/payments/history?propertyId=7&contractId=45&page=0&size=20
```

---

## 3. API cũ — không đổi

```
GET /api/v1/manager/payments?status=
POST /api/v1/manager/payments/{id}/verify
POST /api/v1/manager/payments/{id}/reject
```

Vẫn là **claim**. `status` = `PENDING_VERIFY` / `VERIFIED` / `REJECTED`.  
Không trộn `id` claim với `id` history.

---

## 4. Checklist FE

**Màn Thu & Đối soát (manager mobile / web):**

- [ ] Tab/khối **Cần xử lý** ← `GET /manager/payments?status=PENDING_VERIFY`.
- [ ] Tab/khối **Đã thu** ← `GET /manager/payments/history` (không còn ghép từ invoices `PAID`).
- [ ] Counter “Đã xác nhận / Chờ xác nhận”: chờ xác nhận = số claim pending; đã thu = `totalElements` history (hoặc refetch sau PAID realtime).
- [ ] Date picker: gửi `from` / `to` lên BE.
- [ ] MANAGER: `amount == null` → **không** hiện `0đ`. Copy gợi ý: “Liên hệ admin” / ẩn cột tiền (cùng rule invoices).
- [ ] ADMIN/OWNER web: hiện `amount` đầy đủ.

**Không làm:**

- [ ] Không dedupe history với claim theo `invoiceId` — hai bảng khác nhau; claim verified rồi vẫn có dòng history (đúng).
- [ ] Không PATCH/POST history — read-only.

---

## 5. Test plan

1. Tenant thanh toán PayOS xong → `GET /tenant/me/payments` có 1 dòng.
2. Cùng HĐ, manager `GET /manager/payments` vẫn `[]` nếu không có claim (đúng).
3. Manager `GET /manager/payments/history` → **có** dòng đó (`invoiceCode` khớp, ví dụ `HD-ONBOARD-1`).
4. MANAGER: `amount` của RENT / `HD-ONBOARD-*` là `null`. ADMIN: có số.
5. `from`/`to` chỉ trả bản ghi trong khoảng `paidAt`.
6. Manager A không thấy thanh toán nhà manager B.

---

## 6. File BE đã đụng

| File | Việc |
|------|------|
| `ManagerBillingController` | `GET /api/v1/manager/payments/history` |
| `ManagerBillingService` / `Impl` | Query + mask |
| `TenantPaymentRepository` | `findHistoryForManager` |
| `ManagerPaymentHistoryResponse` | DTO mới |

Không migration — đọc bảng `tenant_payments` có sẵn.

---

*Bám spec `BE-NEED-manager-xem-lich-su-thu-tien-2026-08-17.md`. Endpoint claims không thay thế được history.*
