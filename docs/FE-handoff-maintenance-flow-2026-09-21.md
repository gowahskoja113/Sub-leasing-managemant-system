# FE Handoff — Chỉnh luồng bảo trì (21/09/2026)

> **BE status:** ✅ Đã ship (compile OK)  
> **Người nhận:** FE Mobile (Manager/Tenant) + FE Web (Admin equipment)  
> **UML:** `docs/uml/main-flows/F4-maintenance-sequence.puml`, `F4-maintenance-class.puml`

---

## 1. Tóm tắt thay đổi nghiệp vụ

| # | Trước | Sau |
|---|--------|-----|
| 1 | Tenant tạo ticket | **Không đổi** |
| 2 | Manager phải quét QR mới xem được ticket | **Xem ticket không cần QR**. Chỉ quét khi **bắt đầu xử lý** |
| 3 | Màn xem ticket thiếu info thiết bị | GET phiếu trả snapshot: số lần BT, khấu hao còn lại, ngày mua, BH, BH còn lại |
| 4 | Phần sau (chẩn đoán → sửa → xong) | **Giữ như cũ** |
| 5 | Xác định lỗi bên nào bắt buộc upload thêm ảnh | **Không bắt buộc** thêm ảnh (đã có ảnh tenant + xác nhận hiện trường) |
| 6 | Chi phí tách / có phát sinh; sửa sau khi PAID | **Nhập 1 lần chi phí** → lập HĐ ngay → sửa ngay. Tenant thanh toán **trễ nhất 3 ngày** kể từ lúc tạo HĐ |
| 7 | History thiết bị chưa đầy đủ / thiếu ảnh | Complete ghi history + ảnh. Admin xem trên web equipment |
| 8 | Tenant xem thiết bị hạn chế | Tenant xem cùng bộ thông tin trên mobile |

---

## 2. Luồng Manager (mobile)

```
OPEN (tenant tạo)
  │
  ├─ GET /{id}                    ← xem được ngay, KHÔNG cần QR
  │     response.equipment        ← card thiết bị (count, khấu hao, BH…)
  │     qrScanRequiredToProcess   ← true nếu chưa confirm-arrival
  │
  ├─ PUT /{id}/confirm-arrival    ← BẮT ĐẦU XỬ LÝ: bắt buộc quét QR
  │     body: { "qrCode": "EQ-226" }
  │
  ├─ PUT /{id}/diagnose           ← không bắt buộc faultEvidenceImages
  │     quotedRepairAmount        ← 1 lần chi phí (bỏ chi phí phát sinh)
  │     TENANT_MISUSE + đồng ý trả → BE lập hoá đơn dueDate = today+3
  │
  ├─ PUT /{id}/start-repair       ← được sửa ngay (không chờ PAID)
  │
  └─ PUT /{id}/complete           ← ghi EquipmentMaintenanceHistory + ảnh
```

---

## 3. Contract API — field / body mới hoặc đổi

### 3.1 `GET /api/v1/maintenance/{id}` (và list)

Thêm:

| Field | Type | Ý nghĩa |
|-------|------|---------|
| `equipment` | `EquipmentResponse` | Snapshot thiết bị trên phiếu |
| `qrScanRequiredToProcess` | `boolean` | `true` = chưa quét QR, chặn nút chẩn đoán/duyệt |

Trong `equipment` (dùng cho card trên màn ticket + trang equipment):

| Field | Ý nghĩa |
|-------|---------|
| `maintenanceCount` | Số lần đã bảo trì |
| `purchasedAt` | Ngày mua / lắp |
| `warrantyMonths` | Thời hạn BH (tháng) |
| `warrantyStartDate` / `warrantyEndDate` | Khoảng BH |
| `remainingDepreciationAmount` | Khấu hao / giá trị còn lại (VNĐ) |
| `remainingWarrantyMonths` | Tháng BH còn lại |
| `remainingWarrantyYears` | Năm (phần nguyên của months/12) |
| `remainingWarrantyLabel` | Ví dụ `"Còn 1 năm 3 tháng"`, `"Hết bảo hành"` |
| `penaltyFee` | Phạt khi hết BH (nếu có) |

### 3.2 `PUT /api/v1/maintenance/{id}/confirm-arrival`

Body (mới — bắt buộc khi phiếu có `equipmentId`):

```json
{ "qrCode": "EQ-226" }
```

- QR không khớp thiết bị trên phiếu → `400`
- Phiếu không gắn thiết bị → không cần `qrCode`
- **Xem phiếu không gọi API này**

### 3.3 `PUT /api/v1/maintenance/{id}/diagnose`

| Field | Đổi |
|-------|-----|
| `quotedRepairAmount` | **1 lần chi phí duy nhất** (không còn “chi phí phát sinh thêm”) |
| `faultEvidenceImages` | **Optional** — không bắt buộc |
| `estimatedDamageAmount` | Khi thay mới: optional; null → BE lấy khấu hao còn lại |

Luồng lỗi tenant + `tenantAgreesToPay=true`:

- BE **lập hoá đơn ngay**, `dueDate = createdAt + 3 ngày`
- Response có `issuedInvoice` / `chargeInvoiceId`
- Manager **sửa ngay** sau đó (không gate PAID)

### 3.4 `reject-fault` / `report-fault`

`faultEvidenceImages` → **optional** (cùng lý do: đã đủ bằng chứng).

### 3.5 Equipment — Admin web + Tenant mobile

| Method | Path | Ai dùng |
|--------|------|---------|
| `GET` | `/api/v1/equipment/{id}` | Admin/Manager web — chi tiết + khấu hao/BH |
| `GET` | `/api/v1/equipment/{id}/maintenance-history` | History có `photoUrls` |
| `GET` | `/api/v1/equipment/{id}/maintenance-tickets` | Danh sách phiếu gắn thiết bị (giữ tương thích) |
| `GET` | `/api/v1/tenant/me/equipments` | Tenant list |
| `GET` | `/api/v1/tenant/me/equipments/{id}` | Tenant chi tiết |
| `GET` | `/api/v1/tenant/me/equipments/{id}/maintenance-history` | Tenant xem history + ảnh |

`EquipmentMaintenanceHistoryResponse` thêm:

```ts
photoUrls?: string[]  // BEFORE + AFTER + INVOICE của lần BT
```

---

## 4. Checklist FE

### Manager app

- [ ] Màn list/detail ticket: **bỏ** chặn “phải quét QR mới mở được”
- [ ] Hiển thị card thiết bị từ `response.equipment` (count, khấu hao, ngày mua, BH còn lại)
- [ ] Nút “Bắt đầu xử lý / Chẩn đoán”: nếu `qrScanRequiredToProcess === true` → mở camera quét → gọi `confirm-arrival` với `qrCode`
- [ ] Form diagnose: **1 ô chi phí**; bỏ UI “chi phí phát sinh”
- [ ] Form xác định lỗi: **không bắt buộc** upload ảnh thêm
- [ ] Sau diagnose lỗi tenant: hiện hoá đơn + hạn 3 ngày; **cho phép** start-repair / sửa dù chưa PAID
- [ ] Không chờ `invoice.status === PAID` để enable nút sửa

### Tenant app

- [ ] Màn thiết bị của tôi: dùng field khấu hao / BH còn lại
- [ ] Chi tiết thiết bị + history + ảnh các lần BT
- [ ] Thanh toán hoá đơn bảo trì trong vòng 3 ngày (`issuedInvoice.dueDate`)

### Admin web

- [ ] Trang quản lý equipment: hiển thị khấu hao còn lại, ngày mua, BH, BH còn lại
- [ ] Tab history: list từ `.../maintenance-history` + gallery `photoUrls`

---

## 5. Breaking / lưu ý tích hợp

1. **`confirm-arrival`** đổi signature: nhận body `{ qrCode }` (có thể `null` body nếu phiếu không có thiết bị).
2. **`GET /equipment/{id}/maintenance-history`** trước đây có thể trả list ticket; giờ trả **history có ảnh**. List ticket chuyển sang `.../maintenance-tickets`.
3. Hoá đơn bảo trì: `dueDate` mặc định **+3 ngày** (`TenantPendingChargeService.MAINTENANCE_CHARGE_DUE_DAYS = 3`) — đã có sẵn phía BE.
4. Gate sửa: chỉ cần **đã lập hoá đơn**, không cần **đã thanh toán**.

---

## 6. File BE liên quan (tham chiếu)

| File | Việc |
|------|------|
| `MaintenanceServiceImpl.java` | QR gate, diagnose 1 cost, invoice +3d, history |
| `MaintenanceConfirmArrivalRequest.java` | Body QR |
| `EquipmentAssetCalculator.java` | Khấu hao / BH còn lại |
| `EquipmentResponse.java` | Field mới |
| `EquipmentMaintenanceHistory` + Response | `photoUrls` |
| `GlobalEquipmentController` / `TenantMeController` | API xem thiết bị |
| `F4-maintenance-*.puml` | Sequence / class cập nhật |

---

*Handoff BE → FE — cập nhật khi đổi contract.*
