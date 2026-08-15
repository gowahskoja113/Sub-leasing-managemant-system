# BE DONE — WebSocket hoá đơn thanh toán realtime

**Ngày:** 15/08/2026  
**Người gửi:** team BE  
**Người nhận:** team FE (mobile tenant/manager + web admin/host)  
**Phạm vi:** chỉ **hoá đơn đã thanh toán** (`INVOICE_PAID`). REST API không đổi.

---

## Tóm tắt

Bật WebSocket **không** làm mọi API tự refresh. FE phải **connect + subscribe** trên màn đang xem hoá đơn. Khi tenant/PayOS/manager ghi nhận **PAID**, BE đẩy 1 event tới mọi user được xem hoá đơn đó.

| Việc FE cần làm | Ghi chú |
|-----------------|--------|
| Connect STOMP `/ws` kèm Bearer JWT | Native WebSocket, **không** SockJS |
| Subscribe `/user/queue/billing` | 1 connection/app, giữ khi user đang login |
| Nhận `INVOICE_PAID` → cập nhật UI / refetch list đang mở | Patch item hoặc gọi lại GET list |

---

## 1. Kết nối

| | |
|---|---|
| Protocol | STOMP over WebSocket |
| URL | `wss://{host}/ws` (local: `ws://localhost:{port}/ws`) |
| Auth | STOMP `CONNECT` header `Authorization: Bearer {accessToken}` |
| Prefix user | `/user` |
| Destination FE subscribe | **`/user/queue/billing`** |

Không gửi message `/app/...`. BE chỉ **push xuống**, FE không publish.

Mất kết nối: reconnect + subscribe lại (token còn hạn). Logout: disconnect.

---

## 2. Payload `INVOICE_PAID`

```json
{
  "event": "INVOICE_PAID",
  "invoiceId": 123,
  "invoiceCode": "HD-ONB-45",
  "invoiceType": "RENT",
  "cycleType": "FIRST",
  "status": "PAID",
  "propertyId": 7,
  "propertyName": "Nhà A",
  "roomNumber": "101",
  "contractId": 45,
  "tenantUserId": "uuid",
  "tenantName": "Nguyen Van A",
  "billingMonth": 8,
  "billingYear": 2026,
  "billingPeriod": "Tiền nhà tháng 08/2026",
  "utilityInvoiceId": null,
  "paymentMethod": "QR",
  "transactionId": "VQR-1723...",
  "paidAt": "2026-08-15T15:40:00"
}
```

| Field | Ý nghĩa |
|-------|---------|
| `event` | Luôn `INVOICE_PAID` (version này) |
| `invoiceId` / `invoiceCode` | Khoá patch list `GET .../invoices` |
| `invoiceType` | `RENT` · `ELECTRICITY` · `WATER` · `SERVICE` · `MAINTENANCE` · **`OTHER` = cọc onboard** |
| `cycleType` | `FIRST` nếu tiền nhà chu kỳ đầu (onboard); có thể `null` |
| `status` | `PAID` |
| `propertyId` | Lọc list theo tòa |
| `contractId` | Màn deposits / chi tiết HĐ |
| `utilityInvoiceId` | Có khi hoá đơn điện/nước — patch list utility |
| `billingMonth` / `billingYear` / `billingPeriod` | Lọc kỳ |

Onboard 1 lần QR có thể **2 event** gần nhau: `OTHER` (cọc) + `RENT` + `cycleType=FIRST`.

---

## 3. Ai nhận event

User **ACTIVE** đang login (subscribe) mới thấy. BE fan-out theo hoá đơn:

- Mọi `ROLE_ADMIN`
- Mọi `ROLE_OWNER` (host)
- Manager tòa: `operationManagerId`, `managedBy`, `assignedManager`
- Tenant của hoá đơn (`tenantUserId`)

Cùng user không nhận trùng. Role khác / màn không subscribe → không nhảy.

---

## 4. Gắn UI chỗ nào

REST **không** tự poll. Gắn subscribe (hoặc lắng nghe store global) trên các màn sau:

### Tenant (mobile)

| Màn | API hiện có | Khi nhận event |
|-----|-------------|----------------|
| Danh sách hoá đơn | `GET /api/v1/tenant/me/invoices` | Set item `invoiceId` → `PAID`, hoặc refetch |
| Chi tiết hoá đơn | `GET /api/v1/tenant/me/invoices/{id}` | Nếu `id` khớp → refetch / đóng QR |
| Lịch sử thanh toán | `GET /api/v1/tenant/me/payments` | Refetch (có dòng mới) |

### Manager

| Màn | API hiện có | Khi nhận event |
|-----|-------------|----------------|
| Danh sách hoá đơn | `GET /api/v1/manager/invoices` | Patch / refetch (filter `period`/`type` nếu đang lọc) |
| Chi tiết hoá đơn | `GET /api/v1/manager/invoices/{id}` | Refetch nếu đang mở đúng `invoiceId` |
| Tiền nhà theo tòa | `GET /api/v1/properties/{propertyId}/rent-invoices` | Chỉ khi `propertyId` khớp |
| Cọc / onboard | `GET /api/v1/manager/deposits` | Refetch nếu `invoiceType=OTHER` hoặc có `contractId` |
| Lịch sử điện/nước | `GET /api/v1/manager/utility-invoices` | Patch theo `utilityInvoiceId` → status `PAID` |
| Claim chờ duyệt | `GET /api/v1/manager/payments` | Nếu hoá đơn vừa PAID qua QR, claim có thể hết hạn — refetch |

### Admin / Owner / Host

| Màn | API hiện có | Khi nhận event |
|-----|-------------|----------------|
| Admin/Owner list hoá đơn | `GET /api/v1/manager/invoices` (OWNER xem all như admin) | Giống manager |
| Host invoices | `GET /api/v1/host/invoices` | Refetch / patch `invoiceId` |
| Host dashboard / công nợ | host summary, receivables | Refetch nếu đang mở (số outstanding đổi) |

**Không** bắt buộc gắn: tạo hoá đơn, meter, config billing — chưa có event tạo/huỷ.

---

## 5. Gợi ý xử lý FE

1. **1 STOMP client** sau login (không connect mỗi màn).
2. Màn list: `invoiceId` có trong list → `status = PAID`, `paidAt`, `paymentMethod`. Không có trong list (filter PENDING) → **xoá khỏi list** hoặc refetch.
3. Màn QR đang chờ: `invoiceId` khớp → hiện đã thanh toán, ngừng poll `.../payment/check` nếu đang poll.
4. Utility: ưu tiên `utilityInvoiceId`; không có thì vẫn cập nhật list tenant/manager invoices theo `invoiceType` ELECTRICITY/WATER.
5. Ignore `event` khác `INVOICE_PAID` (phòng BE thêm sau).

Không cần gọi lại **tất cả** API hệ thống — chỉ API của **màn đang mở** (hoặc cache hoá đơn).

---

## 6. Việc BE đã làm (tham chiếu)

- Connect `/ws` + JWT trên STOMP CONNECT.
- Publish khi: PayOS webhook, tenant check payment, manager verify claim, onboard cọc + first rent.
- Thanh toán điện/nước: `utility_invoices.status` → `PAID` cùng lúc với tenant invoice.

REST list/detail **không đổi contract**. WS chỉ là kênh phụ.

---

## 7. Checklist FE

- [ ] Connect `/ws` + `Authorization: Bearer`
- [ ] Subscribe `/user/queue/billing` sau login
- [ ] Tenant: InvoiceList + InvoiceDetail + Payments
- [ ] Manager: invoices + rent-invoices + deposits + utility-invoices
- [ ] Admin/Owner: manager invoices
- [ ] Host: `/host/invoices` (+ dashboard nếu đang mở)
- [ ] Reconnect khi resume app / mạng đứt
- [ ] Disconnect khi logout
