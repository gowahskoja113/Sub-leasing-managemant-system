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
| 6 | Chi phí tách / có phát sinh; sửa sau khi PAID | **Nhập 1 lần chi phí** → lập HĐ → sửa ngay → `WAITING_PAYMENT` → **CLOSED chỉ khi PAID**. Hạn thanh toán **3 ngày** |
| 7 | History thiết bị chưa đầy đủ / thiếu ảnh | Complete ghi history + ảnh. Admin xem trên web equipment |
| 8 | Tenant xem thiết bị hạn chế | Tenant xem cùng bộ thông tin trên mobile |

---

## 2. Luồng Manager (mobile)

```
OPEN (tenant tạo)
  │
  ├─ GET /{id}                    ← xem được ngay, KHÔNG cần QR
  │     response.equipment        ← card thiết bị (count, khấu hao, BH…)
  │     qrScanRequiredToProcess   ← true chỉ khi có equipmentId + chưa confirm-arrival
  │
  ├─ PUT /{id}/confirm-arrival    ← BẮT ĐẦU XỬ LÝ: bắt buộc quét QR (nếu có thiết bị)
  │     body: { "qrCode": "EQ-226" }  ← FE chuẩn hoá EQ-<id> OK; BE cũng nhận số trần / deep link
  │
  ├─ PUT /{id}/diagnose           ← không bắt buộc faultEvidenceImages
  │     quotedRepairAmount        ← 1 lần chi phí (bỏ chi phí phát sinh)
  │     TENANT_MISUSE + đồng ý trả → BE lập hoá đơn dueDate = today+3
  │
  ├─ PUT /{id}/start-repair       ← được sửa ngay (không chờ PAID; cần đã lập HĐ)
  │
  ├─ PUT /{id}/complete|handover  ← ghi history + ảnh
  │     thu tenant + chưa PAID → WAITING_PAYMENT
  │     không thu / đã PAID     → CLOSED
  │
  └─ Tenant thanh toán HĐ         → BE tự WAITING_PAYMENT → CLOSED
```

---

## 2b. Q&A chốt với FE (cập nhật)

| # | Câu hỏi | Quyết định |
|---|---------|------------|
| 1 | `remainingDepreciationAmount` trả full price khi hết BH thiếu `penaltyFee`? | **Bug** — đã sửa: hết BH dùng `penaltyFee`, không fallback giá mua. Mọi thiết bị **bắt buộc** có `penaltyFee` khi gán/tạo. |
| 2 | CLOSED khi chưa trả? | **Không** — sau sửa/bàn giao → `WAITING_PAYMENT`; chỉ `CLOSED` khi hoá đơn **PAID**. Quá 3 ngày: cron billing vẫn escalate (OVERDUE / đề xuất chấm dứt HĐ), phiếu **giữ** `WAITING_PAYMENT` đến khi trả. |
| 3 | FE chuẩn hoá QR → `EQ-<id>`? | **OK** — BE cũng normalize số trần / deep link chứa `EQ-xxx`. |
| 4 | Phiếu không gắn thiết bị mà `qrScanRequiredToProcess=true`? | **BE đã sửa** — cờ `true` chỉ khi `equipmentId != null` và chưa confirm-arrival. |
| 5 | List chậm vì load equipment đầy đủ? | **BE đã rút gọn** — list/detail dùng snapshot nhẹ (không gọi full EquipmentService). |

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
- [ ] Nút “Bắt đầu xử lý / Chẩn đoán”: nếu `qrScanRequiredToProcess === true` → mở camera quét → chuẩn hoá `EQ-<id>` → `confirm-arrival`
- [ ] Form diagnose: **1 ô chi phí**; bỏ UI “chi phí phát sinh”
- [ ] Form xác định lỗi: **không bắt buộc** upload ảnh thêm
- [ ] Sau diagnose lỗi tenant: hiện hoá đơn + hạn 3 ngày; **cho phép** start-repair dù chưa PAID
- [ ] Sau complete/handover: nếu status `WAITING_PAYMENT` → UI “chờ thanh toán”, **không** coi là CLOSED
- [ ] Subscribe `MAINTENANCE_WAITING_PAYMENT` / `MAINTENANCE_COMPLETED` để refetch

### Tenant app

- [ ] Màn thiết bị của tôi: dùng field khấu hao / BH còn lại
- [ ] Chi tiết thiết bị + history + ảnh các lần BT
- [ ] Thanh toán hoá đơn bảo trì trong vòng 3 ngày (`issuedInvoice.dueDate`)

### Admin web

- [ ] Trang quản lý equipment: hiển thị khấu hao còn lại, ngày mua, BH, BH còn lại
- [ ] Tab history: list từ `.../maintenance-history` + gallery `photoUrls`

---

## 5. Breaking / lưu ý tích hợp

1. **`confirm-arrival`** nhận body `{ qrCode }` (bắt buộc khi phiếu có `equipmentId`).
2. **`GET /equipment/{id}/maintenance-history`** trả history có `photoUrls`. List ticket: `.../maintenance-tickets`.
3. Status mới: **`WAITING_PAYMENT`** — map UI / filter / badge.
4. Hoá đơn bảo trì: `dueDate` = +3 ngày. Quá hạn: invoice → OVERDUE + cron notify; phiếu vẫn `WAITING_PAYMENT` đến khi PAID rồi mới CLOSED.
5. `penaltyFee` bắt buộc khi gán/tạo thiết bị. `remainingDepreciationAmount` hết BH = `penaltyFee` (không còn full price).
6. Gate sửa: chỉ cần **đã lập hoá đơn**, không cần **đã thanh toán**. Gate đóng phiếu: **đã thanh toán**.

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
