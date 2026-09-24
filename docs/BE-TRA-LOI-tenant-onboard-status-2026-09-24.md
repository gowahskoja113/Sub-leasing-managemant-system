# BE trả lời — Pipeline đón khách `AWAITING_*` (24/09/2026)

Trả lời `BE-CAUHOI-tenant-onboard-status-2026-09-24.md`.  
**Đã sửa code** cho câu 1–3 (cùng ngày). FE có thể code theo các quyết định dưới đây.

---

## 1. Cron hủy no-show — có hủy nhầm HĐ đã PAID không?

### Quyết định

- **a) Không chủ ý hủy HĐ đã PAID.** Trước đây `onboardInProgress()` gồm cả `AWAITING_CONFIRM` là lỗ hổng.
- Tiền đã thu: **không hoàn tự động**. HĐ `AWAITING_CONFIRM` kẹt OTP quá hạn → manager/admin xử lý tay (nhắc OTP / hủy thủ công + hoàn nếu cần). BE chưa có job hoàn PayOS kèm no-show.
- **b) Đã sửa:** cron chỉ hủy `DRAFT | AWAITING_ONBOARD | AWAITING_PAYMENT` (`ContractStatus.unpaidOnboardCancelable()`), và skip thêm nếu `paymentStatus == PAID`.
- **c) Ngưỡng 3 ngày + mốc `expectedReceptionDate ?? moveInDate` là chủ ý đúng.**
  - Deploy: HĐ chưa trả tiền mà đã trễ ≥ 3 ngày sẽ bị hủy lần cron đầu — đúng nghiệp vụ no-show.
  - HĐ migrate `PENDING+PAID → AWAITING_CONFIRM` **không** còn bị nuốt (nhờ b).
  - Nên báo manager trước deploy nếu có nhiều draft/pending trễ lâu (4–10 ngày).

### FE làm gì

- Không cần CTA “cứu” HĐ PAID khỏi cron no-show.
- `AWAITING_CONFIRM` quá hạn: hiện badge/nhắc OTP, không giả định BE tự hủy.

---

## 2. Đón khách sớm — còn được không?

### Quyết định

- **a–b) Có — cho phép sớm** trong `contract.max-early-move-in-days` (mặc định 3), đồng bộ với cửa sổ OTP/activate vốn đã có.
- **Đã sửa:**
  - `completeCapture` từ `DRAFT` khi `hôm nay ≥ due − maxEarlyMoveInDays` (promote nội bộ → `AWAITING_ONBOARD` rồi sang `AWAITING_PAYMENT`).
  - Cron `promoteDraftsDueForOnboard` cũng promote sớm trong cùng cửa sổ.
  - Không thêm API “đón sớm” riêng — dùng flow hiện tại.
- **c) Không chặn sớm.**

### FE làm gì

- Hiện CTA chụp / completeCapture khi trong cửa sổ sớm (due − 3 ngày ≤ hôm nay).
- Ngoài cửa sổ: ẩn nút / hiện “Chưa tới cửa sổ đón khách (sớm tối đa N ngày…)”.

---

## 3. Đón trễ + tạo QR

### Quyết định

- **Đã sửa:** bỏ check `moveInDate.isBefore(today)`.
- Cửa sổ tạo QR: cùng mốc `expectedReceptionDate ?? moveInDate`:
  - sớm ≤ `maxEarlyMoveInDays`
  - trễ &lt; `noShowGraceDays` (còn trong grace thì vẫn tạo được QR)
- **Không** bắt manager đổi `moveInDate` khi đón trễ 1–2 ngày.
- `PUT /tenant-contracts/{id}` vẫn chỉ sửa hiện trạng ở `DRAFT | AWAITING_ONBOARD` — không đổi rule này.

### FE làm gì

- Ở `AWAITING_PAYMENT` trong grace: hiện nút Tạo QR bình thường.
- Quá grace: BE sẽ reject + cron hủy — FE có thể disable theo ngày đón + 3 ngày.

---

## 4. `completeCapture` — override có quá lỏng không?

### Quyết định

- **Chủ ý dựa validate lúc lưu** (`requireMeterEvidence` + `applyMeterOverridesIfAny` trong `updateDraftContract` / `createTenantContract`).
- `hasMeterOverrideRecord` = “đã có chỉ số, không còn ảnh” sau khi override token đã được consume lúc save — không phải check token sống lại.
- `completeCapture` thường đi kèm PUT cùng request (hoặc sau PUT đã lưu đủ) → đủ an toàn cho pipeline hiện tại.
- **Chưa siết thêm** check `MeterOverridePasscode` trong `completeCapture` (tránh double-consume / phụ thuộc audit table). Nếu sau này có API modify chỉ số lách validate, sẽ bổ sung.

### FE làm gì

- Luôn gửi đủ ảnh hoặc override token ở bước lưu trước/`cùng` PUT `completeCapture:true`. Không gọi completeCapture “trần” trên HĐ thiếu bằng chứng.

---

## 5. Response đủ dựng UI 4 bước chưa?

### a) `statusLabel`

| Endpoint / DTO | Có `statusLabel`? |
|---|---|
| `GET /tenant-contracts` (+ `?status=RECEPTION`) → `TenantContractResponse` | ✅ |
| `GET .../managed` → `TenantContractResponse` | ✅ |
| `GET /properties/{id}/tenant-contracts` → `TenantContractResponse` | ✅ |
| `GET /tenant/me/contracts` (alias `/me/tenant-contracts`) → `TenantContractResponse` | ✅ (không còn DTO `MyContractListItem` riêng) |
| `GET /host/contracts` → `HostContractDto` | ❌ chỉ có `status` (string enum name) |

### b) OTP / payment fields

Trên **`TenantContractResponse`** (các endpoint admin/manager/tenant ở trên): ✅  
`paymentStatus`, `depositPaidAt`, `tenantOtpVerifiedAt`, `managerOtpVerifiedAt`, `confirmRequestedAt`.

`HostContractDto`: ❌ không có các field này (Host portal không dựng pipeline đón khách 4 bước).

### c) Alias `RECEPTION` cho Host?

- **Không** trên `/host/contracts` — Host parse `ContractStatus` từng giá trị hoặc `PENDING` (= chờ duyệt giá).
- Admin/Owner: `GET /api/v1/tenant-contracts?status=RECEPTION`.
- Manager: `GET .../managed?status=RECEPTION` (đã hỗ trợ alias).
- Owner 403 với một số path detail là rule phân quyền hiện tại — dùng list admin hoặc endpoint đúng role.

### FE làm gì

- Màn đón khách Admin/Manager: dùng `TenantContractResponse` + `status=RECEPTION`.
- Host: map `status` enum name nếu cần badge; không dựa `statusLabel` / OTP timestamps.

---

## 6. Xác nhận nhỏ

- **a) Đúng.** Onboard deposit → `AWAITING_CONFIRM` chỉ qua PayOS (`markDepositPaid` webhook + `syncPaymentStatus`). Không còn API thu tiền mặt/CK tay cho bước cọc onboard. Field `depositCash*` là legacy đọc lại. (Thu tiền mặt hóa đơn tháng vẫn là luồng billing riêng.)
- **b)** Tạo không draft + `requireDepositPayment` → thẳng `AWAITING_PAYMENT`; chỉ số/ảnh đã validate trong `createTenantContract`. **Không** cần gọi `completeCapture` thêm.
- **c)** Sau sửa câu 1: HĐ migrate `PAID → AWAITING_CONFIRM` **không** bị cron no-show. HĐ migrate chưa trả (`→ AWAITING_PAYMENT`) vẫn theo grace 3 ngày từ ngày đón — đúng nghiệp vụ; không đóng băng thêm lúc boot.

---

## Tóm tắt cho FE (ưu tiên code)

1. List đón khách: `?status=RECEPTION` (Admin) / managed `?status=RECEPTION` (Manager).
2. Badge: `status` + `statusLabel` trên `TenantContractResponse`.
3. Tách `AWAITING_CONFIRM`: dùng `tenantOtpVerifiedAt` / `managerOtpVerifiedAt`.
4. Đón sớm ≤ 3 ngày: cho phép completeCapture + tạo QR.
5. Đón trễ &lt; 3 ngày: vẫn tạo QR được; ≥ 3 ngày + chưa PAID → BE hủy.
6. Đã PAID (`AWAITING_CONFIRM`): cron **không** hủy — UI nhắc OTP.
