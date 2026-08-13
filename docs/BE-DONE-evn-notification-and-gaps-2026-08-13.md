# BE DONE — Thông báo hoá đơn EVN + 3 lỗ hổng API

**Ngày:** 13/08/2026  
**Người gửi:** team BE  
**Người nhận:** team FE (mobile + web admin)  
**Phản hồi:** `BE-NEED-evn-notification-and-gaps-2026-08-13.md`

---

## Tóm tắt

| # | Mức độ | Việc FE yêu cầu | Trạng thái |
|---|--------|-----------------|------------|
| 1 | 🔴 | Thông báo in-app + push khi admin phát hành EVN | ✅ Done |
| 2 | 🟡 | `GET /admin/evn-bills` không bắt buộc `propertyId` | ✅ Done |
| 3 | 🔴 | `GET /tenant-contracts/managed` luôn rỗng | ✅ Done |
| 4 | 🟢 | `contractId` + `contractStatus` trên `ManagerInvoiceResponse` | ✅ Done |
| 5 | 🟢 | Bỏ `deposit` khỏi response manager invoice | ✅ Done — `/manager/deposits` giữ nguyên |

---

## 1. Thông báo EVN — `EVN_BILL_PUBLISHED`

`POST /api/v1/admin/evn-bills` (nút “Gửi cho quản lý”) sau khi lưu hoá đơn:

- INSERT 1 dòng `notifications` cho **manager phụ trách nhà** (`operationManagerId`, fallback `managedBy`) — không broadcast
- Expo push qua token đã đăng ký lúc login
- Nhà không có manager → skip notify, hoá đơn vẫn tạo

### Payload in-app + push

```json
{
  "type": "EVN_BILL_PUBLISHED",
  "title": "Đã có hoá đơn điện kỳ 8/2026",
  "content": "MTX#13 · THEO_PHONG · 199 kWh · đơn giá 2.008đ/kWh",
  "screen": "UtilityBilling",
  "params": { "propertyId": 68 }
}
```

`type` chứa `EVN` — FE map đúng màn Ghi chỉ số (`navigationRef.ts`).  
`THEO_PHONG` / `NGUYEN_CAN` theo `property.wholeHouse`. Chuỗi `"full NT"` không có trong model — không gửi.

### Việc FE làm

- Không sửa màn chuông: đọc `/api/v1/notifications` là hiện
- Bấm tin → `UtilityBilling` + `params.propertyId`
- Bỏ vòng tự sinh thông báo phía client (nếu còn)

---

## 2. `GET /api/v1/admin/evn-bills` — `propertyId` tuỳ chọn

Query cũ: `e.property.id = :propertyId` → `null` thành `= NULL` → `[]`.

Giờ: `(:propertyId IS NULL OR e.property.id = :propertyId)` + `month`/`year` vẫn optional.

| Call | Kết quả |
|---|---|
| `?propertyId=29` | hoá đơn nhà 29 |
| `?propertyId=29&month=8&year=2026` | kỳ 8/2026 nhà 29 |
| `?month=8&year=2026` | **mọi nhà** kỳ 8/2026 |
| (không param) | mọi hoá đơn EVN |

### Việc FE làm

- Bỏ `listForPeriod` (loop 8/49 request)
- Gọi một lần `GET /admin/evn-bills?month=&year=`

---

## 3. `GET /api/v1/tenant-contracts/managed` — hết rỗng

Hai lỗi:

1. Query lọc `p.managedBy` trong khi nhà gán manager qua `operationManagerId` → luôn `[]`
2. `?status=ACTIVE` bị parse thành `PriceApprovalStatus` → catch → `[]` im lặng

Đã đổi: `operationManagerId OR managedBy`. `status` thử `ContractStatus` trước, rồi `PriceApprovalStatus`.

| Call | Kết quả |
|---|---|
| `/managed` | pipeline chờ xử lý (duyệt giá / DRAFT / PENDING) — **không** gồm ACTIVE |
| `/managed?status=ACTIVE` | HĐ ACTIVE của nhà manager phụ trách |
| `/managed?status=TERMINATED` | HĐ đã chấm dứt |
| `/managed?status=PENDING_PRICE_APPROVAL` | lọc duyệt giá (như cũ) |
| `GET /tenant-contracts` (manager) | cùng logic `/managed` |

`GET /properties/{id}/tenant-contracts` không đổi.

### Việc FE làm

- List HĐ đang ở: `GET /tenant-contracts/managed?status=ACTIVE` — **bỏ** `listActiveByProperties`
- Màn chờ xử lý: vẫn `GET /managed` không `status`
- Đừng coi `[]` không-status là “manager không có HĐ nào”

---

## 4. `ManagerInvoiceResponse` — `contractId` + `contractStatus`

`GET /api/v1/manager/invoices` và `GET /api/v1/manager/invoices/{id}`:

```json
{
  "contractId": 123,
  "contractStatus": "ACTIVE"
}
```

`contractStatus`: `DRAFT` / `PENDING` / `ACTIVE` / `EXPIRED` / `TERMINATED`.

### Việc FE làm

- Lọc việc-cần-làm: `contractStatus === 'ACTIVE'` (hoặc bỏ `TERMINATED`)
- Bỏ ghép thủ công `(propertyId, roomNumber)` với list HĐ từng nhà

---

## 5. Manager invoice — không còn tiền cọc

Policy cũ: chỉ mask số RENT. Cọc vẫn lọt qua `paymentBreakdown.depositAmount` / dòng “Tiền cọc” / `amount` hoá đơn ONBOARD.

Manager invoice giờ:

- `depositAmount` / `depositMonths` = `null`
- line `depositAmount` / `depositMonths` bị gỡ
- item label chứa `"Tiền cọc"` bị gỡ
- `amount` / `totalAmount` trừ phần cọc (ONBOARD còn tiền nhà chu kỳ đầu thì giữ phần đó)

**Giữ nguyên:** `GET /api/v1/manager/deposits` — API cọc riêng.

Admin invoice không đổi.

---

## File đổi

| File | Việc |
|---|---|
| `EvnBillServiceImpl.java` | notify `EVN_BILL_PUBLISHED` + Expo push |
| `EvnBillRepository.java` | `propertyId` optional |
| `TenantContractRepository.java` | `operationManagerId OR managedBy` |
| `TenantOnboardingServiceImpl.java` | parse `ContractStatus` (ACTIVE, …) |
| `TenantContractActionController.java` | comment API |
| `ManagerInvoiceResponse.java` | `contractId`, `contractStatus` |
| `ManagerBillingServiceImpl.java` | map contract + strip deposit |

---

## Checklist FE

- [ ] Admin “Gửi cho quản lý” → manager thấy chuông + push, type `EVN_BILL_PUBLISHED`, mở `UtilityBilling`
- [ ] Admin EVN: `GET /admin/evn-bills?month=8&year=2026` ra đủ nhà, không loop
- [ ] Manager: `GET /tenant-contracts/managed?status=ACTIVE` có HĐ
- [ ] Manager: `GET /managed` không-status chỉ pipeline chờ xử lý
- [ ] `/manager/invoices` có `contractId` / `contractStatus` — lọc HĐ đã chấm dứt
- [ ] `/manager/invoices` không còn số cọc; `/manager/deposits` vẫn có
