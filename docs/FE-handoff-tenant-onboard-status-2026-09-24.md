# Changelog + FE handoff — Onboard Tenant Contract Status (24/09/2026)

> **BE status:** ✅ Đã ship (compile OK)  
> **Người nhận:** FE Web Admin (màn **Hồ sơ đón khách**) + FE Mobile Manager/Tenant  
> **UML:** `docs/uml/main-flows/F2-tenant-contract-onboard-state.puml`

---

## 1. Tóm tắt thay đổi BE

Trước đây HĐ tenant chủ yếu dùng `DRAFT` / `PENDING` / `ACTIVE` — `PENDING` gộp nhiều bước (chờ đón, chờ trả, chờ OTP).

Giờ tách rõ pipeline onboard:

| Status (enum) | Nhãn VI (`statusLabel`) | Ý nghĩa |
|---|---|---|
| `DRAFT` | Chờ đến ngày đón | Admin import HĐ nháp |
| `AWAITING_ONBOARD` | Chờ onboard | Đã tới ngày đón — manager chụp hiện trạng + điện/nước |
| `AWAITING_PAYMENT` | Chờ thanh toán | Đã chụp xong — tạo QR / thu tiền onboard |
| `AWAITING_CONFIRM` | Chờ xác nhận hợp đồng | Đã thanh toán — dual OTP (tenant + manager) |
| `ACTIVE` | Đang hiệu lực | OTP đủ → kích hoạt |

Giữ `PENDING` trong enum **chỉ cho HĐ inbound (master lease)** — tenant onboard mới **không** set `PENDING`.

### Transition (động từ)

```
[*] → DRAFT              : Import
DRAFT → AWAITING_ONBOARD : Promote (cron tới ngày đón)
AWAITING_ONBOARD → AWAITING_PAYMENT : Capture (completeCapture=true)
AWAITING_PAYMENT → AWAITING_CONFIRM : Collect (PayOS PAID)
AWAITING_CONFIRM → ACTIVE : Confirm (dual OTP)
```

### File BE đã đụng

- `enums/ContractStatus.java` — status mới + `displayLabelVi()` + helper lists
- `TenantOnboardingServiceImpl` — transition + cron promote + `statusLabel` trong response
- `UpdateDraftContractRequest.completeCapture`
- `ContractLifecycleCron` — `promoteDraftsDueForOnboard` (00:10 + trước remind 07:15)
- `DatabaseSchemaMigration` — CHECK constraint + migrate data legacy `PENDING`
- `TenantActivationServiceImpl`, occupancy / zone / host portal filters
- `TenantContractResponse.statusLabel`

### Data migration (tự chạy lúc boot)

| Điều kiện cũ | Status mới |
|---|---|
| `status=PENDING` + `payment_status=PAID` | `AWAITING_CONFIRM` |
| `status=PENDING` (còn lại) | `AWAITING_PAYMENT` |

---

## 2. API cho màn Admin — Hồ sơ đón khách

### List pipeline đón khách (khuyến nghị)

```http
GET /api/v1/tenant-contracts?status=RECEPTION
Authorization: Bearer <ADMIN>
```

Alias tương đương: `status=ONBOARD` hoặc `status=PIPELINE`.

Trả về mọi HĐ đang trong onboard:  
`DRAFT` | `AWAITING_ONBOARD` | `AWAITING_PAYMENT` | `AWAITING_CONFIRM`.

### Lọc từng bước (tab / filter)

```http
GET /api/v1/tenant-contracts?status=DRAFT
GET /api/v1/tenant-contracts?status=AWAITING_ONBOARD
GET /api/v1/tenant-contracts?status=AWAITING_PAYMENT
GET /api/v1/tenant-contracts?status=AWAITING_CONFIRM
GET /api/v1/tenant-contracts?status=ACTIVE
```

Admin/Owner: thấy **toàn hệ thống**. Manager: chỉ HĐ phụ trách (`getManagedContracts`).

### Response — dùng badge

Mỗi item có:

```json
{
  "id": 123,
  "contractCode": "HD-MT-2026-00001",
  "status": "AWAITING_ONBOARD",
  "statusLabel": "Chờ onboard",
  "paymentStatus": "PENDING",
  "expectedReceptionDate": "2026-09-25",
  "moveInDate": "2026-09-25",
  "tenantFullName": "...",
  "propertyName": "...",
  "roomNumber": "101"
}
```

**FE:** hiển thị `statusLabel` (hoặc map `status` → màu badge). Không hard-code chỉ `DRAFT`/`PENDING` nữa.

### Gợi ý UI Admin

| Tab / cột Status | `status` | Màu gợi ý |
|---|---|---|
| Chờ đến ngày | `DRAFT` | xám |
| Chờ onboard | `AWAITING_ONBOARD` | xanh dương |
| Chờ thanh toán | `AWAITING_PAYMENT` | cam |
| Chờ xác nhận HĐ | `AWAITING_CONFIRM` | tím |
| Đã active | `ACTIVE` | xanh lá |

Có thể 1 bảng + filter chips theo 4 bước + Active.

---

## 3. Việc FE phải làm

### Bắt buộc (breaking)

1. **Thay mọi check `status === 'PENDING'`** của tenant contract bằng:
   - `AWAITING_ONBOARD` / `AWAITING_PAYMENT` / `AWAITING_CONFIRM` (hoặc `statusLabel`)
2. **Màn Hồ sơ đón khách (Admin):**
   - List: `GET .../tenant-contracts?status=RECEPTION`
   - Cột/badge: `status` + `statusLabel`
   - Filter theo từng bước (optional nhưng nên có)
3. **Mobile Manager — đón khách:**
   - Chụp hiện trạng khi `AWAITING_ONBOARD` (hoặc `DRAFT` trước ngày — vẫn edit được)
   - Xong chụp: `PUT /api/v1/tenant-contracts/{id}` body thêm `"completeCapture": true` → `AWAITING_PAYMENT`
   - Tạo QR cọc **chỉ** khi `AWAITING_PAYMENT`
4. **Tenant confirm OTP:** chỉ khi `AWAITING_CONFIRM` + đã PAID  
   (`GET .../me/tenant-contracts/pending-confirm` đã đổi rule BE)

### Không đổi

- Path API chính giữ nguyên
- PayOS webhook / dual OTP endpoint giữ nguyên
- `paymentStatus` (PENDING/PAID) vẫn tách riêng với `ContractStatus`

---

## 4. Checklist FE Admin (Hồ sơ đón khách)

- [ ] Gọi list `?status=RECEPTION` thay vì chỉ `DRAFT`/`PENDING`
- [ ] Render badge từ `statusLabel` (hoặc map 4 status mới)
- [ ] Tab/filter: DRAFT / AWAITING_ONBOARD / AWAITING_PAYMENT / AWAITING_CONFIRM
- [ ] Không còn copy “PENDING = chờ xử lý” chung chung
- [ ] Detail HĐ: hiện đúng bước hiện tại + CTA phù hợp (xem / nhắc manager / không thu tiền sớm)

---

## 5. Checklist FE Manager / Tenant (mobile)

- [ ] `completeCapture: true` sau khi đủ ảnh hiện trạng + điện/nước
- [ ] Disable “Tạo QR” nếu chưa `AWAITING_PAYMENT`
- [ ] Màn ép confirm OTP: `AWAITING_CONFIRM` (không còn `PENDING`)
- [ ] Activate account vẫn sau khi PAID (firstLogin) — không đổi

---

## 6. Tham chiếu nhanh

| Hành động | API | Status sau |
|---|---|---|
| Import Excel draft | `POST /api/v1/import/tenant-draft-contracts-excel` | `DRAFT` |
| Cron tới ngày | (tự động) | `AWAITING_ONBOARD` |
| Chụp xong | `PUT /tenant-contracts/{id}` + `completeCapture: true` | `AWAITING_PAYMENT` |
| Tạo QR | `POST /tenant-contracts/{id}/deposit-payment` | vẫn `AWAITING_PAYMENT` |
| Thanh toán OK | PayOS webhook | `AWAITING_CONFIRM` |
| Dual OTP | `POST .../confirm` + tenant `confirm-otp` | `ACTIVE` |
| Đón trễ ≥ 3 ngày | Cron `08:05` `autoCancelNoShowContracts` | `TERMINATED` → end |

### Rule đón khách trễ (no-show)

- Mốc: `expectedReceptionDate ?? moveInDate`
- Nếu HĐ còn trong onboard (`DRAFT` / `AWAITING_ONBOARD` / `AWAITING_PAYMENT` / `AWAITING_CONFIRM`)
  và **trễ ≥ 3 ngày** → tự hủy `TERMINATED` (`terminationType=NO_SHOW`)
- Config: `contract.no-show-grace-days: 3` (`application.yaml`)
