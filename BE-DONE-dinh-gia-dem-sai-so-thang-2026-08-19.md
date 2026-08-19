# BE-DONE — Định giá đếm sai số tháng (19/08/2026)

Đối chiếu `BE-BUG-dinh-gia-dem-sai-so-thang-2026-08-19.md`.

Mẫu số chia vốn giờ là **số tháng thật sự thu tiền**, không phải `MONTHS.between(start, end)`.

Công thức (`InboundLeaseRules.resolveRevenueWindow`):

```
rentableFrom   = max(lease.startDate, renovationEndDate, hôm nay)
rentableMonths = MONTHS.between(rentableFrom, lease.endDate + 1 ngày)
revenueMonths  = rentableMonths ≥ 6 ? rentableMonths − 1 : rentableMonths
```

`HANDOVER_BUFFER_MONTHS = 1`, `HANDOVER_MIN_TERM_MONTHS = 6` — cùng chỗ với phiếu chặn khai thác.

`POST /pricing/calculate` và `GET /pricing` dùng `revenueMonths` làm `contractMonths` (mẫu số `monthlyRecovery`).

Response thêm: `leaseMonths`, `rentableFrom`, `rentableMonths`, `handoverBufferMonths`, `revenueMonths`.

**Không** dùng `endDate.plusDays(1)` *thay* buffer — chỉ để đếm thời hạn thật, rồi trừ 1 tháng bàn giao có tên.

Giá đã tính trước khi vá vẫn lưu mẫu số cũ trong `depreciation_result.contract_months` — **tính lại giá** (`POST .../pricing/calculate`) thì mới đúng.

**FE:** `suggestedMinPrice` / `roomFloor` / `suggestedPriceWithProfit` theo mẫu số mới. Có thể bỏ cảnh báo lệch nếu `contractMonths == revenueMonths`.
