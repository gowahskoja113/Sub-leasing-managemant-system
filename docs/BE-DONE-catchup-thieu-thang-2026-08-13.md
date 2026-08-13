# BE DONE — 2/3 ticket FE ngày 13/08/2026

**Ngày:** 13/08/2026  
**Người gửi:** team BE  
**Người nhận:** team FE (mobile + web admin)  
**Tiến độ MD FE gửi:** **2/3**

| # | File FE gửi | Loại | Trạng thái |
|---|-------------|------|------------|
| 1 | `BE-loi-catchup-thieu-thang-2026-08-13.md` | Bug catch-up v2 | ✅ Done — **Phần A** |
| 2 | `BE-yeu-cau-tinh-nang-gop-y-mentor-2026-08-13.md` | Feature mentor 12/08 | ✅ Done — **Phần B** |
| 3 | `KE-HOACH-gop-y-mentor-2026-08-13.md` | Kế hoạch FE | ⏳ Phía FE; BE chưa đụng |

Hai phần dưới **độc lập**: Phần A không đổi API; Phần B **có API / type / DTO mới**. Đọc checklist từng phần.

---
---

# Phần A — Catch-up bù đủ tháng + defer + thống nhất `dueDate`

**Phản hồi:** `BE-loi-catchup-thieu-thang-2026-08-13.md`

## Tóm tắt A

BE tái hiện được 3 ý FE báo. Đã sửa hết phía BE. **FE không cần đổi API / DTO** cho phần này — chỉ đọc `dueDate` + `grandTotal` như hiện tại.

| # | Mức độ | Việc FE yêu cầu | Trạng thái |
|---|--------|-----------------|------------|
| 1 | 🔴 | ACTIVE trễ ≥ 2 tháng → bù **mọi tháng giữa**, không chỉ tháng hiện tại | ✅ Done |
| 2 | 🟡 | Catch-up cộng ngày defer tháng trước (giống cron) | ✅ Done — hàm dùng chung |
| 3 | 🟢 | Thống nhất `dueDate` 2 đường phát hành | ✅ Done — rồi **đổi tiếp** ở Phần B.4.1 (không còn cứng ngày 5) |

**Nghiệp vụ (câu hỏi FE):** HĐ **được phép** `ACTIVE` trễ nhiều tháng so với `startDate` (chờ duyệt giá / chờ tenant thanh toán). Vì vậy BE làm vòng lặp bù, **không chặn** lúc duyệt.

**Ý 2 file onboard 12/08** (startDate tháng tương lai → QR thu nguyên tháng): **đúng là chủ ý**. FE render theo `proRated` / `formula` như đang làm là đúng.

---

## A1. 🔴 Bù đủ các tháng ở giữa

Khi HĐ `ACTIVE` mà `firstCycleMonth < currentMonth`, BE loop từ tháng sau chu kỳ đầu đến tháng hiện tại:

```
startDate 20/06, ACTIVE 05/08
  → tháng 6: đã thu pro-rata qua QR onboard
  → tháng 7: catch-up REGULAR (trước đây mất)
  → tháng 8: catch-up REGULAR
```

Tháng đầu **không** phát lại (đã onboard hoặc `DEFERRED`). Mỗi tháng đã có hoá đơn RENT thì skip.

---

## A2. 🟡 Catch-up cộng ngày defer

Cron và catch-up dùng chung `RentFirstCycleCalculator.deferredCarryOver` / `regularRentAmount`.

| Điều kiện | Hành vi |
|-----------|---------|
| HĐ **không** `endDate`, `startDate` còn ≤ 3 ngày cuối tháng trước | Cộng pro-rata những ngày đó vào hoá đơn REGULAR tháng sau |
| Tháng sau nữa (vd. start 29/07, hoá đơn tháng 9) | Không cộng — chỉ tháng liền kề sau tháng defer |
| Có `endDate` / tháng trước là pro-rata (> 3 ngày) | Không cộng |

**Ví dụ:** `startDate = 29/07`, rent 5.000.000, ACTIVE 05/08  
→ hoá đơn tháng 8 = 5.000.000 + (5.000.000 × 3 / 31) = **5.483.871**

Catch-up ghi thêm vào `note` khi có defer: `deferredDays` / `deferredFrom` / `deferredAmount`. FE **không bắt buộc** parse note — `grandTotal` đã gồm đủ.

---

## A3. 🟢 `dueDate` hoá đơn REGULAR

**Lúc sửa catch-up:** thống nhất ngày 5 + fallback grace nếu ngày 5 đã qua.

**Cập nhật cùng ngày (Phần B.4.1):** mốc **không còn cứng ngày 5 toàn hệ thống**. `dueDate` = ngày `startDate` của HĐ **+ `graceDays`** (mặc định +2), kẹp cuối tháng nếu 29/30/31. Nếu phát hành khi hạn đã qua → `hôm nay + firstCycleGraceDays` để tránh vừa tạo đã OVERDUE.

Hoá đơn **FIRST** (chu kỳ đầu) **giữ** `now + graceDays` — không phải REGULAR.

FE hiển thị đúng field `dueDate` BE trả về. **Không hardcode “hạn ngày 5”.**

---

## Checklist FE — Phần A

- [ ] Smoke: HĐ `startDate` tháng 6, ACTIVE tháng 8 → list invoices có **tháng 7 + tháng 8** (tháng 6 = onboard FIRST)
- [ ] Smoke: HĐ không `endDate`, `startDate` 29/07, ACTIVE tháng 8 → hoá đơn tháng 8 `grandTotal` > rent full (có 3 ngày 7)
- [ ] Bind `dueDate` từ API; đừng hardcode “hạn ngày 5”
- [ ] HĐ `ACTIVE` trễ nhiều tháng **được phép**; FE không cần chặn UI vì lý do thiếu hoá đơn nữa

---
---

# Phần B — 4 việc mentor demo 12/08

**Phản hồi:** `BE-yeu-cau-tinh-nang-gop-y-mentor-2026-08-13.md`  
**Không gộp** với luồng import hoá đơn điện toàn công ty (`EvnBill`) — FE/BE đã xong phần đó.

## Tóm tắt B

| # | Việc | Mức độ | Trạng thái |
|---|------|--------|------------|
| 1 | `POST /api/v1/vision/describe-room` | 🔴 | ✅ Done |
| 2 | Notify + Expo push manager khi thanh toán | 🔴 | ✅ Done |
| 3 | Bỏ `setInitialPaymentAmount` dòng 336 | 🟡 | ✅ Done |
| 4.1 | Mốc đóng tiền theo HĐ + API cấu hình | 🟡 | ✅ Done — **hướng A** |
| 4.2 | Cron nhắc chụp công tơ + list pending | 🟡 | ✅ Done |
| 4.3 | Chặn phát hoá đơn khi thiếu ảnh công tơ | 🟢 | ✅ Done — **chỉ điện/nước** + override admin |

**Trả lời câu hỏi FE:**

| Câu hỏi | Chốt BE |
|---------|---------|
| `type` mục 2 | **`PAYMENT_RECEIVED_MANAGER`** (cọc onboard giữ `DEPOSIT_PAID_MANAGER`) |
| `type` mục 4.2 | **`METER_READING_DUE`** |
| 4.1 hướng A hay B | **A** — mốc = ngày trong tháng của `startDate` từng HĐ; cron chạy hằng ngày |
| 4.3 chặn điện/nước hay cả tiền nhà | **Chỉ điện/nước**. Tiền nhà vẫn phát bình thường |
| 4.3 có override admin không | **Có** — `overrideToken` + `overrideReason` (luồng meter override sẵn có) |

Push: **Expo** qua `userPushTokenService.sendToUser` (không FCM). Số tiền **không** nằm trong nội dung notify manager.

---

## B1. 🔴 Endpoint AI mô tả ảnh hiện trạng

```
POST /api/v1/vision/describe-room
Auth: MANAGER | ADMIN
```

**Request**

```json
{ "imageUrls": ["https://res.cloudinary.com/...", "https://res.cloudinary.com/..."] }
```

- 1–8 ảnh / request. HTTPS + host Cloudinary (cùng allowlist `/labels`).
- Một lần gọi nhìn **cả bộ ảnh** → một đoạn thống nhất (không gọi từng ảnh).

**Response 200**

```json
{
  "description": "Phòng có giường, tủ và máy lạnh nhìn thấy trong ảnh. Sàn lát gạch, có vết bẩn gần cửa. Không quan sát được tình trạng tường phía sau góc camera.",
  "model": "gemini-2.0-flash"
}
```

**Model / phí (FE hỏi):** Gemini 2.0 Flash. Phí API: **`GEMINI_API_KEY` của công ty** (Google AI Studio). Quota **tách** khỏi `/labels` — mặc định 40 request/giờ/tài khoản (`VISION_DESCRIBE_RATE_LIMIT_PER_HOUR`).

**Lỗi mềm — FE bỏ qua, không chặn đón khách:** HTTP 422

| `code` | Khi nào |
|--------|---------|
| `VISION_DESCRIBE_QUOTA` | Hết lượt trong giờ |
| `VISION_DESCRIBE_UNAVAILABLE` | Chưa set key / model lỗi / ảnh lỗi |

Prompt đã cấm suy đoán (“còn tốt / không nứt” khi ảnh không đủ rõ). FE đổ vào ô ghi chú dạng **bản nháp sửa được**, không ghi đè nếu manager đã gõ, có nút “Tạo lại mô tả”.

`/labels` **không đổi**.

---

## B2. 🔴 Notify + Expo push khi tenant thanh toán

Khi hoá đơn chuyển `PAID` (`saveAndPublishPaidInvoice`): tạo `Notification` + Expo push cho `property.operationManagerId`. WebSocket `INVOICE_PAID` **giữ nguyên**.

Không kèm số tiền (policy ẩn tiền manager).

| Field | Giá trị |
|-------|---------|
| `type` | `PAYMENT_RECEIVED_MANAGER` |
| `screen` | `InvoiceList` |
| `params` / push `data` | `{ invoiceId, contractId }` |
| Nội dung | `"Khách {tên} · Phòng {phòng} đã thanh toán {tiền nhà\|tiền điện\|…}."` |

**Cọc onboard:** không đổi type — vẫn `DEPOSIT_PAID_MANAGER` / `ResumeContract` (đã có sẵn). FE gắn icon + điều hướng riêng 2 type.

---

## B3. 🟡 Rò số tiền onboard cho manager

Đã **bỏ** `res.setInitialPaymentAmount(total)` sau `toResponse`. Manager gọi `createDepositPayment` chỉ còn QR + checkout URL; `rentAmount` / `deposit` / `initialPaymentAmount` / breakdown vẫn `null`.

Đúng như FE nói: ẩn UI **không** giấu được số trong VietQR. BE **không** đổi nghiệp vụ gửi link thẳng cho tenant.

FE: bỏ hiển thị số + breakdown trên `OnboardingScreenV2` / `ResumeContractScreen`.

---

## B4.1 🟡 Mốc ngày đóng tiền theo HĐ + API cấu hình — hướng A

Mốc = ngày trong tháng của `startDate` (thiếu ngày thì kẹp cuối tháng).

Ví dụ HĐ ngày **15**, config mặc định `reminderLeadDays=3`, `graceDays=2`:

| | Ngày |
|--|------|
| Phát hành hoá đơn REGULAR | 12 |
| Bắt đầu nhắc | 12 (−3) |
| Hạn chót / OVERDUE sau | 17 (+2) |

Tháng `startDate` do chu kỳ FIRST / onboard lo — **không** phát REGULAR trùng tháng đó. Cron chạy **hằng ngày** 00:05, HĐ nào tới mốc thì phát.

```
GET  /api/v1/admin/billing-config     ADMIN
PUT  /api/v1/admin/billing-config     ADMIN
```

**PUT body / GET response**

```json
{
  "reminderLeadDays": 3,
  "graceDays": 2,
  "meterReminderLeadDays": 1,
  "updatedAt": "2026-08-13T15:00:00"
}
```

Lưu **DB** (`billing_config` id=1) — sửa xong có hiệu lực, không restart. `reminderLeadDays` / `graceDays`: 0–14; `meterReminderLeadDays`: 0–7 (optional lúc PUT, mặc định 1).

FE gắn màn cấu hình hệ thống web admin. Text nhắc hạn: bind `dueDate` từ hoá đơn, **đừng** viết “ngày 1 / ngày 5”.

---

## B4.2 🟡 Cron nhắc chụp công tơ + danh sách việc

Cron daily (trong `runDailySweep` 08:00): property/HĐ tới mốc ghi điện (ngày phát hành REGULAR) mà **thiếu ảnh** kỳ hiện tại → notify + Expo.

| Field | Giá trị |
|-------|---------|
| `type` | `METER_READING_DUE` |
| `screen` | `MeterReadingPending` |
| `params` / push `data` | `{ propertyId, period }` (`period` = `yyyy-MM`) |

```
GET /api/v1/manager/meter-readings/pending?period=2026-08
Auth: MANAGER | ADMIN
```

`period` optional — mặc định tháng hiện tại (Asia/Ho_Chi_Minh). Admin thấy mọi HĐ ACTIVE; manager chỉ nhà `operationManagerId` của mình.

**Item**

```json
{
  "propertyId": 1,
  "propertyName": "Nhà A",
  "roomId": 12,
  "roomNumber": "101",
  "contractId": 99,
  "utilityType": "ELECTRICITY",
  "period": "2026-08",
  "billingDay": 15,
  "meterDueDate": "2026-08-12",
  "hasReading": false,
  "hasPhoto": false
}
```

`roomId` / `roomNumber` = `null` → nguyên căn. Mỗi HĐ thiếu ảnh trả **2 dòng** (ELECTRICITY + WATER) nếu cả hai chưa có ảnh. Đã có ảnh thì không nằm trong list.

---

## B4.3 🟢 Chặn phát hoá đơn điện/nước khi thiếu ảnh

Áp lúc `createRoomInvoice` / `createPropertyInvoice`. **Không** chặn tiền nhà.

Được phát khi **một** trong ba:

1. Request có `meterImageUrl` không blank, hoặc
2. Đã có `MeterReading` kỳ đó kèm `imageUrl`, hoặc
3. Admin override: `overrideToken` + `overrideReason` (cùng luồng `POST /api/v1/manager/meter-override/verify`, `meterKind` = `ELEC` \| `WATER`)

Thiếu cả ba → 422 `code = METER_PHOTO_REQUIRED` + notify/push `METER_READING_DUE` cho manager.

Cửa sổ tạo hoá đơn điện/nước: hết **cứng ngày 10**. Hạn = `dueDate` của HĐ trong kỳ (mốc + grace). Quá hạn → `UTILITY_WINDOW_CLOSED`.

---

## File BE đã đổi (cả 2 phần)

**Phần A:** `RentFirstCycleCalculator`, `TenantBillingServiceImpl`, `BillingCronServiceImpl`, `RentFirstCycleCalculatorTest`

**Phần B:** `VisionController` / `VisionServiceImpl` / `GeminiRoomDescribeProvider`, `TenantBillingServiceImpl`, `TenantOnboardingServiceImpl`, `BillingCronServiceImpl`, `UtilityInvoiceServiceImpl`, `MeterReadingController` / `MeterReadingServiceImpl`, `AdminBillingController`, `BillingConfig*` (entity + API), `ContractBillingCalendar` + test, `application.yaml` (`vision.gemini`, `vision.describe`), `DatabaseSchemaMigration` (bảng `billing_config`)

---

## Checklist FE — Phần B

- [ ] Gọi `describe-room` sau khi upload xong toàn bộ ảnh; 422 thì im lặng + cho gõ tay; có “Tạo lại mô tả”
- [ ] Gắn icon + điều hướng `PAYMENT_RECEIVED_MANAGER` → `InvoiceList`; `METER_READING_DUE` → `MeterReadingPending`
- [ ] Màn onboard manager: chỉ QR, không hiện số / breakdown
- [ ] Web admin: màn `GET/PUT /api/v1/admin/billing-config`
- [ ] Màn việc manager: `GET /api/v1/manager/meter-readings/pending`
- [ ] Tạo hoá đơn điện/nước: gửi `meterImageUrl` hoặc override; bắt `METER_PHOTO_REQUIRED`
- [ ] Bỏ copy “hạn ngày 5 / phát hành ngày 1” — bind `dueDate`

---

## Ghi chú

- **2/3 xong.** File còn lại (`KE-HOACH-gop-y-mentor-2026-08-13.md`) là kế hoạch phía FE.
- Demo mô tả ảnh: server cần `GEMINI_API_KEY`. Chưa có key → 422 `VISION_DESCRIBE_UNAVAILABLE`, luồng đón khách vẫn chạy.
- Ẩn số trên UI onboard **không** giấu được số trong VietQR — nhóm chấp nhận, nêu khi báo cáo mentor.
