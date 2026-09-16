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
| `equipmentReplacementFlagged` | boolean | Diagnose đã chốt thiết bị cần thay mới — FE đọc ở `charge()`/`complete()` |

`quotedRepairAmount` lúc diagnose:
- **Không thay thiết bị:** lưu vào `estimatedDamageAmount` (field sẵn có).
- **Có thay thiết bị** (`equipmentNeedsReplacement=true`): `estimatedDamageAmount` = giá trị thay thế (FE tính); `quotedRepairAmount` (tuỳ chọn) = chi phí phát sinh thêm → lưu `invoiceAmount`.

**Không** suy luận “có thay mới” từ `estimatedDamageAmount > 0` — dùng `equipmentReplacementFlagged`.

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

Sửa thường (không thay thiết bị):

```json
{
  "quotedRepairAmount": 450000,
  "damageCause": "WEAR",
  "category": "APPLIANCE",
  "repairAppointmentAt": null
}
```

Thiết bị hỏng hoàn toàn — cần thay mới (áp dụng đủ 4 nhánh: sửa ngay / mang đi × WEAR / TENANT_MISUSE):

```json
{
  "equipmentNeedsReplacement": true,
  "estimatedDamageAmount": 2500000,
  "quotedRepairAmount": 150000,
  "damageCause": "WEAR",
  "category": "APPLIANCE",
  "repairAppointmentAt": null
}
```

- `estimatedDamageAmount`: **bắt buộc** khi thay mới — FE tự tính (khấu hao còn lại / penaltyFee), BE không tính lại.
- `quotedRepairAmount`: **tuỳ chọn** khi thay mới — chi phí phát sinh thêm (vd lắp đặt) → `invoiceAmount`.

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

Khi `equipmentNeedsReplacement=true` + `WEAR` (hoặc TENANT_MISUSE + absorbed): công ty tự chịu — không lập hoá đơn thu khách; số tiền chỉ lưu tham khảo. FE ở `complete()` đọc `equipmentReplacementFlagged` để gửi `equipmentNeedsReplacement=true` (không suy luận từ `estimatedDamageAmount > 0`).

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
- `charge()` / `complete()`: **không đổi** — `resolveMaintenanceChargeAmount()` đã đúng (`estimatedDamageAmount` + `invoiceAmount` tuỳ chọn khi replacement). FE chỉ cần đọc `equipmentReplacementFlagged`.
- `handover()`: khi `equipmentReplacementFlagged=true`, BE tự gọi `applyEquipmentReplacementOnComplete()` (đọc cờ từ diagnose, không cần field thêm trên `MaintenanceHandoverRequest`).
- Ngay lúc `diagnose(equipmentNeedsReplacement=true)`: set `Equipment.status=BROKEN`, `recommendReplacement=true`, và `notifyAdmins` type `EQUIPMENT_NEEDS_REPLACEMENT` (không đợi charge/complete). Khi `complete()`/`handover()` thay xong → `status=NEW`, `recommendReplacement=false` như cũ. Ticket huỷ sau đó **giữ** `BROKEN` (admin tự xử lý).

---

## FE gợi ý UI (không bắt buộc)

Sau `confirm-arrival`:

1. **Sửa được ngay** → mở màn diagnose → `PUT .../diagnose`
2. **Mang đi kiểm tra** → `PUT .../send-for-inspection` → khi thợ báo → cùng màn diagnose → `PUT .../diagnose`

Badge: `companyAbsorbedFault === true` → “Khách từ chối trả — công ty đã trả hộ”.  
Cuối tháng: list `companyAbsorbedFault=true` + khoảng `from`/`to`.

`approve` / `reject-fault` vẫn dùng được nếu FE chưa migrate hết.

Khối **thiết bị hỏng hoàn toàn — cần thay mới** trên màn diagnose: tick → nhập `estimatedDamageAmount` (FE tính), `quotedRepairAmount` tuỳ chọn. Sau đó ở `charge()`/`complete()` gửi `equipmentNeedsReplacement` theo `equipmentReplacementFlagged` từ GET phiếu.
