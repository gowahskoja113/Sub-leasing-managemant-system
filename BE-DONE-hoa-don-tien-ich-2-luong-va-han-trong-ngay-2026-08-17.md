# BE-DONE — Hoá đơn điện/nước: 2 luồng + hạn chụp trong ngày (17/08/2026)

Đối chiếu file `BE-NEED-hoa-don-tien-ich-2-luong-va-han-trong-ngay-2026-08-17.md`.

**Kết luận kiểm tra:** trước khi sửa, **chưa làm** ① ② ③. Phần “ĐÃ CÓ” (notify manager khi publish, notify khách khi có `UtilityInvoice`, chặn `createPropertyInvoice` trên nhà chia phòng) vẫn đúng, không đụng lại.

Đã triển khai xong trên BE. Chi tiết dưới đây.

---

## ① Nguyên căn — phát hành 1 bước, cùng transaction

| Yêu cầu | Trạng thái |
|---|---|
| `createPublishedBill` tự tạo `UtilityInvoice` nếu `wholeHouse == true` | Đã làm |
| Cùng transaction với lưu `UtilityBill` | Đã làm |
| `prevReading` / `newReading` optional trên `CreateUtilityBillRequest` | Đã làm |
| Không tự điền 0 / `totalQuantity` khi thiếu chỉ số | Đã làm — thiếu thì `READINGS_REQUIRED` |
| `newReading − prevReading === totalQuantity` | Đã làm — lệch thì `CONSUMPTION_MISMATCH` |
| Câu thông báo khách tách admin vs quản lý | Đã làm |

Luồng mới:

1. Admin `POST /api/v1/admin/utility-bills` với `prevReading` + `newReading`.
2. BE lưu hoá đơn tổng, tạo `UtilityInvoice` + `TenantInvoice` cho HĐ ACTIVE, gửi in-app + push cho khách.
3. Nếu bước 2 lỗi (chưa có HĐ, lệch chỉ số, PayOS/billing…) → **rollback cả hoá đơn tổng**, admin phát hành lại được.

Nhà nguyên căn không bắt ảnh công tơ trên luồng này — giấy EVN/nước (`imageUrl`) là chứng từ.

Câu khách nhận:

- Nguyên căn (admin): *Admin vừa phát hành hoá đơn Điện/Nước kỳ …*
- Chia phòng (quản lý ghi số): giữ *Quản lý vừa chốt số và phát hành hoá đơn …*

### FE cần biết (①)

Sau bản BE này **bỏ** `createPropertyInvoice` khi phát hành nguyên căn. Nếu FE cũ vẫn gọi bước 2, khách đã nhận hoá đơn rồi nhưng FE có thể báo lỗi `INVOICE_ALREADY_EXISTS`.

Request tạo bill nguyên căn phải gửi:

```json
{
  "prevReading": 1234.0,
  "newReading": 1456.0
}
```

---

## ② Nhà chia phòng — hạn chụp trong ngày + cron leo thang

| Yêu cầu | Trạng thái |
|---|---|
| `UtilityBill.readingDeadline` = ngày phát hành | Đã làm (chỉ nhà chia phòng) |
| Cron `remindUtilityMeterReading` | Đã làm — 08:00 / 15:00 / 20:00 `Asia/Ho_Chi_Minh` |
| Đếm phòng ACTIVE trừ phòng đã có `UtilityInvoice` | Đã làm |
| Nhà nguyên căn không vào cron | Đã làm |
| `UtilityBillResponse`: `roomsTotal`, `roomsDone`, `readingDeadline`, `overdue` | Đã làm |

Mốc thông báo:

| Mốc | Type | Ai nhận |
|---|---|---|
| Ngay khi phát hành | `UTILITY_READING_ASSIGNED` | Quản lý |
| 15:00 cùng ngày, còn thiếu | `UTILITY_READING_DUE_TODAY` | Quản lý |
| 20:00 cùng ngày, còn thiếu | `UTILITY_READING_LATE_RISK` | Quản lý |
| Mỗi sáng sau đó, còn thiếu | `UTILITY_READING_OVERDUE` | Quản lý **+ admin + host** |

Chống gửi trùng bằng `dedupe_key` (`utility-reading:{billId}:…`), cùng pattern `remindUpcomingReception`.

`overdue = true` khi hôm nay **sau** `readingDeadline` và `roomsDone < roomsTotal`.

---

## ③ Nội dung thông báo quản lý khác nhau theo loại nhà

| Loại nhà | Type | Nội dung |
|---|---|---|
| Nguyên căn | `EVN_BILL_PUBLISHED` / `WATER_BILL_PUBLISHED` | *Đã phát hành hoá đơn … cho khách thuê · {tên}#{id}. Bạn không cần làm gì…* |
| Chia phòng | `UTILITY_READING_ASSIGNED` | *⚡ Việc hôm nay: chụp đồng hồ + ghi chỉ số N phòng · … Phải xong trong hôm nay.* |

Không còn câu chung chỉ nhét `NGUYEN_CAN` / `THEO_PHONG` vào giữa.

---

## File đã đụng

- `entity/UtilityBill.java` — `readingDeadline`
- `dto/request/CreateUtilityBillRequest.java` — `prevReading`, `newReading`
- `dto/response/UtilityBillResponse.java` — progress + hạn
- `service/impl/UtilityBillServiceImpl.java` — atomic publish, notify, cron logic
- `service/impl/UtilityInvoiceServiceImpl.java` — `createFromWholeHouseBill`, tách câu khách
- `config/UtilityMeterReadingCron.java` — schedule 8h / 15h / 20h
- `config/DatabaseSchemaMigration.java` + `db/schema.sql` — cột `reading_deadline`

---

## Việc FE / app quản lý còn lại

1. Nguyên căn: bỏ `POST .../utility-invoices` sau khi tạo bill; gửi chỉ số cũ/mới trên request bill.
2. App quản lý: đọc `roomsTotal`, `roomsDone`, `readingDeadline`, `overdue` trên `GET /api/v1/manager/utility-bills` để hiện “còn N phòng · hết hôm nay” — không cần gọi thêm API trừ.
3. File NEED ghi “②+④”; mục **④ không có trong tài liệu**. BE đã làm đủ field để FE tự đếm ngược.
