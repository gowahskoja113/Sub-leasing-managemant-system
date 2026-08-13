# BE DONE — giờ server + tắt nhắc kỳ đầu + hoá đơn onboard

**Ngày:** 13/08/2026  
**Người gửi:** team BE  
**Người nhận:** team FE (mobile + web admin)  
**Phản hồi:**
- `BE-gio-server-2026-08-13.md`
- `BE-tat-nhac-ky-dau-2026-08-13.md` (gồm 1b `paymentMethod`, 1c dòng “Giá thuê / tháng”)

**Commit trên `dev`:**
- `f42ddde` *wick fix* — CORS `Date`, `capturedAt` server, tắt nhắc kỳ đầu, sửa `items` HD-ONBOARD
- `898f96c` *fix hehe* — `paymentMethod` → `QR`, bỏ dòng `rentAmount` khỏi breakdown onboard

FE **không** bắt buộc deploy lại cho CORS `Date`. Các mục hoá đơn / nhắc kỳ đầu: FE đã dọn UI thì giữ; có thể bỏ vài workaround (mục “FE nên làm”).

---

## Tóm tắt

| # | Việc FE xin | Trạng thái |
|---|-------------|------------|
| A | Expose header `Date` (CORS web prod) | ✅ |
| A | `capturedAt` ảnh bằng chứng do BE đóng dấu | ✅ |
| A | `terminateContract` check giờ server? | ✅ Đã có từ trước |
| B | Tắt nhắc / quá hạn / đề nghị chấm dứt kỳ đầu | ✅ |
| B | Không phát FIRST lần 2 nếu QR onboard đã thu | ✅ Đã chặn + huỷ trùng |
| B | `items` HD-ONBOARD khớp `grandTotal` / breakdown | ✅ |
| B | `month`/`year` null → “Tháng null/” | ✅ HĐ **mới** có tháng/năm; HĐ cũ FE đã vá |
| 1b | `paymentMethod = OTHER` / `PAYOS` → hiện “Khác” | ✅ Trả **`QR`** |
| 1c | Dòng “Giá thuê / tháng” trong breakdown onboard | ✅ Bỏ khỏi `lines` |

---

## A. Giờ server (web prod đọc được)

Header `Date` đã thêm vào CORS `exposed-headers` (`Authorization`, `Date`).

| Môi trường | Đọc `Date`? |
|---|---|
| Mobile native | ✅ (không CORS) |
| Web / mobile qua proxy same-origin | ✅ |
| **Web prod Vercel → domain BE** | ✅ sau commit này |

`utils/serverTime.ts` tự ăn, không cần API mới.

Nếu nginx **tự ghi** CORS (không để Spring trả), thêm:

```nginx
add_header Access-Control-Expose-Headers "Authorization, Date" always;
```

### `capturedAt`

BE **luôn** ghi giờ server lúc lưu ảnh công tơ / hiện trạng. Field `capturedAt` FE gửi bị bỏ. `roomConditionUrls` legacy vốn đã stamp server — giờ hai đường giống nhau.

### `terminateContract`

BE đã kiểm tra bằng `LocalDate.now()` (giờ server): loại `VIOLATION` cần hoá đơn tiền phòng `dueDate < today − 2`. Chỉnh giờ máy không qua được.

---

## B. Tắt nhắc tiền nhà chu kỳ đầu

Từ khi QR onboard thu **cọc + tiền nhà kỳ đầu**, hoá đơn `HD-ONBOARD-{id}` **PAID** lúc HĐ ACTIVE → không còn khoản kỳ đầu chưa trả.

BE đã:
- Xoá nhánh `RENT_FIRST_CYCLE_REMINDER` (D+1→D+3), `RENT_FIRST_CYCLE_OVERDUE`, `RENT_FIRST_CYCLE_MANAGER` trong `runDailySweep`.
- Không set `terminationProposed` theo nhánh kỳ đầu.
- Kỳ thường (dueDate + `graceDays` admin / BillingRulesCard) **giữ nguyên**.

`billing.first-cycle-grace-days` trong yaml = **legacy tên**. Không còn dùng để nhắc kỳ đầu. Vẫn dùng khi phát REGULAR mà mốc hạn đã qua (catch-up) → `dueDate = today + N`. **Không** nhầm với `graceDays` admin.

### Còn tạo RENT `cycleType = FIRST` không?

| Trường hợp | Có tạo? | Ghi chú |
|---|---|---|
| Sau QR onboard | Có — `HD-RENT-…` **PAID**, `onboardPaid=true` | Sổ cái, **không** đòi thêm |
| ACTIVE không qua QR / defer | Có thể FIRST **PENDING** | Sweep xử lý như tiền nhà thường (`dueDate`) |
| QR đã thu nhà + FIRST PENDING cũ | **CANCELLED** lúc sweep | Tránh đòi 2 lần |

`RENT_FIRST_DEFERRED` (≤3 ngày cuối tháng) gộp tháng sau với `cycleType = **REGULAR**` (+ `deferredAmount` trong note). FE coi hoá đơn thường là đúng.

### Dữ liệu cũ (onboard 10–12/08)

FIRST PENDING/OVERDUE **không** nằm trong QR gộp → giữ, hiện như hoá đơn thường (`dueDate` / `status`). Muốn huỷ tay thì báo BE.

---

## C. Hoá đơn `HD-ONBOARD-*`

### `items` khớp `grandTotal`

Trước: dòng “Tiền nhà tháng đầu” = **giá thuê nguyên tháng** (5.000.000) + cọc 5.000.000 → cộng 10tr trong khi `grandTotal` = 8.064.516.

Sau: dòng **“Tiền nhà chu kỳ đầu”** = pro-rata (`firstRentAmount`, hoặc `grandTotal − cọc` nếu note cũ). VD `HD-ONBOARD-9`:

| Dòng | Số |
|---|---|
| Tiền nhà chu kỳ đầu | 3.064.516 |
| Tiền cọc (1 tháng) | 5.000.000 |
| **Cộng = `grandTotal`** | **8.064.516** |

### `month` / `year`

HĐ onboard **mới**: fill từ kỳ pro-rata (`periodStart`). HĐ cũ vẫn null — FE đã `billMonthLabel()` ẩn “Tháng null/”.

### 1b. `paymentMethod`

PayOS nội bộ từng ghi `PAYOS` → FE không map → **“Khác”**.

| Ghi | FE nhận |
|---|---|
| PayOS (mới + map response data cũ) | **`QR`** |
| Tiền mặt (nếu còn) | `CASH` |
| Claim CK | `BANK_TRANSFER` (không đổi) |

`type = OTHER` = **loại** hoá đơn onboard, không phải phương thức. Đừng bind `type` ra chỗ “Đã trả bằng”.

### 1c. Breakdown — bỏ dòng “Giá thuê / tháng”

`paymentBreakdown.lines` của onboard (**QR preview + hoá đơn HD-ONBOARD**) **không** còn `key: rentAmount`. Tránh đứng cạnh “Tiền cọc” cùng 5.000.000.

Vẫn có `rentAmountMonthly` trên object (công thức). Hoá đơn tiền phòng thường / FIRST **vẫn** có dòng đó.

FE có thể bỏ `utils/onboardBill.withoutRentReferenceLine` nếu muốn.

---

## `/tenant/me/payments`

**Có** gồm giao dịch `HD-ONBOARD-*`: **một dòng**, số **gộp** (cọc + tiền nhà kỳ đầu), `method = QR`.

👉 FE **bỏ** dòng “Tiền cọc” tự dựng từ `contract.deposit` + `depositPaidAt`. Giữ sẽ **trùng 2 dòng** cùng một lần chuyển.

---

## Checklist FE

- [ ] Web prod: `syncServerTimeFromHeader` đọc được `Date` (sau khi VPS pull 2 commit; nginx expose nếu có)
- [ ] Không còn dựa nhắc kỳ đầu / `RENT_FIRST_CYCLE_*`
- [ ] Invoice list / home: `items` HD-ONBOARD cộng đúng tổng; ưu tiên `paymentBreakdown.lines`
- [ ] Hiện phương thức: `QR` / `CASH` / `BANK_TRANSFER` — không hiện “Khác” vì `OTHER` (đó là `type`)
- [ ] Payment history: xoá dòng cọc tự dựng
- [ ] Optional: gỡ `withoutRentReferenceLine` + suy `HD-ONBOARD` = CK khi `method` trống
