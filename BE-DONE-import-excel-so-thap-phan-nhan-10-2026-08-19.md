# BE-DONE — Import Excel số thập phân nhân 10 (19/08/2026)

Đối chiếu `BE-BUG-import-excel-so-thap-phan-nhan-10-2026-08-19.md`.

**Nguyên nhân:** `readDouble`/`readInteger`/`readDecimal` format ô số thành chuỗi theo locale JVM (`vi-VN` → `"29,6"`) rồi `.replace(",", "")` → `"296"`.

**Đã sửa**

| Việc | Kết quả |
|---|---|
| Ô `NUMERIC` / công thức số: `getNumericCellValue()` | 29.6 giữ 29.6, không qua chuỗi |
| Ô TEXT: `parseFlexibleNumber` (vi `1.234,5` / en `1,234.5`) | Đã làm |
| `DataFormatter(Locale.US)` trên 6 trình đọc | Đã làm |
| `ExcelOnboardingWorkbookReader` (bản copy private) ủy quyền sang support | Đã làm |
| Test regression locale `vi-VN` | `ExcelImportReaderSupportTest` |

**Chưa làm:** migration chia 10 dữ liệu đã import sai. Cần import lại hoặc rà `room.area` / `length` / `width` (tổng phòng > diện tích căn). Parse chuỗi hỏng vẫn `null` như trước (cột bắt buộc vẫn fail validation); ô số/tiền numeric không còn nuốt `12.000.000`.

**FE:** không parse Excel — chỉ import lại / sửa DB.
