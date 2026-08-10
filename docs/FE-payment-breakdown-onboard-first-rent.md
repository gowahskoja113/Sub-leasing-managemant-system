# FE guide — Thanh toán cọc onboard & tiền nhà chu kỳ đầu (pro-rata)

**Ngày:** 10/08/2026  
**Từ:** BE  
**Cho:** FE (manager app / tenant app / admin web)

> Flow đã đổi: **QR onboard chỉ thu cọc**. Tiền nhà tháng vào ở **không gộp QR**, phát hoá đơn `RENT` + `cycleType=FIRST` (pro-rata theo ngày) sau khi HĐ `ACTIVE`, tenant trả trên app. Response có object **`paymentBreakdown`** / **`depositPaymentBreakdown`** / **`firstRentPaymentBreakdown`** để UI minh bạch.

---

## 1. Business flow (tóm tắt)

```
[DRAFT/PENDING]  ──QR cọc (chỉ deposit)──►  paymentStatus=PAID
        │
        ▼ OTP confirm
[ACTIVE]  ──BE auto issue──►  Hóa đơn RENT cycleType=FIRST (pro-rata hoặc full)
        │
        ▼ tenant app tạo PayOS
   Thanh toán tiền nhà chu kỳ đầu
        │
        ▼ tháng sau (cron ~ngày 1)
[REGULAR]  Hóa đơn full tháng, hạn ~ngày 5
```

| Khoản | Khi nào | Công thức |
|---|---|---|
| **Cọc** | Onboard QR / cash | `deposit` **hoặc** `rentAmount × depositMonths` |
| **Tiền nhà chu kỳ đầu** | Sau `ACTIVE` | **Pro-rata**: `(rentAmount ÷ daysInMonth) × billedDays` (làm tròn VND `HALF_UP`) |
| **Tiền nhà vòng lặp** | Tháng sau (cron 1–5) | Full `rentAmount` |

### Quy ước ngày (quan trọng)

- **Inclusive**: có tính **ngày vào ở**.  
  VD tháng 30 ngày, vào **25/04** → 25…30 = **6 ngày** → `(5.000.000 ÷ 30) × 6 = 1.000.000`.
- **≤ 3 ngày** cuối tháng (và không kết thúc HĐ cùng tháng): **không** phát hoá đơn FIRST; gộp sang tháng sau (`kind=RENT_FIRST_DEFERRED` trên preview).
- `dailyRate` display = `rentAmount ÷ daysInMonth` (làm tròn VND) — chỉ để UI; **thành tiền** vẫn theo công thức nhân chia tháng (tránh lệch rounding).

---

## 2. Fields mới trên API

### 2.1 `TenantContractResponse` (GET contract / createDepositPayment / confirm…)

| Field | Ý nghĩa |
|---|---|
| `initialPaymentAmount` | **Chỉ cọc** (số trên QR) |
| `deposit` / `depositMonths` / `rentAmount` | Base |
| **`depositPaymentBreakdown`** | Cách tính **cọc QR** (object bên dưới) |
| **`firstRentPaymentBreakdown`** | **Preview** tiền nhà chu kỳ đầu (chưa cần invoice) |

> `ROLE_MANAGER`: các field số tiền rent/deposit/breakdown **null** (policy cũ). Admin / tenant vẫn thấy.

### 2.2 `TenantInvoiceResponse` (tenant invoices list + detail)

| Field | Ý nghĩa |
|---|---|
| `items[]` | Dòng gọn (label + amount) — vẫn giữ |
| **`paymentBreakdown`** | **Ưu tiên** cho màn “Chi tiết / Cách tính” |
| `cycleType` | `FIRST` = chu kỳ đầu; `REGULAR` = vòng lặp |
| `billingPeriod` | Text hiển thị sẵn, VD `Tiền nhà 25/04–30/04/2026 (6/30 ngày)` |

### 2.3 `ManagerInvoiceResponse`

- List: nhẹ, **không** `items` / `paymentBreakdown`.
- **Detail** `GET /api/v1/manager/invoices/{id}`: có `items` + `paymentBreakdown`.
- Manager + type `RENT`: số tiền mask (`null` / `***`); vẫn có `kind`, `explanation`, `billedDays`…

### 2.4 Shape `PaymentBreakdownResponse`

```ts
type PaymentBreakdown = {
  kind:
    | "DEPOSIT_ONBOARD"
    | "RENT_FIRST_PRO_RATA"
    | "RENT_FIRST_FULL"
    | "RENT_FIRST_DEFERRED"
    | "RENT_REGULAR"
    | "OTHER";
  title: string;           // "Tiền cọc lúc nhận nhà" | "Tiền nhà chu kỳ đầu (trước vòng lặp)"
  formula: string | null;  // "(5000000 ÷ 30) × 6 = 1000000" (BE format . cho nghìn)
  explanation: string;     // copy UI / tooltip — tiếng Việt
  totalAmount: number;

  // structured (null nếu N/A)
  rentAmountMonthly?: number;
  depositMonths?: number;
  depositAmount?: number;
  dailyRate?: number;
  daysInMonth?: number;
  billedDays?: number;
  periodStart?: string;    // ISO date
  periodEnd?: string;
  includesMoveInDay?: boolean; // first rent: true
  proRated?: boolean;
  deferredToNextMonth?: boolean;

  lines: Array<{
    key: string;           // rentAmount | depositMonths | dailyRate | billedDays | period | total | …
    label: string;
    displayValue: string;  // đã format sẵn → có thể render trực tiếp
    amount?: number;       // tiền — FE format currency nếu muốn
    unit?: string;         // VND | ngày | tháng
  }>;
};
```

**FE ưu tiên render:**

1. `title` + `totalAmount`  
2. `formula` (mono / badge)  
3. `lines[]` (table)  
4. `explanation` (footnote)

Không tự tính lại tiền trên FE (tránh lệch rounding). Dùng breakdown BE.

---

## 3. Màn hình gợi ý

### 3.1 Manager — gen / show QR cọc

**API:** `POST /api/v1/tenant-contracts/{id}/deposit-payment` (hoặc path hiện tại trong `TenantContractActionController`)

**UI:**

```
Tiền cọc lúc nhận nhà                    10.000.000đ
────────────────────────────────────────────────────
Giá thuê / tháng                         5.000.000đ
Số tháng cọc                                   2 tháng
Tiền cọc phải trả                       10.000.000đ
────────────────────────────────────────────────────
Công thức: 5000000 × 2 = 10000000

ℹ  QR chỉ thu cọc. Tiền nhà tháng này (theo ngày) sẽ phát
   sau khi hoàn tất nhận nhà — khách thanh toán trên app.
```

Data: `depositPaymentBreakdown` (+ `payosQrCode` / `payosCheckoutUrl` / `initialPaymentAmount`).

Optional panel “Sau khi nhận nhà sẽ thu”:

- map `firstRentPaymentBreakdown`  
- nếu `kind === 'RENT_FIRST_DEFERRED'` → badge “Gộp tháng sau”, không show số thu ngay.

### 3.2 Tenant — hoá đơn tiền nhà chu kỳ đầu

**API:**

- `GET /api/v1/tenant/invoices` (filter type `RENT`, status `PENDING`)
- `POST /api/v1/tenant/invoices/{id}/payment` → QR PayOS

Nhận diện chu kỳ đầu:

```ts
inv.cycleType === "FIRST"
// hoặc
inv.paymentBreakdown?.kind?.startsWith("RENT_FIRST_")
// hoặc code HD-RENT-{contractId}-{YearMonth}
```

**UI chi tiết:**

```
Tiền nhà chu kỳ đầu (trước vòng lặp)       1.000.000đ
cycleType: FIRST

Công thức: (5.000.000 ÷ 30) × 6 = 1.000.000

Giá thuê / tháng                          5.000.000đ
Số ngày trong tháng                              30
Đơn giá / ngày                              166.667đ
Kỳ tính tiền                    25/04/2026 → 30/04/2026
Số ngày tính (gồm ngày vào ở)                     6
Thành tiền                                    1.000.000đ

ℹ  Trước vòng lặp 1–5. Tháng sau thu full theo lịch định kỳ.
```

Render từ `paymentBreakdown.lines` + `formula` + `explanation`.

### 3.3 Onboard invoice (đã PAID cọc)

- Code: `HD-ONBOARD-{contractId}`
- type: `OTHER` (flow mới; data cũ có thể `RENT`)
- note: `ONBOARD|depositAmount=…|depositMonths=…`
- `paymentBreakdown.kind = DEPOSIT_ONBOARD`
- items: 1 dòng **Tiền cọc (N tháng)** (data cũ có thể còn “Tiền nhà tháng đầu”)

### 3.4 Admin web — detail invoice

`GET /api/v1/manager/invoices/{id}` → drawer:

- section **Cách tính** from `paymentBreakdown`
- vẫn show `items[]` nếu cần list đơn giản

---

## 4. Ví dụ JSON

### Contract (sau khi load HĐ / gen QR)

```json
{
  "id": 42,
  "rentAmount": 5000000,
  "deposit": 10000000,
  "depositMonths": 2,
  "initialPaymentAmount": 10000000,
  "moveInDate": "2026-04-25",
  "depositPaymentBreakdown": {
    "kind": "DEPOSIT_ONBOARD",
    "title": "Tiền cọc lúc nhận nhà",
    "formula": "5000000 × 2 = 10000000",
    "explanation": "Lúc onboard chỉ thu tiền cọc qua QR. …",
    "totalAmount": 10000000,
    "rentAmountMonthly": 5000000,
    "depositMonths": 2,
    "depositAmount": 10000000,
    "lines": [
      { "key": "rentAmount", "label": "Giá thuê / tháng", "displayValue": "5000000", "amount": 5000000, "unit": "VND" },
      { "key": "depositMonths", "label": "Số tháng cọc", "displayValue": "2", "unit": "tháng" },
      { "key": "depositAmount", "label": "Tiền cọc phải trả", "displayValue": "10000000", "amount": 10000000, "unit": "VND" },
      { "key": "total", "label": "Tổng QR onboard", "displayValue": "10000000", "amount": 10000000, "unit": "VND" }
    ]
  },
  "firstRentPaymentBreakdown": {
    "kind": "RENT_FIRST_PRO_RATA",
    "title": "Tiền nhà chu kỳ đầu (trước vòng lặp)",
    "formula": "(5000000 ÷ 30) × 6 = 1000000",
    "explanation": "Tiền nhà trước vòng lặp: tính theo ngày từ 25/04/2026 đến 30/04/2026 (6/30 ngày, gồm ngày vào ở). …",
    "totalAmount": 1000000,
    "rentAmountMonthly": 5000000,
    "dailyRate": 166667,
    "daysInMonth": 30,
    "billedDays": 6,
    "periodStart": "2026-04-25",
    "periodEnd": "2026-04-30",
    "includesMoveInDay": true,
    "proRated": true,
    "deferredToNextMonth": false,
    "lines": [ /* … */ ]
  },
  "payosQrCode": "…",
  "payosCheckoutUrl": "…"
}
```

### Invoice FIRST (tenant)

```json
{
  "id": 100,
  "code": "HD-RENT-42-2026-04",
  "type": "RENT",
  "cycleType": "FIRST",
  "billingPeriod": "Tiền nhà 25/04–30/04/2026 (6/30 ngày)",
  "status": "PENDING",
  "grandTotal": 1000000,
  "items": [
    { "label": "Giá thuê / tháng", "amount": 5000000 },
    { "label": "Số ngày tính (6/30, gồm ngày vào ở)", "amount": null },
    { "label": "Thành tiền pro-rata", "amount": 1000000 }
  ],
  "paymentBreakdown": {
    "kind": "RENT_FIRST_PRO_RATA",
    "title": "Tiền nhà chu kỳ đầu (trước vòng lặp)",
    "formula": "(5000000 ÷ 30) × 6 = 1000000",
    "explanation": "Đây là tiền nhà trước khi vào vòng lặp thu ngày 1–5 hàng tháng. …",
    "totalAmount": 1000000,
    "billedDays": 6,
    "daysInMonth": 30,
    "includesMoveInDay": true,
    "proRated": true,
    "lines": [ /* … */ ]
  }
}
```

---

## 5. Checklist FE

- [ ] Màn QR cọc: **không** còn 2 dòng “tháng đầu + cọc”; chỉ `depositPaymentBreakdown`
- [ ] Copy: QR = cọc; tiền nhà (pro-rata) sau ACTIVE trên app
- [ ] Tenant invoice detail: ưu tiên `paymentBreakdown` (đặc biệt `FIRST`)
- [ ] Badge chu kỳ: `FIRST` vs `REGULAR`
- [ ] Handle `RENT_FIRST_DEFERRED` trên preview (vào muộn ≤3 ngày)
- [ ] Format currency từ `amount` / `displayValue` — không tự chia lại
- [ ] Manager mask: RENT detail không lộ tiền (breakdown đã mask VND)
- [ ] Legacy `HD-ONBOARD` có `rentAmount` trong items → hiện nhãn “legacy” hoặc vẫn show 2 dòng nếu BE trả

---

## 6. Endpoints liên quan (tham chiếu)

| Việc | Method | Note |
|---|---|---|
| Gen QR cọc | `POST` …`/deposit-payment` | `initialPaymentAmount` = cọc |
| Sync / webhook PAID | webhook + `check-payment` | tạo `HD-ONBOARD-*` |
| Confirm OTP → ACTIVE | confirm contract | BE tạo RENT FIRST |
| List/detail invoice tenant | `GET /api/v1/tenant/invoices`… | có `paymentBreakdown` |
| Pay rent | `POST …/invoices/{id}/payment` | PayOS trên app |
| Manager invoice detail | `GET /api/v1/manager/invoices/{id}` | `items` + `paymentBreakdown` |

Path exact theo controller hiện tại trong repo (`TenantContractActionController`, `TenantMeController`, `ManagerBillingController`).

---

## 7. Không làm (out of scope FE)

- Không hardcode 30 ngày — luôn đọc `daysInMonth` / `billedDays` từ BE  
- Không gộp cọc + thuê trên 1 QR  
- Không đổi `initialPaymentAmount` nghĩa cũ (rent+deposit) — **đã = cọc only**

Hết. Có thắc mắc field/kind → hỏi BE kèm sample contractId.
