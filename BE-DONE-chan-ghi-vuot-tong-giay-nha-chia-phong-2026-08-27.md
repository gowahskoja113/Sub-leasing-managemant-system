# BE-DONE — Chặn tổng chỉ số các phòng vượt tổng giấy nhà nước

Ngày: 2026-08-27  
Phạm vi: **chỉ BE** — nhà **chia phòng**. Nguyên căn không đổi.

## Vấn đề đã xử lý

Quản lý ghi từng phòng không bị kiểm tổng so với giấy EVN/nước → có thể thu
khách nhiều hơn số công ty trả nhà nước.

## Thay đổi

### 1. Chặn trong `createRoomInvoice` (trước khi lưu)

`assertRoomSumWithinBill`:

- Trần = `totalQuantity × (1 + billing.utility.room-sum-tolerance-percent/100)` (mặc định **10%**)
- Tổng = Σ consumption phòng khác (còn hiệu lực) + consumption đang ghi
- Vượt trần → `422 ROOM_SUM_EXCEEDS_BILL`, không tạo HĐ / không notify khách
- Chưa có giấy kỳ này → **cho qua**
- Phát hành lại cùng phòng: exclude `roomId` + bỏ CANCELLED (không cộng dồn)

`details` kèm `sum`, `billTotal`, `cap`, `tolerancePercent`, `rooms[]`
(roomId / roomNumber / consumption) để app hiện danh sách dò.

### 2. Hàm cộng dùng chung với đối soát

`UtilityInvoiceService.sumActiveRoomConsumptions(...)` → `RoomUtilitySumSnapshot`  
Dùng cho chặn trần, đối soát hao hụt, và map response bill.

### 3. Tiến độ trên `UtilityBillResponse` (GET utility-bills)

| Field | Nghĩa |
|-------|--------|
| `roomSumQuantity` | Tổng phòng đã ghi |
| `roomSumCap` | Trần cho phép |
| `roomsBilled` / `roomsExpected` | Đã ghi / cần ghi (cùng `roomsDone` / `roomsTotal`) |

## Config

```yaml
billing.utility.room-sum-tolerance-percent: 10   # env: BILLING_UTILITY_ROOM_SUM_TOLERANCE_PERCENT
```

## File đụng

- `UtilityInvoiceService.java` / `UtilityInvoiceServiceImpl.java`
- `UtilityBillServiceImpl.java` / `UtilityBillResponse.java`
- `RoomUtilitySumSnapshot.java` (mới)
- `application.yaml`

## Kiểm chứng

1. Giấy 500, tol 10% → trần 550; 420 + 120 = 540 → OK  
2. 420 + 140 = 560 → `ROOM_SUM_EXCEEDS_BILL`  
3. Sửa lại 1 phòng (huỷ + phát hành) → không cộng dồn bản cũ  
4. Chưa có giấy → vẫn ghi được  
5. Nguyên căn → không đổi  
6. Tol = 0 → chặn ngay khi vượt đúng totalQuantity  
