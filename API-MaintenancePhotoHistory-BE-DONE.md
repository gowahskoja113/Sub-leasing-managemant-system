# BE DONE — Lịch sử ảnh bảo trì (`photoHistory`)

> Người gửi: BE. Ngày: 2026-07-23.
> Nhánh: `dev`
> Liên quan TODO FE: `API-MaintenancePhotoHistory-BE-TODO.md`

---

## 1. Tóm tắt

BE đã xử lý xong yêu cầu **giữ toàn bộ lịch sử ảnh bảo trì** (không mất ảnh khi sửa lại / từ chối lại).

- Ảnh mọi vòng được ghi **append-only** vào bảng `maintenance_images`.
- Response thêm field mới `photoHistory` — FE dùng để hiển thị khối **"Lịch sử ảnh"** (đối chiếu tranh chấp).
- Field cũ `beforeImages` / `afterImages` / `rejectImages` **giữ nguyên** (snapshot vòng hiện tại) → màn hình chính FE **không cần sửa** để không bị vỡ.

---

## 2. Field mới trong response

Có trong mọi API trả `MaintenanceRequestResponse` (list + detail).

```json
{
  "id": 123,
  "status": "WAITING_TENANT_CONFIRM",
  "beforeImages": ["https://.../before-1.jpg"],
  "afterImages": ["https://.../after-2.jpg"],
  "rejectImages": ["https://.../reject-1.jpg"],
  "images": ["..."],
  "photoHistory": [
    {
      "type": "BEFORE",
      "url": "https://.../before-1.jpg",
      "createdAt": "2026-07-23T10:00:00"
    },
    {
      "type": "AFTER",
      "url": "https://.../after-1.jpg",
      "createdAt": "2026-07-23T15:03:00"
    },
    {
      "type": "REJECT",
      "url": "https://.../reject-1.jpg",
      "createdAt": "2026-07-23T16:10:00"
    },
    {
      "type": "AFTER",
      "url": "https://.../after-2.jpg",
      "createdAt": "2026-07-24T09:00:00"
    }
  ],
  "timeline": [ "... unchanged ..." ]
}
```

### `photoHistory[]`

| Field       | Type     | Mô tả                                      |
|-------------|----------|--------------------------------------------|
| `type`      | `string` | `BEFORE` \| `AFTER` \| `REJECT`            |
| `url`       | `string` | URL ảnh                                    |
| `createdAt` | `string` | ISO datetime — thời điểm ghi log           |

- Sắp xếp theo `createdAt` **tăng dần**.
- Gồm **toàn bộ ảnh mọi vòng**, không lọc / không xoá.
- Ticket cũ: BE đã backfill từ 3 cột CSV → `photoHistory` vẫn có dữ liệu (timestamp xấp xỉ).

---

## 3. Phân biệt field cũ vs mới (quan trọng cho UI)

| Field | Ý nghĩa | Dùng khi nào |
|-------|---------|--------------|
| `beforeImages` / `afterImages` / `rejectImages` | Snapshot **vòng hiện tại** | Màn chính (form upload, preview vòng đang làm) — **không đổi** |
| `photoHistory` | Log **đầy đủ mọi vòng** | Khối **"Lịch sử ảnh"** trên Chi tiết yêu cầu (tenant + manager) |

Ví dụ sau khi manager **chấp nhận từ chối → sửa lại** (`status = APPROVED`):

- `afterImages` = `[]` (đã reset snapshot vòng hiện tại → bắt buộc chụp AFTER mới).
- `photoHistory` vẫn còn các ảnh `AFTER` / `REJECT` vòng trước.

→ FE **không** lấy lịch sử từ `afterImages`/`rejectImages`; phải dùng `photoHistory`.

---

## 4. Hành vi BE đã sửa (để FE biết không còn mất ảnh)

| Bước | Trước | Sau |
|------|-------|-----|
| Manager `complete()` lần 2 | Xoá `rejectReason` + `rejectImageUrls` vòng trước | **Giữ nguyên**; ảnh REJECT vẫn nằm trong `photoHistory` |
| Manager `reviewReject(approve=true)` | Xoá hẳn `afterImageUrls` (mất vĩnh viễn) | Ảnh AFTER cũ đã nằm trong `photoHistory`; chỉ reset snapshot vòng hiện tại |
| `uploadPhotos` / `create` / `reject` / `complete` | Chỉ ghi CSV cột TEXT | Đồng thời **insert** row vào `maintenance_images` |

---

## 5. Việc FE cần làm

1. **Không bắt buộc** đổi màn chính đang dùng `beforeImages` / `afterImages` / `rejectImages`.
2. Trên **Chi tiết yêu cầu** (tenant + manager): thêm section **"Lịch sử ảnh"** đọc từ `photoHistory`.
3. Gợi ý UI:
   - Group theo `type` hoặc theo thời gian.
   - Label rõ: `BEFORE` / `AFTER` / `REJECT` + `createdAt`.
   - Có thể đối chiếu với `timeline` (cùng trục thời gian).
4. Khi `status = APPROVED` sau reopen: `afterImages` rỗng là **đúng** — vẫn show lịch sử qua `photoHistory`.

---

## 6. Backward compatible

- Field cũ không đổi tên / không đổi kiểu.
- `photoHistory` là field **mới** — client cũ bỏ qua cũng không vỡ.
- Không có API riêng `GET .../photo-history` (đã nhúng vào response chính). Nếu payload quá lớn sau này mới tách.

---

## 7. Checklist FE

- [ ] Pull / sync nhánh `dev` (BE đã có thay đổi).
- [ ] Parse `photoHistory` từ detail response.
- [ ] Thêm UI "Lịch sử ảnh" trên màn Chi tiết (tenant + manager).
- [ ] Smoke: tạo yêu cầu → complete → reject kèm ảnh → reviewReject approve → complete lần 2 → kiểm tra `photoHistory` còn đủ ảnh vòng 1 + vòng 2.
