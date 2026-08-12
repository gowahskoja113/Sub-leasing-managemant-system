# Tóm tắt 2 commit mới nhất trên nhánh `dev`

**Nhánh:** `dev`  
**Ngày tóm tắt:** 12/08/2026  
**Phạm vi:** 2 commit mới nhất (theo thời gian commit)

---

## Tổng quan

| # | Hash (ngắn) | Thời gian | Message | Tác giả |
|---|-------------|-----------|---------|---------|
| 1 (mới nhất) | `609de59` | 12/08/2026 21:47 +0700 | `update new payment process` | ngoc son |
| 2 | `6b7f099` | 12/08/2026 17:39 +0700 | `add websocket update realtime` | ngoc son |

---

## 1. `609de59` — update new payment process

**Mục đích:** Đổi quy trình thanh toán onboard: một QR PayOS thu **tiền cọc + tiền nhà pro-rata chu kỳ đầu** (trước đây chỉ thu cọc).

### Thay đổi chính

- **Onboard PayOS:** tổng phải trả = cọc + tiền nhà chu kỳ đầu (pro-rata theo ngày vào ở). Nếu defer ≤ 3 ngày cuối tháng thì tiền nhà chu kỳ đầu = 0 (không gộp vào QR).
- **Sau khi thanh toán onboard thành công:** ghi nhận hóa đơn tiền nhà chu kỳ đầu `RENT` / `FIRST` với status `PAID` (không tạo `TenantPayment` riêng), đánh dấu `onboardPaid=true` trong note.
- **Tránh thu trùng:** nếu tiền nhà chu kỳ đầu đã thu qua onboard thì luồng phát hành / thu tiền nhà thường sẽ bỏ qua.
- **Breakdown / response:** cập nhật mô tả và công thức hiển thị (cọc + first rent), hỗ trợ parse note mới và dữ liệu legacy.
- **Test:** cập nhật `RentFirstCycleCalculatorTest`, `TenantContractPaymentAmountsTest`.

### Files (9 files, +315 / −65)

| File | Vai trò |
|------|---------|
| `TenantContractResponse.java` | Javadoc: tổng onboard = cọc + pro-rata |
| `TenantBillingService.java` / `Impl` | Thêm `recordPaidFirstRentFromOnboard`, check `hasFirstRentPaidViaOnboard` |
| `TenantOnboardingService.java` / `Impl` | QR onboard gộp cọc + first rent; notification / invoice note cập nhật |
| `PaymentBreakdownBuilder.java` | Breakdown QR & invoice phản ánh first rent |
| `TenantContractPaymentAmounts.java` | `resolveFirstRentCycle`, `resolveFirstRentAmount`, tổng onboard mới |
| Test util liên quan | Đồng bộ kỳ vọng số tiền / công thức |

### Ảnh hưởng nghiệp vụ

- Tenant thanh toán **một lần** lúc onboard (cọc + tháng đầu pro-rata), thay vì chỉ cọc rồi trả tiền nhà riêng sau khi HĐ ACTIVE.
- Billing sau onboard không phát hành lại tiền nhà chu kỳ đầu nếu đã thu qua QR onboard.

---

## 2. `6b7f099` — add websocket update realtime

**Mục đích:** Thêm tài liệu hướng dẫn FE subscribe WebSocket/STOMP để cập nhật realtime khi hóa đơn tenant chuyển `PAID`.

### Nội dung tài liệu

File mới: `docs/FE-websocket-invoice-paid-realtime-2026-08-12.md`

- **Mục tiêu:** Admin/Manager web thấy hóa đơn `PAID` ngay khi tenant thanh toán trên app, không cần F5.
- **Endpoint:** STOMP over native WebSocket (`/ws`); không dùng SockJS.
- **Auth:** JWT trên header STOMP `CONNECT` (`Authorization: Bearer ...`).
- **Subscribe:** `/user/queue/billing` (BE route theo user; ADMIN nhận mọi invoice PAID, MANAGER chỉ nhà mình quản lý).
- **Event:** `INVOICE_PAID` (payload có `invoiceId`, `status`, `paidAt`, … — không có số tiền).
- **FE:** dùng `@stomp/stompjs`, cập nhật row UI hoặc refetch list invoices.

### Files

| File | Vai trò |
|------|---------|
| `docs/FE-websocket-invoice-paid-realtime-2026-08-12.md` | Spec tích hợp realtime cho FE (+210 dòng) |

> Commit này **chỉ thêm docs**; không đổi code runtime trong diff commit.

---

## Thứ tự đọc / triển khai gợi ý

1. **Payment process (`609de59`)** — ảnh hưởng BE onboarding/billing; cần regression QR PayOS, invoice FIRST/RENT, tránh double charge.
2. **WebSocket docs (`6b7f099`)** — FE follow doc để subscribe và cập nhật UI khi `INVOICE_PAID`.
