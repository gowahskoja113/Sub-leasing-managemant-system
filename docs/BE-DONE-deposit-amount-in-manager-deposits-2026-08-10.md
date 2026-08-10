# BE DONE — `/api/v1/manager/deposits` trả số tiền cọc + `invoiceType` trên payments

**Ngày:** 10/08/2026  
**Người gửi:** team BE  
**Người nhận:** team FE (mobile)  
**Phản hồi:** `BE-NEED-deposit-amount-in-manager-deposits-2026-08-10.md`  
**Màn liên quan:** app quản lý → **Thu & Đối soát** (`ManagerPaymentHistoryScreen`)

---

## Tóm tắt

Manager được xem **số tiền cọc** trong danh sách deposits; payment claim kèm **loại hoá đơn** để FE không phải đoán từ `invoiceCode`.

| # | Việc FE yêu cầu | Trạng thái |
|---|-----------------|------------|
| 1 | `ManagerDepositDto.deposit` = `contract.getDeposit()` | ✅ Done |
| 2 | `ManagerPaymentResponse.invoiceType` (RENT \| ELECTRICITY \| …) | ✅ Done (bonus trong NEED) |

---

## 1. Deposits — thêm `deposit`

### API

```http
GET /api/v1/manager/deposits?status=&page=&size=
```

### Field mới trên mỗi phần tử page

| Field | Type | Nguồn | Ghi chú |
|-------|------|--------|---------|
| `deposit` | `BigDecimal` \| `null` | `TenantContract.deposit` | Số tiền cọc (VND). Có thể null nếu HĐ chưa set. |

### Payload mẫu (rút gọn)

```json
{
  "content": [
    {
      "contractId": 12,
      "contractCode": "HD-...",
      "propertyName": "Nhà A",
      "roomNumber": "101",
      "tenantName": "Nguyễn Văn A",
      "tenantPhone": "09...",
      "depositMonths": 1,
      "deposit": 3000000,
      "paymentStatus": "PAID",
      "depositMethod": "PAYOS",
      "depositPaidAt": "2026-08-01T10:00:00",
      "contractStatus": "ACTIVE",
      "moveInDate": "2026-08-01"
    }
  ]
}
```

### Việc FE nên bỏ

- Gọi `GET /api/v1/tenant-contracts/{id}` chỉ để lấy `deposit`
- Hàm cache / lazy `ensureDepositAmount` (và tương đương) trong `PaymentHistoryScreen.tsx`

Dùng thẳng `item.deposit` từ list deposits.

---

## 2. Payments — thêm `invoiceType`

### API liên quan

| Method | Path | Ghi chú |
|--------|------|---------|
| GET | `/api/v1/manager/...` list payments (claim) | Response dùng `ManagerPaymentResponse` |
| POST/PATCH verify / reject claim | cùng DTO | Cũng có `invoiceType` |

### Field mới

| Field | Type | Giá trị | Nguồn |
|-------|------|---------|--------|
| `invoiceType` | `string` \| `null` | `RENT`, `ELECTRICITY`, `WATER`, `SERVICE`, `MAINTENANCE`, `OTHER` | `TenantInvoice.invoiceType.name()` |

### Payload mẫu (rút gọn)

```json
{
  "id": 42,
  "invoiceCode": "HD-RENT-23-2026-08",
  "invoiceType": "RENT",
  "tenantName": "Nguyễn Văn A",
  "roomNumber": "101",
  "propertyName": "Nhà A",
  "amount": 5000000,
  "method": "TRANSFER",
  "status": "PENDING_VERIFY",
  "transferContent": "...",
  "createdAt": "2026-08-10T09:00:00",
  "verifiedAt": null
}
```

### Việc FE nên bỏ

- Regex / heuristic đoán loại HĐ từ `invoiceCode` (`HD-RENT-…`, `INV…-R` / `-E` / `-W` / `-S`, …)
- Dùng `invoiceType === 'RENT'` (và các type utility/service) để quyết định **ẩn/hiện số tiền** theo rule màn đối soát

---

## File BE đã đổi

| File | Thay đổi |
|------|----------|
| `dto/response/ManagerDepositDto.java` | + `BigDecimal deposit` |
| `dto/response/ManagerPaymentResponse.java` | + `String invoiceType` |
| `service/impl/ManagerBillingServiceImpl.java` | map `deposit`, `invoiceType` |

Không đổi path URL, phân trang, filter status.

---

## Checklist FE

- [ ] Deposits: bind `deposit` hiển thị / đối soát cọc
- [ ] Deposits: xoá N+1 `getContract` + cache `ensureDepositAmount`
- [ ] Payments: bind `invoiceType` thay cho parse code
- [ ] Payments: rule ẩn tiền thuê (`RENT`) / hiện điện–nước–dịch vụ dùng field mới
- [ ] Smoke: list deposits + list payments trên màn Thu & Đối soát

---

## Ghi chú

- Rule **ẩn số tiền hoá đơn thuê** trên list invoices manager (`ManagerInvoiceResponse`) **không đổi** trong ticket này; chỉ deposits được mở số tiền cọc.
- `ManagerPaymentResponse.amount` vẫn trả như trước; FE tự ẩn UI theo `invoiceType` nếu cần.
