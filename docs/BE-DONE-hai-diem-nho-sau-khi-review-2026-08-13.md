# BE DONE — 2 điểm nhỏ sau review `731acad`

**Ngày:** 13/08/2026  
**Người gửi:** team BE  
**Người nhận:** team FE (mobile + web admin)  
**Phản hồi:** `BE-hai-diem-nho-sau-khi-review-2026-08-13.md`  
**Liên quan:** `731acad` *fix feedback from mentor* + `BE-DONE-catchup-thieu-thang-2026-08-13.md`

---

## Tóm tắt

Cả 3 ý FE báo đã sửa. **Không đổi URL / DTO / HTTP status.** FE bỏ dò chuỗi trong `message` và bỏ chấp nhận 2 thông báo trùng lúc onboard.

| # | Mức độ | Việc FE yêu cầu | Trạng thái |
|---|--------|-----------------|------------|
| 1 | 🟡 | `UTILITY_WINDOW_CLOSED` vào field `code` | ✅ Done |
| 2 | 🟡 | Manager không nhận 2 notification trùng khi tenant quét QR onboard | ✅ Done |
| 2b | 🟢 | `DEPOSIT_PAID_*` lưu `screen` / `paramsJson` để in-app điều hướng được | ✅ Done |

HTTP **vẫn 422** (không đổi sang 409). FE bắt `error.code`, không bắt status 409.

---

## 1. 🟡 `UTILITY_WINDOW_CLOSED` vào đúng field `code`

### Trước

Constructor 1 tham số → `code` mặc định `"BUSINESS_ERROR"`. Tiền tố `"409:"` chỉ là text trong `message`.

```json
{
  "status": 422,
  "code": "BUSINESS_ERROR",
  "message": "409: UTILITY_WINDOW_CLOSED - Đã quá hạn chót ..."
}
```

### Sau

Constructor 2 tham số, giống `METER_PHOTO_REQUIRED` / `VISION_DESCRIBE_*`.

```json
{
  "status": 422,
  "code": "UTILITY_WINDOW_CLOSED",
  "message": "Đã quá hạn chót phát hành hoá đơn tiền nhà kỳ này (2026-08-13)."
}
```

Điện/nước cùng `code`, `message` dạng:

```text
Đã quá hạn chót (2026-08-13), không thể tạo mới hoá đơn điện/nước cho kỳ này.
```

### Việc FE làm

- Bắt `error.code === 'UTILITY_WINDOW_CLOSED'` — **không** dò chuỗi trong `message`.
- HTTP status = **422** (`UNPROCESSABLE_ENTITY`). Không bắt 409.
- Có thể bỏ workaround parse `"409: UTILITY_WINDOW_CLOSED"` trong message.

### File BE

| File | Chỗ |
|------|-----|
| `TenantBillingServiceImpl.java` | Chặn tạo hoá đơn tiền nhà kỳ hiện tại sau hạn |
| `UtilityInvoiceServiceImpl.java` | Chặn tạo hoá đơn điện/nước kỳ hiện tại sau hạn |

---

## 2. 🟡 Bỏ notification trùng lúc onboard

Một lần tenant thanh toán QR onboard (có tiền nhà chu kỳ đầu) trước đây bắn **2 tin** cho cùng manager:

| Nguồn | Type | Tiêu đề | Còn / bỏ |
|-------|------|---------|----------|
| `markDepositPaid` | `DEPOSIT_PAID_MANAGER` | 💰 Khách đã thanh toán xong | ✅ Giữ — dẫn `ResumeContract` để bấm OTP |
| `recordPaidFirstRentFromOnboard` → `notifyManagerPaymentReceived` | `PAYMENT_RECEIVED_MANAGER` | 💰 Khách đã thanh toán | ❌ Bỏ khi FIRST RENT + `onboardPaid=true` |

`notifyManagerPaymentReceived` skip khi:

```
invoice.cycleType == FIRST  &&  isOnboardPaidInvoice(invoice)
```

(`isOnboardPaidInvoice` = note chứa `onboardPaid=true` hoặc bắt đầu `ONBOARD|`.)

### Việc FE làm

- Demo / list notification: **1 tin** `DEPOSIT_PAID_MANAGER` khi tenant quét QR onboard (kể cả có first rent).
- `PAYMENT_RECEIVED_MANAGER` **vẫn** bắn cho thanh toán hoá đơn thường (RENT / điện / nước / …) — không đổi.
- Tenant vẫn nhận `DEPOSIT_PAID_TENANT` như cũ.

---

## 2b. 🟢 `DEPOSIT_PAID_*` lưu `screen` / `paramsJson`

Trước: `screen` / `params` chỉ có trên **push data**. Bấm tin trong **danh sách in-app** không đi đâu.

Sau — bản ghi DB khớp push:

| Type | `screen` | `paramsJson` |
|------|----------|--------------|
| `DEPOSIT_PAID_MANAGER` | `ResumeContract` | `{"contractId": <id>}` |
| `DEPOSIT_PAID_TENANT` | `InvoiceList` | *(không có — giống push)* |

Push payload **không đổi**.

### Việc FE làm

- Bấm tin in-app `DEPOSIT_PAID_MANAGER` → `ResumeContract` + `params.contractId`.
- Bấm tin in-app `DEPOSIT_PAID_TENANT` → `InvoiceList`.
- Có thể bỏ fallback “push thì đi, in-app thì không”.

---

## Việc FE nên bỏ

- Parse `"UTILITY_WINDOW_CLOSED"` / `"409:"` trong `error.message`.
- Chấp nhận 2 notification manager lúc onboard (để demo).
- Hardcode điều hướng in-app cho `DEPOSIT_PAID_*` vì `screen`/`params` null.

---

## File đổi

| File | Việc |
|------|------|
| `TenantBillingServiceImpl.java` | `UTILITY_WINDOW_CLOSED` 2 tham số; skip notify FIRST RENT onboard |
| `UtilityInvoiceServiceImpl.java` | `UTILITY_WINDOW_CLOSED` 2 tham số |
| `TenantOnboardingServiceImpl.java` | `screen` / `paramsJson` cho `DEPOSIT_PAID_MANAGER` + `DEPOSIT_PAID_TENANT` |

---

## Checklist FE

- [ ] Bắt `error.code === 'UTILITY_WINDOW_CLOSED'` (HTTP 422)
- [ ] Onboard có first rent: manager chỉ thấy **1** tin `DEPOSIT_PAID_MANAGER`
- [ ] Thanh toán hoá đơn thường: manager vẫn nhận `PAYMENT_RECEIVED_MANAGER`
- [ ] Bấm tin in-app `DEPOSIT_PAID_MANAGER` → `ResumeContract`
- [ ] Bấm tin in-app `DEPOSIT_PAID_TENANT` → `InvoiceList`

---

## Ghi chú BE (không chặn FE)

`PERIOD_ALREADY_SETTLED` / `INVOICE_ALREADY_EXISTS` vẫn đang nhét code vào `message` (constructor 1 tham số) — **chưa** sửa vì FE không kêu trong file này. Nếu FE cần `code` đúng luôn 2 loại đó thì báo lại.
