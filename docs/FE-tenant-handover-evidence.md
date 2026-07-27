# Biên bản bàn giao / bằng chứng onboard — Hướng dẫn FE (Tenant App)

Tenant xem **ảnh hiện trạng**, **chỉ số điện/nước đầu kỳ** và **thời điểm manager chụp** lúc onboard. Dùng để đối chiếu khi nhận phòng và làm bằng chứng sau này.

**Liên quan (manager ghi nhận timestamp lúc chụp):** [`FE-onboard-evidence-photo-timestamp.md`](./FE-onboard-evidence-photo-timestamp.md)

---

## 1. Tóm tắt nhanh

| Ai | Việc |
|----|------|
| Manager | Lúc onboard HĐ: chụp hiện trạng + đồng hồ, gửi URL + `capturedAt` (xem doc timestamp). |
| Tenant | Sau khi HĐ `ACTIVE`: mở màn **Biên bản bàn giao** → xem ảnh / chỉ số / ngày giờ → (tuỳ chọn) **Xác nhận đã xem**. |

```
[Manager onboard: ảnh + chỉ số + capturedAt]
                │
                ▼
        HĐ ACTIVE
                │
                ▼
┌───────────────────────────────┐
│  Tenant App                   │
│  GET /tenant/me/handover      │
│  → chỉ số điện/nước           │
│  → ảnh đồng hồ + giờ chụp     │
│  → ảnh hiện trạng + giờ chụp  │
│  → thiết bị bàn giao          │
│  [Xác nhận đã nhận bàn giao]  │
└───────────────────────────────┘
```

---

## 2. API

### 2.1. Lấy biên bản bàn giao

```http
GET /api/v1/tenant/me/handover
Authorization: Bearer <tenant-jwt>
```

**Điều kiện:** tenant có HĐ `ACTIVE`. Không có → `404` (`Không tìm thấy hợp đồng đang hiệu lực`).

### 2.2. Xác nhận đã xem / nhận bàn giao (tuỳ chọn UX)

```http
POST /api/v1/tenant/me/handover/acknowledge
Authorization: Bearer <tenant-jwt>
```

- Gọi **1 lần**. Đã xác nhận rồi → lỗi business: *Bạn đã xác nhận biên bản bàn giao trước đó*.
- Response cùng shape với GET.

### 2.3. Response (rút gọn — field quan trọng cho UI)

```json
{
  "contractId": 12,
  "contractCode": "HD-2026-0012",
  "propertyName": "Nhà trọ Minh Anh",
  "roomNumber": "P201",

  "initialElectricReading": 1234.5,
  "initialWaterReading": 56.0,
  "electricMeterImageUrl": "https://res.cloudinary.com/.../electric.jpg",
  "electricMeterCapturedAt": "2026-07-21T13:45:12",
  "waterMeterImageUrl": "https://res.cloudinary.com/.../water.jpg",
  "waterMeterCapturedAt": "2026-07-21T13:46:05",

  "roomConditionUrls": [
    "https://.../a.jpg",
    "https://.../b.jpg"
  ],
  "roomConditionPhotos": [
    { "url": "https://.../a.jpg", "capturedAt": "2026-07-21T13:47:01" },
    { "url": "https://.../b.jpg", "capturedAt": "2026-07-21T13:47:20" }
  ],
  "roomConditionNote": "Phòng sạch, tường góc phải có vết trầy nhẹ",

  "equipmentList": [],
  "equipmentSnapshot": "Giường (Tốt) x1, Điều hòa (Mới) x1",

  "acknowledged": false,
  "acknowledgedAt": null
}
```

| Field | Dùng UI |
|-------|---------|
| `initialElectricReading` / `initialWaterReading` | Số lớn trên card chỉ số |
| `electricMeterImageUrl` + `electricMeterCapturedAt` | Ảnh đồng hồ điện + caption ngày giờ |
| `waterMeterImageUrl` + `waterMeterCapturedAt` | Ảnh đồng hồ nước + caption ngày giờ |
| `roomConditionPhotos[]` | **Ưu tiên** — gallery hiện trạng kèm `capturedAt` từng ảnh |
| `roomConditionUrls` | Legacy — chỉ fallback nếu `roomConditionPhotos` rỗng |
| `roomConditionNote` | Ghi chú manager |
| `equipmentList` / `equipmentSnapshot` | Mục thiết bị bàn giao |
| `acknowledged` / `acknowledgedAt` | Trạng thái nút xác nhận |

**Format ngày giờ (khuyến nghị):** `dd/MM/yyyy HH:mm` (vd. `21/07/2026 13:45`).  
`capturedAt` là `LocalDateTime` ISO không timezone (`2026-07-21T13:45:12`) — hiển thị như giờ Việt Nam đã lưu, **không** convert UTC.

**Nguồn data khác (không bắt buộc):** cùng field evidence cũng có trong `GET /api/v1/tenant/me/contracts` / `GET /api/v1/tenant-contracts/{id}`. Màn tenant nên gọi **`/handover`** cho gọn.

---

## 3. Ý tưởng thiết kế UI (FE follow)

### 3.1. Entry point

Đặt 1 chỗ rõ trong Tenant App:

| Vị trí | Gợi ý |
|--------|--------|
| Tab **Hợp đồng** / **Nhà của tôi** | Card/row *“Biên bản bàn giao lúc nhận phòng”* |
| Dashboard sau login lần đầu (HĐ mới, `acknowledged = false`) | Banner mềm: *“Xem & xác nhận hiện trạng phòng khi nhận nhà”* → CTA mở màn này |
| Deep link từ thông báo onboard | Mở thẳng màn handover |

**Không** chôn trong settings. Đây là bằng chứng pháp lý nhẹ — cần dễ tìm.

### 3.2. Cấu trúc màn (1 scroll, 4 section)

Một màn, **một việc**: xem bằng chứng nhận phòng. Không dashboard, không card chồng card.

```
┌────────────────────────────────────────┐
│  ← Biên bản bàn giao                   │
│  HD-2026-0012 · Nhà trọ Minh Anh · P201│
├────────────────────────────────────────┤
│                                        │
│  CHỈ SỐ ĐẦU KỲ                         │
│  ┌─────────────┐  ┌─────────────┐      │
│  │ Điện        │  │ Nước        │      │
│  │ 1.234,5 kWh │  │ 56,0 m³     │      │
│  │ [ảnh đồng hồ]│  │ [ảnh đồng hồ]│     │
│  │ 21/07 13:45 │  │ 21/07 13:46 │      │
│  └─────────────┘  └─────────────┘      │
│                                        │
│  HIỆN TRẠNG PHÒNG / NHÀ                │
│  ┌────┐ ┌────┐ ┌────┐                  │
│  │img │ │img │ │img │  → tap fullscr  │
│  │13:47│ │13:47│ │13:48│               │
│  └────┘ └────┘ └────┘                  │
│  Ghi chú: tường góc phải vết trầy…     │
│                                        │
│  THIẾT BỊ BÀN GIAO                     │
│  • Giường · Tốt · x1                   │
│  • Điều hòa · Mới · x1                 │
│                                        │
│  [ Xác nhận đã nhận bàn giao ]         │
│  hoặc: Đã xác nhận lúc 22/07 09:10     │
└────────────────────────────────────────┘
```

### 3.3. Section — Chỉ số điện / nước

- Hai cột (mobile: stack dọc).
- **Số đọc** là hero của section (font lớn, rõ).
- Dưới số: thumbnail ảnh đồng hồ (tỉ lệ ~4:3 hoặc vuông crop).
- Caption dưới ảnh: icon đồng hồ nhỏ + `capturedAt` đã format.
- Tap ảnh → lightbox / full screen (pinch zoom nếu mobile).
- Nếu thiếu ảnh: vẫn hiện số; placeholder “Chưa có ảnh đồng hồ”.
- Nếu thiếu `capturedAt`: hiện “Chưa ghi nhận thời điểm” (không ẩn cả ảnh).

Đơn vị gợi ý: Điện `kWh`, Nước `m³` (BE trả `BigDecimal` thuần — FE gắn đơn vị).

### 3.4. Section — Hiện trạng

- Grid 2–3 cột (mobile 2 cột).
- Mỗi ô: ảnh fill + **overlay / caption đáy** ngày giờ chụp (`dd/MM/yyyy HH:mm`).
- **Bắt buộc** hiện timestamp trên UI — đây là điểm bằng chứng.
- Tap → viewer full screen, swipe giữa các ảnh, vẫn hiện timestamp trên viewer.
- `roomConditionNote` dưới gallery, kiểu text phụ (không box promo).
- Empty state: “Manager chưa tải ảnh hiện trạng lúc onboard.”

Ưu tiên data:

```ts
const photos = data.roomConditionPhotos?.length
  ? data.roomConditionPhotos
  : (data.roomConditionUrls ?? []).map(url => ({ url, capturedAt: null }));
```

### 3.5. Section — Thiết bị

- List đơn giản từ `equipmentList` (tên, tình trạng, số lượng).
- Fallback: nếu list rỗng mà có `equipmentSnapshot` → hiện text snapshot.
- Không cần ảnh thiết bị (BE chưa có).

### 3.6. CTA xác nhận

| `acknowledged` | UI |
|----------------|-----|
| `false` | Primary button *“Xác nhận đã nhận bàn giao”* → confirm dialog ngắn → `POST .../acknowledge` |
| `true` | Ẩn button; hiện dòng trạng thái: *Đã xác nhận lúc {acknowledgedAt}* |

Dialog confirm gợi ý:

> Bạn xác nhận đã xem chỉ số điện/nước và ảnh hiện trạng lúc nhận phòng?  
> [Huỷ] [Xác nhận]

Sau success: toast + cập nhật local state từ response (không cần GET lại).

**Lưu ý UX:** Xác nhận **không** chặn xem lại màn — tenant vẫn mở xem ảnh bất cứ lúc nào.

### 3.7. Loading / lỗi

| Case | UI |
|------|-----|
| Loading | Skeleton: header + 2 ô chỉ số + grid ảnh |
| 404 không HĐ ACTIVE | Empty: “Chưa có hợp đồng hiệu lực để xem biên bản.” |
| Ảnh load fail | Placeholder broken + vẫn giữ caption giờ |
| Acknowledge lỗi (đã xác nhận) | Toast message BE |

### 3.8. Nguyên tắc UI (tránh làm rối)

- Một màn = một mục đích (xem bằng chứng nhận phòng).
- Timestamp là **caption cố định** dưới / trên ảnh — không badge nổi, không sticker.
- Không nhồi stats, không card lồng card cho gallery.
- Manager app (ghi nhận lúc onboard) vẫn follow [`FE-onboard-evidence-photo-timestamp.md`](./FE-onboard-evidence-photo-timestamp.md) — tenant app **chỉ đọc**.

---

## 4. Checklist FE

### Tenant App
- [ ] Màn **Biên bản bàn giao** gọi `GET /api/v1/tenant/me/handover`
- [ ] Hiện chỉ số điện/nước + ảnh đồng hồ + `*CapturedAt`
- [ ] Gallery hiện trạng từ `roomConditionPhotos` + `capturedAt` từng ảnh
- [ ] Format giờ `dd/MM/yyyy HH:mm`
- [ ] Lightbox xem ảnh
- [ ] Nút acknowledge khi `acknowledged === false`
- [ ] Entry từ Hợp đồng / banner lần đầu nhận phòng

### Manager App (nhắc lại — để tenant có data)
- [ ] Khi chụp/upload: gửi `capturedAt` (giờ thiết bị) kèm URL
- [ ] Prefer `roomConditionPhotos` thay vì chỉ `roomConditionUrls`

---

## 5. Không nằm trong scope màn này

| Việc | Ghi chú |
|------|---------|
| Chỉ số điện/nước **hàng tháng** sau khi ở | API manager `meter-readings` — tenant chưa có endpoint riêng |
| Ảnh listing nhà (marketing) | `property.imageUrls` — khác bằng chứng onboard |
| Sửa / xoá ảnh bằng chứng | Tenant **read-only** |

Nếu sau này cần tenant xem lịch sử chỉ số tháng → mở ticket BE riêng.
