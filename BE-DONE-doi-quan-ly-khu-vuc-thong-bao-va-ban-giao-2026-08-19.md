# BE-DONE — Đổi quản lý khu vực: thông báo khách & bàn giao (19/08/2026)

Đối chiếu file `BE-NEED-doi-quan-ly-khu-vuc-thong-bao-va-ban-giao-2026-08-19.md`.

**Kết luận:** dữ liệu `assignManager` / `removeManager` giữ nguyên. Đã bổ sung thông báo + endpoint gộp.

---

## 1 — Thông báo khách thuê (`MANAGER_CHANGED`) — ưu tiên cao

| Việc | Trạng thái |
|---|---|
| Gửi khi `assignManager` | Đã làm |
| Gửi khi `removeManager` | Đã làm |
| Nội dung có SĐT quản lý mới | Đã làm |
| HĐ nháp chưa có tài khoản → bỏ qua | Đã làm |
| Không gửi `TERMINATED` | Đã làm (`status <> TERMINATED`) |
| Push + in-app (`screen=ContractDetail`, `params.contractId`) | Đã làm |

Gán QL mới:

> Từ hôm nay, phòng 101 (MTX#13) do Nguyễn Văn A phụ trách. Liên hệ: 09xx. Mọi yêu cầu sửa chữa, hoá đơn, trả phòng vui lòng liên hệ số này.

Gỡ QL:

> Từ hôm nay, … tạm thời chưa có quản lý phụ trách. Khu vực đang chờ phân công quản lý mới, mọi yêu cầu vui lòng liên hệ tổng đài/chủ nhà.

---

## 2 — Thông báo QL mới có việc gấp (`ZONE_ASSIGNED`)

Ví dụ: *Bạn tiếp nhận khu vực Thủ Đức: 2 nhà · 9 hợp đồng. ⚠ 3 hợp đồng chờ đón khách, sớm nhất ngày 25/08/2026.*

Đếm HĐ `DRAFT`/`PENDING`; ngày sớm nhất lấy `expectedReceptionDate`.

---

## 3 — QL cũ còn mấy khu vực (`ZONE_REVOKED`)

Thêm câu: *Sau bàn giao, bạn còn phụ trách N khu vực.*

`GET /api/v1/managers/idle` — quản lý `ACTIVE` không phụ trách khu vực nào (`zoneCount: 0`).

---

## 4 — Endpoint chuyển hẳn (một transaction)

```
POST /api/v1/zones/manager-transfer
{
  "managerId": "...",
  "toZoneId": "...",
  "releaseZoneIds": ["..."]
}
```

Trong cùng transaction: gán vào `toZoneId`, rồi `removeManager` từng khu vực trong `releaseZoneIds` (bỏ qua nếu trùng `toZoneId` hoặc không phải QL này). PUT + DELETE cũ vẫn dùng được.

`DELETE /zones/{id}/manager` mở thêm `OWNER` cho khớp màn gán.

---

## FE cần biết

- Khách: type `MANAGER_CHANGED` → màn `ContractDetail` + `contractId`. Không cần FE tạo thông báo.
- Tick “gỡ khỏi khu vực cũ”: nên gọi `POST /zones/manager-transfer` thay vì PUT rồi DELETE.
- Banner QL rảnh: có thể dùng `GET /managers/idle` thay vì suy từ full list nhà.
