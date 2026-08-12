# FE — Realtime cập nhật trạng thái thanh toán hóa đơn (WebSocket / STOMP)

**Ngày:** 12/08/2026  
**Người gửi:** team BE  
**Người nhận:** team FE (web admin / manager)  
**Trạng thái:** BE đã xong, chờ FE subscribe và cập nhật UI.

---

## 1. Mục tiêu

Khi **tenant thanh toán hóa đơn trên mobile app xong**, trang quản lý web (admin / manager) **cập nhật trạng thái hóa đơn ngay**, không cần F5.

Phạm vi hiện tại: **chỉ realtime khi hóa đơn tenant chuyển sang `PAID`**.

---

## 2. Endpoint

| Môi trường | WebSocket URL |
|------------|----------------|
| Local | `ws://localhost:8080/ws` |
| Production | `wss://slms-api.duckdns.org/ws` |

| Mục | Giá trị |
|-----|---------|
| Protocol | STOMP over native WebSocket |
| Handshake path | `/ws` |
| SockJS | **Không dùng** — connect native WebSocket |
| Auth | JWT trên STOMP `CONNECT` header |
| Subscribe | `/user/queue/billing` |

REST API không đổi. FE vẫn gọi `GET /api/v1/manager/invoices` như cũ; socket chỉ **đẩy event** khi có thanh toán thành công.

---

## 3. Auth

JWT **không** gửi trên HTTP handshake. Gửi lúc STOMP `CONNECT`:

```text
Authorization: Bearer <access_token>
```

Header `authorization` (chữ thường) cũng được chấp nhận.

Token hết hạn / sai → BE reject CONNECT. FE cần reconnect với token mới sau login/refresh.

---

## 4. Ai nhận event

BE gửi **theo user đang login**, không phải topic chung.

| Role | Nhận event khi |
|------|----------------|
| **ADMIN** (status `ACTIVE`) | Mọi hóa đơn tenant vừa `PAID` |
| **MANAGER** | Chỉ hóa đơn của tenant thuộc nhà mà manager đó là `operationManager` |
| OWNER / TENANT | **Không** nhận event này |

Cả admin và manager đều subscribe **cùng destination**: `/user/queue/billing`.  
BE tự route đúng user — FE **không** subscribe `/topic/admin/...` hay `/topic/manager/{id}/...`.

---

## 5. Khi nào BE bắn event

Event `INVOICE_PAID` được publish **sau khi DB đã lưu hóa đơn `PAID`**, ở các luồng:

1. Tenant check PayOS trên app → đã thanh toán
2. PayOS webhook `POST /api/v1/payos/webhook` → thanh toán thành công
3. Manager/admin verify bank transfer claim → `POST /api/v1/manager/payments/{id}/verify`

---

## 6. Payload

JSON object, ví dụ:

```json
{
  "event": "INVOICE_PAID",
  "invoiceId": 123,
  "invoiceCode": "HD-ELE-45",
  "invoiceType": "ELECTRICITY",
  "status": "PAID",
  "propertyName": "Nhà A",
  "roomNumber": "101",
  "tenantName": "Nguyen Van A",
  "paymentMethod": "QR",
  "transactionId": "VQR-1712345678901",
  "paidAt": "2026-08-12T16:58:00"
}
```

| Field | Type | Ý nghĩa |
|-------|------|---------|
| `event` | string | Hiện chỉ `"INVOICE_PAID"` |
| `invoiceId` | number | ID hóa đơn — dùng match row trên UI |
| `invoiceCode` | string | Mã hóa đơn |
| `invoiceType` | string | `RENT` / `ELECTRICITY` / `WATER` / `SERVICE` / `MAINTENANCE` / `OTHER` |
| `status` | string | Luôn `"PAID"` với event này |
| `propertyName` | string | Tên nhà |
| `roomNumber` | string \| null | Số phòng (nguyên căn có thể null) |
| `tenantName` | string \| null | Tên khách |
| `paymentMethod` | string | `QR` hoặc `BANK_TRANSFER` (tùy luồng) |
| `transactionId` | string | Mã giao dịch |
| `paidAt` | string (ISO local datetime) | Thời điểm ghi nhận thanh toán |

Payload **không** chứa số tiền. FE cần số tiền thì refetch `GET /api/v1/manager/invoices/{id}` (admin thấy đủ; manager vẫn bị mask tiền thuê như REST hiện tại).

---

## 7. FE cần làm gì

### 7.1 Package

```bash
npm i @stomp/stompjs
```

### 7.2 Connect + subscribe (web)

```javascript
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: 'wss://slms-api.duckdns.org/ws', // local: ws://localhost:8080/ws
  connectHeaders: {
    Authorization: `Bearer ${accessToken}`,
  },
  reconnectDelay: 5000,
  onConnect: () => {
    client.subscribe('/user/queue/billing', (message) => {
      const event = JSON.parse(message.body);
      if (event.event === 'INVOICE_PAID') {
        // Cập nhật row hóa đơn event.invoiceId → status PAID
        // hoặc refetch GET /api/v1/manager/invoices
      }
    });
  },
});

client.activate();
```

Local:

```javascript
brokerURL: 'ws://localhost:8080/ws'
```

### 7.3 Cập nhật UI

Ưu tiên:

1. Tìm row `invoiceId` trên bảng hóa đơn đang mở
2. Đổi `status` → `PAID`, gán `paidAt` / `paymentMethod` nếu đang hiển thị
3. Nếu không tìm thấy row (đang filter khác kỳ / khác status) → có thể toast “Hóa đơn {invoiceCode} vừa được thanh toán” rồi refetch list

Không bắt buộc refetch toàn bộ list mỗi event.

### 7.4 Lifecycle

| Thời điểm | Việc |
|-----------|------|
| User login (ADMIN / MANAGER) | `client.activate()` |
| Token refresh | disconnect + connect lại với token mới |
| Logout / unmount trang | `client.deactivate()` |
| Mất mạng | `@stomp/stompjs` tự reconnect (`reconnectDelay`) |

Chỉ cần 1 connection / tab. Không connect trên màn tenant mobile cho feature này.

---

## 8. Checklist FE

- [ ] Connect `wss://slms-api.duckdns.org/ws` (prod) / `ws://localhost:8080/ws` (local)
- [ ] STOMP CONNECT header `Authorization: Bearer <jwt>`
- [ ] Subscribe `/user/queue/billing`
- [ ] Nhận `event === "INVOICE_PAID"` → update row / refetch
- [ ] Reconnect khi token mới
- [ ] Deactivate khi logout
- [ ] Admin: thấy mọi hóa đơn vừa thanh toán
- [ ] Manager: chỉ thấy hóa đơn nhà mình quản lý (BE đã lọc)

---

## 9. Test nhanh

1. Admin/manager web login → mở danh sách hóa đơn → socket CONNECT + subscribe
2. Tenant app thanh toán 1 hóa đơn `PENDING` (PayOS QR)
3. Web **không F5** → row đó đổi `PAID` (hoặc toast + list cập nhật)

Nếu không nhận event:

- Sai URL (`ws` vs `wss`, thiếu `/ws`)
- Thiếu header `Authorization` lúc CONNECT
- Subscribe sai (phải là `/user/queue/billing`, không phải `/queue/billing`)
- User không phải ADMIN active / không phải manager của nhà đó
- BE chưa deploy bản có WebSocket

---

## 10. Ngoài phạm vi (chưa làm)

- Realtime tạo hóa đơn mới / quá hạn
- Mobile tenant subscribe socket
- SockJS fallback

> **Cập nhật 12/08/2026:** Realtime thanh toán **cọc / onboard** (`markDepositPaid`) đã bắn `INVOICE_PAID` cho hoá đơn `HD-ONBOARD-*` và FIRST RENT (nếu có) — cùng format event hiện tại.
