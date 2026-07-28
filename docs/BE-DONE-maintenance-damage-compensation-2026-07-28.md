# BE DONE — Luồng bồi thường khi khách thuê làm hư (maintenance damage compensation)

**Ngày:** 2026-07-28  
**Nguồn plan:** `PLAN-maintenance-damage-compensation-flow-2026-07-28.md`  
**Phạm vi:** Backend Spring Boot (`slms2026`) — FE mobile chưa làm trong báo cáo này.  
**Compile:** OK (`./mvnw -DskipTests compile`)

---

## 1. Tóm tắt

Sau khi manager báo sửa xong ticket bảo trì, BE hỗ trợ:

1. Ghi **ai chịu chi phí** (`HOST` / `TENANT`) + số tiền đã chốt (`repairCost`).
2. Nếu khách chịu → ticket chờ khách **đồng ý bồi thường** (`costAgreementStatus=PENDING`).
3. Khách đồng ý → tự tạo `TenantPendingCharge` + hoá đơn `MAINTENANCE` + QR PayOS (nếu đã cấu hình).
4. Khách khiếu nại → ticket vẫn `CLOSED`, cờ `DISPUTED`, **không** tự thu tiền.

**Nguyên tắc cứng:** không nhánh nào tự charge khi khách chưa bấm đồng ý. Auto-confirm 3 ngày chỉ đóng ticket theo chất lượng sửa — không ép đồng ý tiền.

---

## 2. Checklist đối chiếu plan §6

| # | Yêu cầu plan | Đã làm? | Ghi chú |
|---|--------------|:-------:|--------|
| 1 | Fix map `penaltyFee` ở `GET /api/v1/equipment/{id}` | ✅ | `EquipmentServiceImpl.toResponse` |
| 2 | Mở rộng `MaintenanceCompleteRequest` (`repairCost`, `costPaidBy`, `cause`) | ✅ | |
| 3 | Thêm `costAgreementStatus` trên entity (không đổi `MaintenanceStatus`) | ✅ | + `costDisputeReason` |
| 4 | `MaintenanceConfirmRequest.agreeToCharge` bắt buộc khi PENDING | ✅ | + `chargeDisputeReason` |
| 5 | `confirm()` → AGREED tạo charge+invoice / DISPUTED không thu | ✅ | Trả `issuedInvoice` kèm QR |
| 6 | `TenantPendingCharge.maintenanceRequestId` | ✅ | |

---

## 3. Thay đổi dữ liệu

### Enum mới

`CostAgreementStatus`

| Value | Nghĩa |
|-------|--------|
| `NOT_APPLICABLE` | Chủ nhà chịu / không thu khách (mặc định) |
| `PENDING` | Đã complete với `costPaidBy=TENANT`, chờ khách trả lời |
| `AGREED` | Khách đồng ý trả → đã/đang tạo hoá đơn |
| `DISPUTED` | Khách khiếu nại số tiền → không auto-charge |

### Entity

| Entity | Field mới | Kiểu | Ghi chú |
|--------|-----------|------|--------|
| `MaintenanceRequest` | `costAgreementStatus` | enum STRING | default `NOT_APPLICABLE` |
| `MaintenanceRequest` | `costDisputeReason` | TEXT | lý do khiếu nại (nullable) |
| `TenantPendingCharge` | `maintenanceRequestId` | Long nullable | tham chiếu ticket rõ ràng |

> `ddl-auto: update` — cột mới tự thêm khi app start. Không cần migration tay.

Field sẵn có (đã dùng, trước đây chưa set qua API): `costPaidBy`, `cause`, `repairCost`, `equipmentId`.

---

## 4. API thay đổi

### 4.1 `PUT /api/v1/maintenance/{id}/complete` (MANAGER / ADMIN)

Body mở rộng:

```json
{
  "resolutionNote": "Đã thay cảm biến",
  "afterImages": ["https://..."],
  "costPaidBy": "TENANT",
  "cause": "MISUSE",
  "repairCost": 4000000
}
```

| `costPaidBy` | Validate | `costAgreementStatus` |
|--------------|----------|------------------------|
| `HOST` hoặc null | `cause`/`repairCost` optional (có thể ghi cho thống kê) | `NOT_APPLICABLE` |
| `TENANT` | **Bắt buộc** `cause` + `repairCost > 0` | `PENDING` |

Status flow:

- `APPROVED` → `WAITING_TENANT_CONFIRM` (như cũ)
- Cho gọi lại khi đang `WAITING_TENANT_CONFIRM` để **sửa** `repairCost` / `costPaidBy` trước khi khách phản hồi (TC #8)

### 4.2 `PUT /api/v1/maintenance/{id}/confirm` (TENANT)

Body mở rộng:

```json
{
  "accept": true,
  "agreeToCharge": true,
  "chargeDisputeReason": null
}
```

| Điều kiện | Hành vi |
|-----------|---------|
| `costAgreementStatus != PENDING` | Như cũ: đóng ticket, không hỏi tiền |
| `PENDING` + thiếu `agreeToCharge` | `BusinessException` — bắt buộc gửi true/false |
| `PENDING` + `agreeToCharge=true` | `AGREED` → tạo charge + invoice MAINTENANCE + PayOS QR → response có `issuedInvoice` |
| `PENDING` + `agreeToCharge=false` | `DISPUTED` + lưu `costDisputeReason` → đóng ticket, **không** tạo charge, notify manager |

`accept=false` vẫn bị từ chối — từ chối chất lượng sửa dùng `/reject`.

### 4.3 Response `MaintenanceRequestResponse` thêm field

| Field | Khi nào có |
|-------|------------|
| `costAgreementStatus` | Luôn (null legacy → map `NOT_APPLICABLE`) |
| `costDisputeReason` | Khi khách khiếu nại |
| `issuedInvoice` | Chỉ lúc confirm vừa tạo hoá đơn (có `id`, `code`, `grandTotal`, `payosCheckoutUrl`, `payosQrCode`, …) |

### 4.4 Equipment

`GET /api/v1/equipment/{id}` (và các response đi qua `EquipmentServiceImpl.toResponse`) giờ trả `penaltyFee` — FE dùng để gợi ý số tiền khi hết bảo hành.

---

## 5. Luồng nghiệp vụ (BE)

```
Manager complete
  ├─ HOST  → WAITING, costAgreementStatus=NOT_APPLICABLE
  └─ TENANT → WAITING, costAgreementStatus=PENDING, repairCost chốt cứng
        │
        ▼
Tenant
  ├─ /reject (chất lượng)     → REJECTED (tiền chưa hỏi; vòng sau hỏi lại)
  └─ /confirm
        ├─ không PENDING tiền  → CLOSED
        ├─ agreeToCharge=true  → CLOSED + AGREED + charge + invoice + QR
        └─ agreeToCharge=false → CLOSED + DISPUTED (manager xử lý tay)
```

**Auto-confirm cron (3 ngày):** vẫn `CLOSED`, nếu còn `PENDING` tiền → **giữ PENDING**, không tạo charge, timeline ghi chú không tự thu.

**Review-reject reopen (`approve=true`):** clear `repairCost` / `cause` / `costAgreementStatus` / `costDisputeReason` — manager điền lại khi complete vòng mới.

---

## 6. File đã đụng

| File | Thay đổi |
|------|----------|
| `enums/CostAgreementStatus.java` | **Mới** |
| `entity/MaintenanceRequest.java` | + `costAgreementStatus`, `costDisputeReason` |
| `entity/TenantPendingCharge.java` | + `maintenanceRequestId` |
| `dto/request/MaintenanceCompleteRequest.java` | + cost fields |
| `dto/request/MaintenanceConfirmRequest.java` | + `agreeToCharge`, `chargeDisputeReason` |
| `dto/response/MaintenanceRequestResponse.java` | + agreement + `issuedInvoice` |
| `dto/response/TenantPendingChargeResponse.java` | + `maintenanceRequestId` |
| `service/TenantPendingChargeService.java` | + `createAndIssueMaintenanceCharge` |
| `service/impl/TenantPendingChargeServiceImpl.java` | implement charge→invoice→PayOS |
| `service/impl/MaintenanceServiceImpl.java` | wire complete/confirm/reviewReject/auto-confirm |
| `service/impl/EquipmentServiceImpl.java` | map `penaltyFee` |

---

## 7. Hợp đồng khi tạo charge

`resolveActiveContract(ticket)`:

1. Có `roomId` → HĐ ACTIVE theo phòng; fallback HĐ nguyên căn (`room IS NULL`) cùng property.
2. Không `roomId` → HĐ nguyên căn ACTIVE theo property.
3. Phải thuộc đúng tenant của ticket — không khớp → `BusinessException`.

Charge: `category="MAINTENANCE"`, `note` chứa `#ticketId`, `maintenanceRequestId` set rõ.  
Invoice due date mặc định: **+7 ngày**.  
PayOS: chỉ gọi nếu `payosService.isConfigured()`; lỗi PayOS **không** rollback hoá đơn (log warn) — FE vẫn có invoiceId để gọi tạo QR sau.

---

## 8. FE checklist (chưa làm — handoff)

- [ ] Màn manager “Báo sửa xong”: khối chọn HOST/TENANT + cause + repairCost
- [ ] Có `equipmentId` → gợi ý theo công thức plan §5 (còn BH: khấu hao còn lại; hết BH: `penaltyFee`); manager được sửa trước khi gửi
- [ ] Không `equipmentId` → để trống, nhập tay
- [ ] Màn tenant: khối bồi thường **tách** khỏi khối xác nhận chất lượng sửa
- [ ] Confirm gửi `agreeToCharge` khi `costAgreementStatus=PENDING`
- [ ] Khi response có `issuedInvoice` → điều hướng màn hoá đơn/QR sẵn có
- [ ] Types DTO khớp BE (`Complete` / `Confirm` / response mới)

---

## 9. Ngoài phạm vi v1 (giữ nguyên theo plan)

- Không auto-ép thu khi khách im lặng về tiền
- Không danh mục “hạng mục nhà” (tường/sàn…) có khấu hao riêng
- Không API sửa hoá đơn sau khi đã tạo (xử lý tay như hoá đơn sai thường)
- Không màn danh sách dispute riêng cho manager (theo dõi qua cờ `DISPUTED` trên ticket)

---

## 10. Gợi ý test nhanh (Postman / app)

| # | Case | Kỳ vọng |
|---|------|---------|
| 1 | Complete `costPaidBy=HOST` → tenant confirm không gửi `agreeToCharge` | CLOSED, không invoice |
| 2 | Complete `TENANT` + cost → confirm thiếu `agreeToCharge` | 4xx BusinessException |
| 3 | Confirm `agreeToCharge=true` | CLOSED, AGREED, có `issuedInvoice` (+ QR nếu PayOS OK) |
| 4 | Confirm `agreeToCharge=false` + lý do | CLOSED, DISPUTED, không charge |
| 5 | Complete lại khi đang WAITING (sửa số tiền) | Cập nhật được trước khi khách bấm |
| 6 | Auto-confirm ticket PENDING tiền | CLOSED, cost vẫn PENDING, không charge |
| 7 | `GET /equipment/{id}` | Có field `penaltyFee` |
