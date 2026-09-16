# BE xác nhận redesign luồng bảo trì (manager tới hiện trường) — 16/09/2026

**Phản hồi file:** `KE-HOACH-redesign-luong-bao-tri-2026-09-16.md`  
**Trạng thái:** BE đã chốt hướng + implement sẵn endpoint/field dưới đây. FE có thể soạn `BE-YEUCAU-*.md` / gắn UI theo contract này.

---

## Trả lời câu hỏi mở (mục 6)

| # | Câu hỏi | Quyết định BE |
|---|---|---|
| 1 | Gộp 1 endpoint linh hoạt theo status, hay tách riêng? | **Tách 2 endpoint** — khớp style hiện tại (`approve` / `reject-fault` / `confirm-arrival`), dễ audit timeline. `approve` / `reject-fault` **giữ nguyên** (backward compatible). Luồng mới dùng `send-for-inspection` + `diagnose`. |
| 2 | `companyAbsorbedFault` có cần note? | **Có** — boolean + `companyAbsorbedNote` (optional TEXT). Một khi `true` thì **không API bỏ tick** (immutable); chỉ dùng để tra cứu cuối tháng. |
| 3 | Lưu ngày dự kiến trả máy ban đầu? | **Có** — field `expectedReturnAt` (optional, không gate). Khác `repairAppointmentAt` = lịch giao/sửa **chính thức** sau chẩn đoán (bắt buộc ở nhánh mang đi). |

---

## Field mới trên `MaintenanceRequest` (trả về GET `/{id}` + list)

| Field | Type | Ý nghĩa |
|---|---|---|
| `companyAbsorbedFault` | boolean | Khách lỗi + từ chối trả → công ty trả hộ |
| `companyAbsorbedNote` | string? | Tóm tắt thoả thuận ngoài app |
| `expectedReturnAt` | datetime? | Dự kiến trả máy khi mang đi kiểm tra (tham khảo) |

`quotedRepairAmount` lúc diagnose được lưu vào **`estimatedDamageAmount`** (field sẵn có).

---

## API mới

### 1) `PUT /api/v1/maintenance/{id}/send-for-inspection`

**Khi nào:** sau quét QR (`confirm-arrival`), chọn **Mang đi kiểm tra thêm**.  
**Từ → tới:** `OPEN` → `REPAIR_SCHEDULED` (**chưa** set `damageCause` / `flowType`).

```json
{
  "expectedReturnAt": "2026-09-20T10:00:00",
  "category": "APPLIANCE",
  "note": "Mang máy lạnh về xưởng"
}
```

### 2) `PUT /api/v1/maintenance/{id}/diagnose`

**Khi nào:** màn **Chẩn đoán & báo giá** — gọi được từ:
- `OPEN` (sửa được ngay), hoặc
- `REPAIR_SCHEDULED` **và** `damageCause == null` (sau mang đi)

```json
{
  "quotedRepairAmount": 450000,
  "damageCause": "WEAR",
  "category": "APPLIANCE",
  "repairAppointmentAt": null
}
```

Lỗi khách + đồng ý trả:

```json
{
  "quotedRepairAmount": 450000,
  "damageCause": "TENANT_MISUSE",
  "tenantAgreesToPay": true,
  "faultReason": "Làm vỡ remote",
  "faultEvidenceImages": ["https://..."],
  "repairAppointmentAt": "2026-09-22T09:00:00"
}
```

Lỗi khách + từ chối trả (công ty trả hộ):

```json
{
  "quotedRepairAmount": 450000,
  "damageCause": "TENANT_MISUSE",
  "tenantAgreesToPay": false,
  "companyAbsorbedNote": "Khách không chịu trả, công ty tạm ứng",
  "faultReason": "Làm vỡ remote",
  "faultEvidenceImages": ["https://..."],
  "repairAppointmentAt": "2026-09-22T09:00:00"
}
```

### Bảng kết quả diagnose

| Nguyên nhân | `tenantAgreesToPay` | Status (OPEN, sửa ngay) | Status (sau mang đi / có hẹn) | Payment gate |
|---|---|---|---|---|
| `WEAR` | — | `IN_REPAIR` | `REPAIR_SCHEDULED` | Không |
| `TENANT_MISUSE` | `true` | `TENANT_FAULT` | `REPAIR_SCHEDULED` | Có (như cũ) |
| `TENANT_MISUSE` | `false` | `IN_REPAIR` + `companyAbsorbedFault=true` | `REPAIR_SCHEDULED` + flag | **Không** |

`repairAppointmentAt` **bắt buộc** khi đang ở nhánh mang đi (`REPAIR_SCHEDULED` chưa chẩn đoán).

### 3) Filter list

`GET /api/v1/maintenance?companyAbsorbedFault=true&from=...&to=...&propertyId=...`

- `from` / `to`: lọc theo `createdAt`
- Manager đã scope theo property của mình; thêm `propertyId` / tenant qua filter sẵn có nếu cần

---

## Gate / ràng buộc bổ sung

- `start-repair` / `handover`: **chặn** nếu `damageCause == null` (chưa diagnose).
- `charge` / payment gate `start-repair` / `handover` / `complete`: **bỏ qua** khi `companyAbsorbedFault=true`; `charge` bị reject.
- `start-repair` với công ty trả hộ → `IN_REPAIR` (không vào `TENANT_FAULT`).
- `complete` không lập hoá đơn thu khách khi `companyAbsorbedFault`.
- `billingHint` khi CLOSED + absorbed → `HOST_PAID`.

---

## FE gợi ý UI (không bắt buộc)

Sau `confirm-arrival`:

1. **Sửa được ngay** → mở màn diagnose → `PUT .../diagnose`
2. **Mang đi kiểm tra** → `PUT .../send-for-inspection` → khi thợ báo → cùng màn diagnose → `PUT .../diagnose`

Badge: `companyAbsorbedFault === true` → “Khách từ chối trả — công ty đã trả hộ”.  
Cuối tháng: list `companyAbsorbedFault=true` + khoảng `from`/`to`.

`approve` / `reject-fault` vẫn dùng được nếu FE chưa migrate hết.
