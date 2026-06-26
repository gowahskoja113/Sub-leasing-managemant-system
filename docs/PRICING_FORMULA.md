# Đặc tả công thức định giá — SLMS-2026

> **Mô hình:** Thuê và cho thuê lại bất động sản (Sub-leasing Management Ecosystem)  
> **Phiên bản:** Prepaid rent — tiền thuê nhà gốc trả **100% một lần** cho cả kỳ HĐ inbound.

Tài liệu này định nghĩa công thức toán học và logic thuật toán định giá cho:

1. **Luồng xuôi** — Gợi ý giá phòng khi biết lợi nhuận mong muốn (`P_desired`)
2. **Luồng ngược** — KPI doanh thu khi biết ROI mong muốn (`ROI_expected`)
3. **Phân bổ theo m²** — Không chia đều theo số phòng
4. **Dashboard** — Đối soát doanh thu / lợi nhuận thực tế

---

## 1. Giả định nghiệp vụ (theo code hiện tại)

| Thực tế hệ thống | Quy ước trong công thức |
|-----------------|-------------------------|
| `InboundContract.totalRentAmount` = tổng tiền thuê trả chủ gốc **một lần** cho cả kỳ HĐ | Là **vốn đầu tư ban đầu**, hoàn vốn đều theo tháng |
| Không có field cọc / không trả thuê định kỳ cho chủ nhà gốc | **Không** dùng `C_deposit`, **không** dùng `R_master` |
| Chi phí cải tạo + thiết bị lấy từ onboarding | Cùng nhóm vốn cần hoàn |
| Mỗi phòng có `room.area` (m²) | Phân bổ giá theo diện tích, không chia đều `N_room` |

**Map field code:**

| Ký hiệu | Field / nguồn |
|---------|----------------|
| `C_rent` | `InboundContract.totalRentAmount` |
| `C_renovation` | `SUM(renovation_lines.cost)` theo `propertyId` |
| `C_equipment` | `SUM(equipment.price)` với `source = PURCHASED` |
| `M` | `ChronoUnit.MONTHS(startDate, endDate)` |
| `A_i` | `Room.area` |
| `A_property` | `Property.areaSize` (tùy chọn) |
| `N_room` | Số phòng `deleted = false` |

---

## 2. Biến đầu vào

### 2.1. Tự động từ hệ thống

| Biến | Mô tả |
|------|-------|
| `C_rent` | Tổng tiền thuê đã trả trước cho chủ nhà gốc |
| `C_renovation` | Tổng chi phí cải tạo |
| `C_equipment` | Tổng chi phí thiết bị mua mới |
| `M` | Thời hạn HĐ inbound (tháng), `M ≥ 1` |
| `A_i` | Diện tích phòng *i* (m²) |
| `N_room` | Tổng số phòng cho thuê |

### 2.2. Người dùng nhập khi tính giá

| Biến | Mặc định | Mô tả |
|------|----------|-------|
| `P_desired` | — | Lợi nhuận ròng mong muốn mỗi tháng (cấp tòa). Dùng **Luồng 1** |
| `ROI_expected` | — | Tỷ suất hoàn vốn mong muốn mỗi năm (%). Dùng **Luồng 2** |
| `O_operation` | `0` | Chi phí vận hành cố định mỗi tháng (lương OM, dọn dẹp, internet tổng…) |
| `V_rate` | `0.10` | Tỷ lệ dự phòng trống phòng (10%) |
| `k_i` | `1.0` | Hệ số chất lượng phòng *i* (WC riêng, tầng, nội thất…) |

> **Lưu ý:** Chỉ dùng **một** trong hai: `P_desired` (xuôi) hoặc `ROI_expected` (ngược).

---

## 3. Công thức nền (cấp tòa nhà)

### 3.1. Tổng vốn đầu tư (CAPEX)

```
CAPEX = C_rent + C_renovation + C_equipment
```

Tương đương `DepreciationResult.totalInvestment` trong code.

### 3.2. Hoàn vốn hàng tháng

Toàn bộ CAPEX là tiền đã chi ra (thuê trả trước + cải tạo + thiết bị), hoàn đều trong `M` tháng:

```
Monthly_Recovery = CAPEX / M
```

### 3.3. Chi phí nền hàng tháng (Fixed OPEX)

```
Fixed_OPEX = O_operation + Monthly_Recovery
```

---

## 4. Trọng số phân bổ theo m²

### 4.1. Diện tích hiệu dụng (có khu vực chung)

```
A_common = max(0, A_property − Σ A_j)     // nếu không có A_property thì A_common = 0

effective_m2_i = A_i + A_common × (A_i / Σ A_j)

weight_i = effective_m2_i × k_i
W_total  = Σ weight_i
```

### 4.2. Phân bổ vốn xuống từng phòng (break-even từng phòng)

Renovation hiện lưu cấp property → phân bổ theo trọng số. Equipment có thể gắn theo phòng:

```
C_equipment_i  = SUM giá thiết bị PURCHASED của phòng i
C_renovation_i = C_renovation × (weight_i / W_total)
C_rent_i       = C_rent × (weight_i / W_total)

CAPEX_i = C_rent_i + C_renovation_i + C_equipment_i

Monthly_Recovery_i = CAPEX_i / M
OPEX_share_i       = O_operation × (weight_i / W_total)
```

### 4.3. Giá sàn từng phòng (hoàn vốn + OPEX + buffer trống phòng)

```
Room_Floor_i = (Monthly_Recovery_i + OPEX_share_i) × (1 + V_rate)
```

---

## 5. Luồng 1 — Tính xuôi (biết `P_desired`)

Áp dụng khi chủ đầu tư biết lợi nhuận ròng mong muốn mỗi tháng.

### Bước 1 — Doanh thu tối thiểu (chưa buffer trống phòng)

```
Revenue_min = Fixed_OPEX + P_desired
```

### Bước 2 — Doanh thu mục tiêu (giả định lấp đầy 100% ở mức giá niêm yết)

```
Revenue_target = Revenue_min × (1 + V_rate)
```

### Bước 3 — Đơn giá theo trọng số m²

```
price_per_weight_unit = Revenue_target / W_total
```

### Bước 4 — Giá từng phòng

```
Room_Price_i = price_per_weight_unit × weight_i
```

### Ràng buộc kiểm tra

```
Σ Room_Price_i  ≈  Revenue_target    (sai số làm tròn; có thể điều chỉnh phòng cuối)
Room_Price_i    ≥  Room_Floor_i      (khuyến nghị)
```

---

## 6. Luồng 2 — Tính ngược (biết `ROI_expected`)

Áp dụng khi chủ đầu tư đặt mục tiêu ROI trên tổng vốn CAPEX.

### Bước 1 — Lợi nhuận ròng tích lũy cả kỳ HĐ

```
Y = M / 12

Total_Profit = CAPEX × (ROI_expected / 100) × Y
```

### Bước 2 — Dòng tiền mục tiêu mỗi tháng (hoàn vốn + lời)

```
Monthly_Goal = (CAPEX + Total_Profit) / M
```

### Bước 3 — Doanh thu mục tiêu cấp tòa

```
Revenue_target = (Monthly_Goal + O_operation) × (1 + V_rate)
```

### Bước 4 — Phân bổ xuống phòng

Dùng **cùng Bước 3–4 của Luồng 1** (mục 5) để tính `Room_Price_i`.

---

## 7. Nhà nguyên căn (`wholeHouse = true`)

Không áp dụng phân bổ theo phòng / m²:

```
Property_Price = Revenue_target
```

`PricingScope = WHOLE_HOUSE` — giữ nhánh riêng trong `DepreciationService`.

---

## 8. Dashboard — Đối soát thực tế

Tính hàng tháng khi có hóa đơn phòng trạng thái `Paid`.

### 8.1. Chỉ số thực tế

```
Actual_Revenue   = Σ hóa đơn phòng status = Paid trong tháng

Occupancy_Rate   = (số phòng có HĐ tenant Active / N_room) × 100%

Actual_Profit    = Actual_Revenue − Fixed_OPEX
```

### 8.2. Dòng tiền tháng (sau giai đoạn đầu tư ban đầu)

```
Actual_Cash_Flow = Actual_Revenue − O_operation
```

`Monthly_Recovery` là hoàn vốn kế toán của khoản đã chi trước — **không** phải chi phí tiền mặt phát sinh hàng tháng.

### 8.3. So sánh KPI

| Chỉ số | So với | Ý nghĩa |
|--------|--------|---------|
| `Actual_Profit` | `P_desired` | Đạt chỉ tiêu lợi nhuận (sau hoàn vốn) |
| `Actual_Revenue` | `Revenue_target × (Occupancy_Rate / 100)` | Doanh thu có đủ với mức lấp đầy thực? |
| `Actual_Cash_Flow` | — | Tiền mặt ròng sau chi phí vận hành |

**Trạng thái Đạt chỉ tiêu:** `Actual_Profit ≥ P_desired`

---

## 9. Ví dụ số

**Đầu vào:**

| Biến | Giá trị |
|------|---------|
| `C_rent` | 600,000,000 |
| `C_renovation` | 80,000,000 |
| `C_equipment` | 40,000,000 |
| `M` | 60 tháng |
| `O_operation` | 5,000,000 |
| `P_desired` | 10,000,000 |
| `V_rate` | 10% |

**Tính nền:**

```
CAPEX            = 720,000,000
Monthly_Recovery = 12,000,000
Fixed_OPEX       = 17,000,000
Revenue_min      = 27,000,000
Revenue_target   = 29,700,000
```

**Phân bổ m²** (giả sử `A_common = 0`):

| Phòng | `A_i` | `k_i` | `weight_i` |
|-------|-------|-------|------------|
| A | 15 m² | 1.0 | 15 |
| B | 20 m² | 1.1 | 22 |
| **Tổng** | | | **W_total = 37** |

```
Room_Price_A = 29,700,000 × 15/37 ≈ 12,040,541
Room_Price_B = 29,700,000 × 22/37 ≈ 17,659,459
```

---

## 10. Tóm tắt một dòng

```
CAPEX          = totalRentAmount + renovation + equipment
Fixed_OPEX     = O_operation + CAPEX / M
Revenue_target = (Fixed_OPEX + P_desired) × (1 + V_rate)              // Luồng 1
Room_Price_i   = Revenue_target × (effective_m2_i × k_i) / W_total
```

**Luồng 2** thay bước `Revenue_target` bằng:

```
Revenue_target = ((CAPEX + CAPEX×ROI%×M/12) / M + O_operation) × (1 + V_rate)
```

---

## 11. Gợi ý API / DTO (tham khảo implement)

### Request — `POST /api/v1/properties/{propertyId}/pricing/calculate`

```json
{
  "mode": "FORWARD",
  "pDesired": 10000000,
  "oOperation": 5000000,
  "vRate": 0.10,
  "roomQualityFactors": {
    "12": 1.0,
    "13": 1.1
  }
}
```

```json
{
  "mode": "REVERSE",
  "roiExpected": 15,
  "oOperation": 5000000,
  "vRate": 0.10
}
```

### Response (trích)

```json
{
  "propertyId": 1,
  "pricingScope": "ROOM",
  "capex": 720000000,
  "contractMonths": 60,
  "monthlyRecovery": 12000000,
  "fixedOpex": 17000000,
  "revenueTarget": 29700000,
  "roomResults": [
    {
      "roomId": 12,
      "roomNumber": "101",
      "area": 15,
      "effectiveM2": 15,
      "weight": 15,
      "roomFloor": 9800000,
      "suggestedMinPrice": 12040541
    }
  ]
}
```

---

## 12. Khác biệt so với code hiện tại (`DepreciationServiceImpl`)

| Hiện tại | Theo tài liệu này |
|----------|-------------------|
| `suggestedMinPrice = CAPEX / M` (chưa lời, chưa `V_rate`) | `Room_Floor_i` hoặc giá sau `P_desired` / ROI |
| Chia đều `rooms.size()` | Chia theo `weight_i` (m² × `k_i`) |
| `CalculateDepreciationRequest` rỗng | Thêm `mode`, `pDesired` / `roiExpected`, `oOperation`, `vRate` |
| Chưa có `effectiveM2`, `weight`, `revenueTarget` trong response | Bổ sung trong `DepreciationResultResponse` |

---

## 13. Lịch sử thay đổi

| Ngày | Nội dung |
|------|----------|
| 2026-06-25 | Phiên bản đầu — prepaid rent, phân bổ m², bỏ cọc / trả thuê định kỳ |
