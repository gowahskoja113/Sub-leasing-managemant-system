# FE Handoff — Timestamp ảnh bằng chứng onboard (điện/nước/hiện trạng)

Ngày: 2026-07-21  
BE: ảnh onboard trước đây **chỉ lưu URL**, không ghi thời điểm chụp. Đã bổ sung timestamp làm bằng chứng.

---

## Tóm tắt

| Ảnh | Field URL (cũ, vẫn giữ) | Field timestamp (mới) |
|-----|-------------------------|------------------------|
| Đồng hồ điện | `electricMeterImageUrl` | `electricMeterCapturedAt` |
| Đồng hồ nước | `waterMeterImageUrl` | `waterMeterCapturedAt` |
| Hiện trạng phòng | `roomConditionUrls` (legacy) | `roomConditionPhotos[].url` + `capturedAt` |

**Rule BE:** có URL mà FE không gửi `capturedAt` → BE tự ghi `LocalDateTime.now()` lúc lưu (thời điểm ghi nhận).  
**Khuyến nghị FE:** gửi thời điểm **chụp trên thiết bị** (ISO-8601) để bằng chứng chính xác hơn.

---

## Request — create / update draft

Endpoints không đổi:

- `POST /api/v1/properties/{id}/rooms/{roomId}/tenant-contract`
- `POST /api/v1/properties/{id}/tenant-contract`
- `PUT /api/v1/tenant-contracts/{id}`

### Body mới (khuyến nghị)

```json
{
  "electricMeterImageUrl": "https://...",
  "electricMeterCapturedAt": "2026-07-21T13:45:12",
  "waterMeterImageUrl": "https://...",
  "waterMeterCapturedAt": "2026-07-21T13:46:05",
  "roomConditionPhotos": [
    { "url": "https://.../a.jpg", "capturedAt": "2026-07-21T13:47:01" },
    { "url": "https://.../b.jpg", "capturedAt": "2026-07-21T13:47:20" }
  ],
  "roomConditionNote": "..."
}
```

### Tương thích FE cũ

Vẫn nhận:

```json
{
  "electricMeterImageUrl": "https://...",
  "waterMeterImageUrl": "https://...",
  "roomConditionUrls": ["https://.../a.jpg", "https://.../b.jpg"]
}
```

→ BE set `capturedAt = now()` cho từng ảnh.  
Nếu gửi cả `roomConditionPhotos` và `roomConditionUrls` → **ưu tiên `roomConditionPhotos`**.

---

## Response

`TenantContractResponse` / handover:

```json
{
  "electricMeterImageUrl": "https://...",
  "electricMeterCapturedAt": "2026-07-21T13:45:12",
  "waterMeterImageUrl": "https://...",
  "waterMeterCapturedAt": "2026-07-21T13:46:05",
  "roomConditionUrls": ["https://.../a.jpg", "https://.../b.jpg"],
  "roomConditionPhotos": [
    { "url": "https://.../a.jpg", "capturedAt": "2026-07-21T13:47:01" },
    { "url": "https://.../b.jpg", "capturedAt": "2026-07-21T13:47:20" }
  ]
}
```

- `roomConditionUrls`: vẫn trả (derived từ photos) — FE cũ không vỡ.
- UI bằng chứng: dùng `roomConditionPhotos` + meter `*CapturedAt`.

Áp dụng cả `GET` contract detail và `GET /tenant/me/handover`.

---

## Việc FE cần làm

1. Khi chụp / chọn ảnh: lưu `capturedAt` (thời điểm device, ISO local hoặc UTC có offset).
2. Upload Cloudinary như cũ → gửi kèm URL + `capturedAt` lên create/update draft.
3. Màn xem bằng chứng: hiện ngày giờ dưới mỗi ảnh (format `dd/MM/yyyy HH:mm`).
4. Prefer `roomConditionPhotos` thay vì chỉ `roomConditionUrls`.

---

## Checklist verify

- [ ] PUT draft có `electricMeterCapturedAt` / `waterMeterCapturedAt` → GET trả đúng.
- [ ] PUT chỉ URL, không gửi capturedAt → GET vẫn có timestamp (~ lúc lưu).
- [ ] PUT `roomConditionPhotos` → GET có `roomConditionPhotos` + `roomConditionUrls` khớp.
- [ ] PUT chỉ `roomConditionUrls` (legacy) → GET có photos với `capturedAt` không null.
- [ ] Handover tenant cũng trả đủ field mới.
