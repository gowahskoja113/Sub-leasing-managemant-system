# BE-DONE — Dual OTP: 3 việc còn lại + báo cáo đối chiếu

Ngày: 2026-08-31  
Phạm vi: **chỉ BE** (`slms2026`)  
Đối chiếu từ: `BE-YEUCAU-xac-nhan-hop-dong-2-otp-2026-08-27.md`

---

## Tóm tắt

| # | Việc (doc mục 0) | Trạng thái | Ghi chú |
|---|---|---|---|
| 1 | `GEMINI_API_KEY` trên deploy + tải ảnh song song | ✅ Code xong | Env var cần set trên server (Render/VPS) |
| 2 | Chỉ số đồng hồ cũ còn phần lẻ → `CONSUMPTION_MISMATCH` | ✅ Code xong | Migrate làm tròn + tolerance ±1 |
| 3 | Cột `confirm_requested_at` trên `tenant_contracts` | ✅ Code xong | FE có thể đọc qua `GET /tenant-contracts/{id}` |
| — | Khách PAID rồi bỏ dở, HĐ kẹt PENDING (mục 5) | ⏸ Chưa làm | Nghiệp vụ chưa quyết |

Luồng dual OTP chính (mục 1–2 doc) **đã có từ trước** — bug đua confirm đã sửa ở commit `3299622`. Lần này chỉ bổ sung 3 việc còn lại.

---

## 1. `confirm_requested_at` — mốc "khách đã bấm gửi OTP"

### Vấn đề

BE chỉ lưu hai mốc *đã verify* (`tenant_otp_verified_at`, `manager_otp_verified_at`).
Không phân biệt được "khách chưa bấm gửi" vs "đã gửi rồi, chưa ai nhập".

### Thay đổi

| Thành phần | Chi tiết |
|---|---|
| DB | Cột `tenant_contracts.confirm_requested_at TIMESTAMP` (migration idempotent) |
| Entity | `TenantContract.confirmRequestedAt` |
| Response | `TenantContractResponse.confirmRequestedAt` |
| Ghi mốc | Lần đầu khách gọi `POST /api/v1/tenant/me/contracts/{id}/send-confirm-otp` → `sendDualContractConfirmOtps` set `now()` nếu null, rồi `saveAndFlush` |
| Không ghi | `POST /api/v1/tenant-contracts/{id}/send-otp` (quản lý xin lại mã) — đúng nghiệp vụ |

### FE có thể suy

```
confirmRequestedAt == null          → khách chưa bấm gửi OTP
confirmRequestedAt != null
  && tenantOtpVerifiedAt == null    → đã gửi, chờ khách nhập mã
tenantOtpVerifiedAt != null         → khách đã xác nhận (chờ quản lý nếu manager chưa verify)
```

Field mới **không bắt buộc** — FE cũ vẫn chạy; panel quản lý có thể nâng cấp sau.

---

## 2. Chỉ số đồng hồ — số nguyên + dữ liệu cũ còn lẻ

### Vấn đề

Từ 27/08 FE chỉ ghi phần **đen** (số nguyên); phần đỏ chỉ để làm tròn.
Dữ liệu cũ trong DB vẫn có phần lẻ → kỳ hoá đơn đầu: `newReading − prevReading` lệch
tới ±1 so với `consumption` FE gửi → `validateInvoiceAmounts` ném `CONSUMPTION_MISMATCH`.

### Thay đổi

**A. Migration một lần** — `DatabaseSchemaMigration.roundLegacyMeterReadingsToIntegers()`:

| Bảng | Cột làm tròn `ROUND(...)` |
|---|---|
| `tenant_contracts` | `initial_electric_reading`, `initial_water_reading` |
| `utility_invoices` | `prev_reading`, `new_reading`, `consumption` |
| `meter_readings` | `reading` |

Chỉ UPDATE hàng có giá trị khác `ROUND(giá trị)`. Log số dòng đã sửa khi khởi động app.

**B. Validation** — `UtilityInvoiceServiceImpl.validateInvoiceAmounts`:

- Trước: so khớp tuyệt đối `newReading − prevReading == consumption`
- Sau: cho phép lệch **±1 đơn vị** (belt-and-suspenders nếu còn sót dữ liệu lẻ)

`AMOUNT_MISMATCH` vẫn giữ tolerance ±1đ như cũ.

---

## 3. Gemini — mô tả hiện trạng phòng trên deploy

### Vấn đề

- `.dockerignore` loại `.env` → image deploy **không có** `GEMINI_API_KEY` local
- `GeminiRoomDescribeProvider` tải ảnh **tuần tự** (timeout 20s/ảnh) → request nhiều ảnh dễ timeout

### Thay đổi

| Thành phần | Chi tiết |
|---|---|
| Config | `application.yaml` đã có `vision.gemini.api-key: ${GEMINI_API_KEY:}` — không đổi |
| Deploy | Ghi chú trong `Dockerfile`: cần set env `GEMINI_API_KEY` trên server |
| Code | `buildRequestBody`: tải ảnh **song song** qua `CompletableFuture.supplyAsync` |

### Ops cần làm (không nằm trong repo)

Trên Render / VPS, thêm biến môi trường:

```
GEMINI_API_KEY=<key từ Google AI Studio>
```

Tuỳ chọn: `GEMINI_MODEL`, `VISION_GEMINI_TIMEOUT_SECONDS`.

---

## File đụng

```
src/main/java/com/sep490/slms2026/
  config/DatabaseSchemaMigration.java
  entity/TenantContract.java
  dto/response/TenantContractResponse.java
  service/impl/TenantOnboardingServiceImpl.java
  service/impl/TenantContractDocumentServiceImpl.java
  service/impl/UtilityInvoiceServiceImpl.java
  vision/GeminiRoomDescribeProvider.java
Dockerfile
```

---

## FE cần làm (tuỳ chọn)

| Việc | Bắt buộc? |
|---|---|
| Đọc `confirmRequestedAt` từ `GET /tenant-contracts/{id}` để hiện "khách chưa gửi" | Không — FE đã né được |
| Không sửa đường dẫn OTP / DTO confirm | Không — không đổi so với `bd2503b` |
| Set `GEMINI_API_KEY` trên server deploy | Có — nếu dùng nút mô tả hiện trạng phòng |

---

## Kiểm chứng đề xuất

### Dual OTP (doc mục 6 — chạy lại sau deploy)

| # | Việc | Kỳ vọng |
|---|---|---|
| 4 | `send-confirm-otp` | 2 bản ghi OTP, 2 mã khác nhau, 2 purpose |
| 5 | Khách nhập đúng | PENDING, `tenantOtpVerifiedAt` có giá trị |
| 7 | Quản lý nhập đúng | ACTIVE, phòng RENTED, 1 hoá đơn prorated |
| 9 | Hai lệnh confirm song song | Không kẹt, không duplicate activate |

### Việc mới (31/08)

1. Khách gọi `send-confirm-otp` lần 1 → `confirmRequestedAt` có giá trị; gọi lại → không đổi mốc
2. Quản lý gọi `send-otp` → `confirmRequestedAt` không bị ghi đè
3. HĐ có `initial_electric_reading = 19200.5` → sau restart app = `19201` (ROUND)
4. Tạo hoá đơn với prev lẻ còn sót + consumption làm tròn → không `CONSUMPTION_MISMATCH` nếu lệch ≤ 1
5. `POST /vision/describe-room` với 3+ ảnh + `GEMINI_API_KEY` set → không timeout do tải tuần tự

---

## Chưa làm — mục 5 doc (nghiệp vụ)

**Khách thanh toán xong rồi bỏ dở, không bao giờ xác nhận.**

- Tiền đã thu, phòng bị giữ, HĐ kẹt `PENDING`
- Cần quyết: cron nhắc sau 24h? ADMIN kích hoạt thủ công? huỷ + hoàn tiền?
- **Chưa có gì trong code** — chờ product quyết trước khi implement

---

## Tham chiếu commit / doc

- Yêu cầu gốc: `BE-YEUCAU-xac-nhan-hop-dong-2-otp-2026-08-27.md`
- Dual OTP core: commit `bd2503b`, bug đua confirm: `3299622`
- Điện/nước mốc đón khách: `BE-DONE-tinh-dien-nuoc-theo-moc-don-khach-2026-08-27.md`
