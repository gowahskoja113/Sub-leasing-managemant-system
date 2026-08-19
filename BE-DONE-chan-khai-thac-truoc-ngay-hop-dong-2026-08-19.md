# BE-DONE — Chặn khai thác nhà trước ngày HĐ chủ nhà (19/08/2026)

Đối chiếu `BE-NEED-chan-khai-thac-truoc-ngay-hop-dong-2026-08-19.md`.

Quy tắc chung: `InboundLeaseRules` (`HANDOVER_BUFFER_MONTHS = 1`).

---

## 5 chặn cứng

| # | Chỗ | Kết quả |
|---|---|---|
| 1–2 | `onboardTenant()` (nháp + import) | `moveInDate < lease.startDate` / `endDate > lease.endDate` → `BusinessException`. Thiếu HĐ chủ nhà cũng chặn |
| 3 | `updateDraftContract()` | Cùng rule 1–2 trên ngày sau khi sửa |
| 4 | `confirmContract()` **trước** khối nhận nhà sớm | `hôm nay < lease.startDate` → không đón khách. Nhận sớm kẹp `clampMoveInToLease` |
| 5 | `hostConfirm()` | `lease.endDate <= hôm nay` → không kích hoạt |

Import Excel: lỗi theo dòng (cùng rule 1–2). **Không** siết `moveInDate = hôm nay` trên import lịch sử.

---

## 3 cảnh báo (không chặn)

| # | Chỗ | Field |
|---|---|---|
| 6 | `hostConfirm` | `leaseNotStartedWarning` khi `lease.startDate > hôm nay` |
| 7 | `onboardTenant` / `updateDraftContract` | `leaseHandoverWindowWarning` + message khi HĐ khách hết trong 1 tháng cuối master lease |
| 8 | `hostConfirm` | `shortExploitationWarning` khi còn < 6 tháng khai thác |

---

## FE

- `GET /properties/{id}`: thêm `leaseStartDate`, `leaseEndDate` — form tạo HĐ khách chặn lúc nhập.
- `POST .../host-confirm`: vẫn cho ACTIVE sớm; đọc `leaseNotStartedWarning` / `shortExploitationWarning`.
- Tạo/sửa HĐ / đón khách: BE ném lỗi tiếng Việt (ngày cụ thể).
- Đón khách trước `lease.startDate` **không còn** lách bằng `max-early-move-in-days`.

**Cố ý không chặn:** duyệt giá trước ngày HĐ; import ngày vào ở trong quá khứ (nếu vẫn ≥ `lease.startDate`); HĐ khách ngắn hơn HĐ chủ nhà.
