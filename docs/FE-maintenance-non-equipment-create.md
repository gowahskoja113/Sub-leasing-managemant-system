# FE Handoff — Tạo bảo trì không gắn thiết bị (category lúc tạo)

> Ngày: 2026-07-27  
> Base path: `/api/v1/maintenance`  
> Auth: Bearer JWT — role `TENANT` tạo, `MANAGER`/`ADMIN` duyệt.

---

## 1. Mục tiêu

Cho phép tenant **báo hư hao không phải trang thiết bị / nội thất** mà không bị kẹt vì thiếu `equipmentId`.

Luồng sau khi tạo (duyệt → sửa → xác nhận) **không đổi** — chỉ bổ sung bước chọn danh mục lúc tạo.

---

## 2. Hai nhánh tạo request

| Nhánh | Khi nào | Tenant làm gì |
|-------|---------|----------------|
| **A. Có thiết bị** | Báo hỏng máy lạnh, tủ lạnh, bàn ghế… trong danh sách bàn giao | Chọn thiết bị → tiêu đề → ảnh → gửi |
| **B. Không thiết bị** | Tường, điện cố định, nước/WC, khác | Tiêu đề → **chọn category** → ảnh → gửi |

`description` **không bắt buộc** ở cả hai nhánh.

---

## 3. Ý tưởng thiết kế UI

### 3.1 Nguyên tắc

- **Một việc / một màn:** chọn loại sự cố → điền form → gửi. Không nhồi category + list thiết bị cùng lúc.
- **Rào trước, viết sau:** category / thiết bị chọn từ list cố định; tenant chỉ gõ tiêu đề ngắn (+ mô tả tuỳ chọn).
- **Ảnh là bằng chứng chính:** CTA chụp/upload nổi, bắt buộc ≥ 1 ảnh trước khi enable nút Gửi.
- **Mô tả thu gọn:** mặc định ẩn hoặc collapse “Thêm mô tả (không bắt buộc)” — tránh form dài.
- Mobile-first (tenant app); manager web giữ layout list/detail hiện tại, chỉ bổ sung badge category.

---

### 3.2 Entry — chọn loại sự cố (Tenant)

Hai card / segmented control ở **đầu** màn tạo (hoặc màn trước form):

```
┌─────────────────────────────────────┐
│  Báo sự cố                          │
│                                     │
│  ┌──────────────┐ ┌──────────────┐  │
│  │  🔧          │ │  🏠          │  │
│  │  Thiết bị /  │ │  Hư hao khác │  │
│  │  nội thất    │ │  (tường,     │  │
│  │              │ │   điện, nước)│  │
│  └──────────────┘ └──────────────┘  │
│                                     │
│  Phòng: P01 · Nhà Lê Lợi            │
└─────────────────────────────────────┘
```

| Card | Hành vi |
|------|---------|
| Thiết bị / nội thất | Sang form nhánh A — hiện list/picker thiết bị bàn giao (+ QR nếu có) |
| Hư hao khác | Sang form nhánh B — hiện grid/list 4 danh mục |

Nếu phòng **không có thiết bị**: card A vẫn hiện nhưng empty-state *“Phòng chưa có thiết bị bàn giao — chọn Hư hao khác”* + deep-link sang nhánh B (tránh alert chặn tạo như bug cũ).

---

### 3.3 Form nhánh B — không gắn thiết bị

```
┌─────────────────────────────────────┐
│  ← Hư hao khác                      │
│                                     │
│  Danh mục *                         │
│  ┌──────────┐ ┌──────────┐          │
│  │ 🧱 Kết   │ │ ⚡ Điện  │          │
│  │ cấu      │ │ cố định  │          │
│  └──────────┘ └──────────┘          │
│  ┌──────────┐ ┌──────────┐          │
│  │ 🚿 Nước  │ │ … Khác   │          │
│  │ / WC     │ │          │          │
│  └──────────┘ └──────────┘          │
│  (chip chọn 1 — highlight border)   │
│                                     │
│  Tiêu đề *                          │
│  ┌─────────────────────────────┐    │
│  │ vd. Tường thấm góc phòng    │    │
│  └─────────────────────────────┘    │
│  gợi ý theo category đã chọn        │
│                                     │
│  ▸ Thêm mô tả (không bắt buộc)      │
│                                     │
│  Ảnh hiện trạng *                   │
│  ┌────┐ ┌────┐ ┌────┐               │
│  │ +  │ │img │ │img │  tối đa ~5    │
│  └────┘ └────┘ └────┘               │
│  Chụp rõ chỗ hư, đủ sáng            │
│                                     │
│  ┌─────────────────────────────┐    │
│  │        Gửi yêu cầu          │    │
│  └─────────────────────────────┘    │
└─────────────────────────────────────┘
```

**Chi tiết UX**

| Thành phần | Gợi ý |
|------------|--------|
| Danh mục | **Grid 2×2 chip/card**, không dùng free-text. Chọn 1. Map: `STRUCTURAL` / `ELECTRICAL` / `PLUMBING` / `OTHER` |
| Placeholder tiêu đề | Đổi theo category: STRUCTURAL → “vd. Sơn tường bong / thấm góc…”; ELECTRICAL → “vd. Ổ cắm cháy / đèn không sáng…”; PLUMBING → “vd. Vòi rò / bồn cầu tắc…”; OTHER → “Mô tả ngắn sự cố” |
| Mô tả | Accordion / link “Thêm mô tả”; không hiện textarea full ngay |
| Ảnh | Nút `+` mở camera trước (mobile); gallery phụ. Preview thumbnail + xoá từng ảnh |
| Nút Gửi | Disable đến khi: đã chọn category + title không trống + ≥ 1 ảnh |
| Loading | Disable form + spinner trên nút; success → về list “Yêu cầu của tôi” + toast |

**Label category (nhánh B) — chỉ 4 giá trị:**

| Value | Label ngắn | Subtitle (dưới card) |
|-------|------------|----------------------|
| `STRUCTURAL` | Kết cấu | Tường, sàn, trần, cửa, khóa |
| `ELECTRICAL` | Điện cố định | Ổ cắm, đèn, cầu dao |
| `PLUMBING` | Nước / WC | Vòi, ống, toilet, thoát sàn |
| `OTHER` | Khác | Không thuộc 3 nhóm trên |

> Không hiện `APPLIANCE` / `FURNITURE` ở nhánh B.

---

### 3.4 Form nhánh A — có thiết bị (giữ flow cũ, tinh chỉnh nhẹ)

```
┌─────────────────────────────────────┐
│  ← Báo hỏng thiết bị                │
│                                     │
│  Thiết bị *                         │
│  ┌─────────────────────────────┐    │
│  │ Máy lạnh Daikin · Phòng ngủ │  > │
│  └─────────────────────────────┘    │
│  hoặc [Quét QR]                     │
│                                     │
│  Tiêu đề *                          │
│  ▸ Thêm mô tả (không bắt buộc)      │
│  Ảnh hiện trạng *                   │
│                                     │
│  [ Gửi yêu cầu ]                    │
└─────────────────────────────────────┘
```

- Không bắt chọn `category` lúc tạo (manager gán khi duyệt, thường `APPLIANCE`/`FURNITURE`).
- `description` cũng không bắt buộc (đồng bộ nhánh B).

---

### 3.5 List & detail (Tenant)

| Chỗ | UI |
|-----|-----|
| List item | Title + badge status + **chip category** (nếu có) hoặc tên thiết bị |
| Detail `PENDING` | Block “Loại sự cố”: chip category **hoặc** card thiết bị (ảnh + tên). Không hiện “Chưa phân loại” nếu đã có category từ lúc tạo |
| Ảnh | Gallery BEFORE (và AFTER khi có) |

Chip màu gợi ý (tuỳ design system app):

| Category | Màu gợi ý |
|----------|-----------|
| `STRUCTURAL` | Xám / nâu nhạt |
| `ELECTRICAL` | Vàng / cam nhạt |
| `PLUMBING` | Xanh dương nhạt |
| `OTHER` | Trung tính |
| `APPLIANCE` / `FURNITURE` | Xanh lá / tím nhạt (nhánh thiết bị) |

---

### 3.6 Manager (Web) — duyệt

- Cột / filter list thêm `category` (kể cả ticket `PENDING` đã có category từ tenant).
- Dialog duyệt:
  - Nếu `category != null` → prefill dropdown, label phụ *“Tenant đã chọn — có thể đổi”*.
  - Nếu `category == null` → dropdown bắt buộc + helper *“Chọn danh mục trước khi duyệt”*.
- Priority vẫn optional (select nhỏ bên cạnh).

Không cần màn tạo riêng cho manager ở scope này.

---

### 3.7 Microcopy

| Chỗ | Copy gợi ý |
|-----|------------|
| Card B | “Hư hao khác (tường, điện, nước…)” |
| Helper ảnh | “Chụp rõ vị trí hư hỏng, đủ ánh sáng” |
| Empty thiết bị | “Phòng chưa có thiết bị — hãy báo ở Hư hao khác” |
| Success | “Đã gửi yêu cầu. Quản lý sẽ xem và phản hồi.” |
| Validate | “Chọn danh mục và thêm ít nhất 1 ảnh” |

---

### 3.8 Không làm (tránh scope creep)

- Không cho tenant gõ category tự do / “Khác” mở text thay enum.
- Không bắt mô tả dài.
- Không tính chi phí / ai trả trên màn tạo (billing sau `CLOSED`).
- Không đổi flow status sau `PENDING`.

---

## 4. API tạo

```http
POST /api/v1/maintenance
Authorization: Bearer {tenantToken}
Content-Type: application/json
```

### 4.1 Body — nhánh A (có thiết bị)

```json
{
  "roomId": 12,
  "equipmentId": 45,
  "title": "Máy lạnh không lạnh",
  "description": "Chảy nước ở góc tường",
  "images": [
    "https://.../before-1.jpg"
  ]
}
```

### 4.2 Body — nhánh B (không thiết bị) — thuê theo phòng

```json
{
  "roomId": 12,
  "title": "Tường thấm nước góc phòng",
  "category": "STRUCTURAL",
  "images": [
    "https://.../before-1.jpg"
  ]
}
```

### 4.2b Body — nguyên căn (không có `roomId`)

Dashboard nguyên căn trả `room.id = null`. FE **không** gửi `roomId`; gửi `propertyId` từ HĐ / dashboard (`building.propertyId` hoặc `contracts[].propertyId`).

```json
{
  "propertyId": 8,
  "title": "Mái nhà dột góc sân",
  "category": "STRUCTURAL",
  "images": [
    "https://.../before-1.jpg"
  ]
}
```

→ Ticket lưu `room = null`, gắn `propertyId`. Không còn lỗi 500 khi thiếu `roomId`.

### 4.3 Field

| Field | Bắt buộc | Ghi chú |
|-------|:--------:|---------|
| `roomId` | thuê theo phòng | Bắt buộc nếu thuê theo phòng |
| `propertyId` | nguyên căn | Bắt buộc khi **không** có `roomId` |
| `title` | ✅ | Tối đa 200 ký tự |
| `images` | ✅ | Ít nhất 1 URL ảnh BEFORE |
| `equipmentId` | nhánh A | Có khi chọn từ list thiết bị / QR |
| `category` | nhánh B | `STRUCTURAL` \| `ELECTRICAL` \| `PLUMBING` \| `OTHER` |
| `description` | ❌ | Có thể bỏ / `null` / `""` |

### 4.4 Response sau tạo (nhánh B — rút gọn)

```json
{
  "id": 102,
  "requestCode": "M-102",
  "title": "Tường thấm nước góc phòng",
  "description": null,
  "status": "PENDING",
  "category": "STRUCTURAL",
  "priority": null,
  "roomId": 12,
  "equipmentId": null,
  "beforeImages": ["https://.../before-1.jpg"],
  "createdAt": "2026-07-27T10:00:00"
}
```

→ Status: **`PENDING`**. Các bước sau giữ nguyên flow maintain hiện tại.

---

## 5. Lỗi BE cần bắt trên UI

| Tình huống | Message gợi ý từ BE |
|------------|---------------------|
| Thiếu `title` | Tiêu đề sự cố là bắt buộc |
| Thiếu ảnh | Bắt buộc đính kèm ảnh hiện trạng (BEFORE) |
| Thiếu `roomId` và `propertyId` | Thiếu vị trí sự cố: gửi roomId (thuê theo phòng) hoặc propertyId (thuê nguyên căn) |
| Nguyên căn sai property | Bạn không có hợp đồng nguyên căn đang hiệu lực cho bất động sản này |
| Nhánh B thiếu `category` | Danh mục hư hỏng (category) là bắt buộc khi không chọn trang thiết bị/nội thất… |
| Nhánh B gửi `APPLIANCE`/`FURNITURE` | Hư trang thiết bị/nội thất vui lòng chọn thiết bị (equipmentId)… |
| `category` sai enum | Danh mục không hợp lệ. Chọn một trong: APPLIANCE, FURNITURE, STRUCTURAL, ELECTRICAL, PLUMBING, OTHER |

---

## 6. Manager duyệt (không đổi bước, chỉ nới `category`)

```http
PUT /api/v1/maintenance/{id}/approve
```

| Ticket | Body `category` |
|--------|-----------------|
| Nhánh B — đã có `category` từ tenant | Có thể `{}` hoặc chỉ gửi `priority`; gửi `category` = ghi đè |
| Nhánh A — `category` còn `null` | **Bắt buộc** gửi `category` (`APPLIANCE` / `FURNITURE` / …) |

```json
{
  "category": "APPLIANCE",
  "priority": "HIGH"
}
```

`priority` luôn tùy chọn: `LOW` | `MEDIUM` | `HIGH` | `URGENT`.

---

## 7. Flow sau duyệt (không đổi)

```
PENDING
  → Manager approve          → APPROVED
  → Manager complete (+AFTER) → WAITING_TENANT_CONFIRM
  → Tenant confirm            → CLOSED
  → Tenant reject             → REJECTED → manager review-reject
```

Chi tiết đầy đủ: [`FE-maintenance-flow.md`](./FE-maintenance-flow.md), [`FE-MAINTENANCE-GUIDE.md`](./FE-MAINTENANCE-GUIDE.md).

---

## 8. Checklist FE

- [ ] Entry 2 card: Thiết bị / nội thất vs Hư hao khác
- [ ] Nhánh B: grid 4 category (không APPLIANCE/FURNITURE)
- [ ] Placeholder tiêu đề đổi theo category
- [ ] Mô tả collapse / không bắt buộc
- [ ] Bắt buộc ≥ 1 ảnh BEFORE; disable nút Gửi khi thiếu
- [ ] Empty thiết bị → hướng sang nhánh B (không alert chặn)
- [ ] List/detail: chip category + gallery ảnh
- [ ] Manager: prefill category nếu tenant đã chọn; bắt buộc nếu null
- [ ] Happy path nhánh B: tạo → duyệt → complete → tenant confirm → `CLOSED`
