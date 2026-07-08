# Changelog BE — 2026-06-25

> **Đối tượng:** Team FE (Tien), BE, QA  
> **Phạm vi:** Sửa lỗi import cải tạo bổ sung · Sửa query inbound contract · Thêm API Cổng Host

---

## 1. Sửa lỗi import cải tạo bổ sung (`2 results`)

### Triệu chứng

`POST /api/v1/import/renovation-supplement-excel` (`dryRun=false`) trả **500**:

```
Query did not return a unique result: 2 results were returned
```

`dryRun=true` vẫn PASS.

### Nguyên nhân

`PropertyOnboardingServiceImpl.appendPurchasedEquipmentManifest()` gọi:

```java
equipmentManifestRepository.findByPropertyIdAndCatalogIdAndStatusAndSource(...)
```

Method trả `Optional<EquipmentManifest>` nhưng khóa `(propertyId, catalogId, status, source)` **không unique** — cùng catalog + `PURCHASED` có thể có nhiều dòng **khác `price`** (do import đợt 2 / import lặp). Spring ném lỗi **trước** khi `.filter(price)` chạy.

### Thay đổi

| File | Thay đổi |
|------|----------|
| `EquipmentManifestRepository.java` | `findByPropertyIdAndCatalogIdAndStatusAndSource` → trả `List<EquipmentManifest>` |
| `PropertyOnboardingServiceImpl.java` | Thêm `findPurchasedManifestByPrice()` — lọc theo giá trên list; dùng ở `appendPurchasedEquipmentManifest` và `assignEquipment` (PURCHASED) |
| `PropertyOnboardingServiceImpl.java` | `assignEquipment` (INITIAL_HANDOVER): `.stream().findFirst()` thay vì Optional trực tiếp |

### Cách test

1. Import đợt 2 căn có ≥ 2 manifest PURCHASED cùng catalog khác giá.
2. Import file cải tạo bổ sung `dryRun=false` → kỳ vọng **200**, manifest cùng giá được gộp `quantity`.

---

## 2. Sửa lỗi `findByPropertyId` trên Inbound Contract

### Triệu chứng

`getOnboardingSummary()` (và các API dùng `inboundContractRepository.findByPropertyId`) có thể **500** khi 1 property có ≥ 2 inbound contract (re-import).

### Thay đổi

| File | Thay đổi |
|------|----------|
| `InboundContractRepository.java` | Bỏ `findByPropertyId`, `findContractCodeByPropertyId` |
| `InboundContractRepository.java` | Thêm `findFirstByPropertyIdOrderByIdDesc` — lấy HĐ mới nhất |
| `InboundContractRepository.java` | Thêm `searchMasterLeases`, `findByStatus` (phục vụ Host Portal) |
| `PropertyOnboardingServiceImpl.java` | `getOnboardingSummary` dùng `findFirstByPropertyIdOrderByIdDesc` |
| `InboundContractServiceImpl.java` | 3 chỗ gọi contract theo property |
| `DepreciationServiceImpl.java` | `loadContract()` |
| `PropertyDeletionServiceImpl.java` | `purgeProperty()` lấy `contractCode` |

---

## 3. API Cổng Host (`/api/v1/host/**`) — MỚI

### Bối cảnh

FE cổng Host (`host.service.ts`) gọi `/api/v1/host/*` nhưng BE chưa có endpoint → badge thông báo poll liên tục → toast **Internal server error**.

### Quyền truy cập

- **JWT bắt buộc**
- Role: `ROLE_OWNER` hoặc `ROLE_ADMIN` (`@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")`)
- Lưu ý: BE dùng `OWNER`, không có `ROLE_HOST` — FE cần login tài khoản Owner

### File mới

| Loại | Đường dẫn |
|------|-----------|
| Controller | `controller/HostController.java` |
| Service | `service/HostPortalService.java`, `service/impl/HostPortalServiceImpl.java` |
| Entity | `entity/HostNotification.java`, `entity/HostExpense.java` |
| Enum | `enums/HostExpenseCategory.java` |
| Repository | `repository/HostNotificationRepository.java`, `repository/HostExpenseRepository.java` |
| DTO | `dto/host/*` (17 file) |

### Migration DB (tự chạy khi start app)

`DatabaseSchemaMigration.ensureHostPortalTables()`:

**`host_notifications`**

| Cột | Mô tả |
|-----|--------|
| `user_id` | UUID user nhận thông báo |
| `dedupe_key` | Khóa chống trùng (unique cùng `user_id`) |
| `type`, `title`, `message`, `priority` | Nội dung |
| `is_read` | Đã đọc |
| `created_at` | Thời điểm tạo |

**`host_expenses`**

| Cột | Mô tả |
|-----|--------|
| `property_id` | FK `properties` |
| `category` | `LEASE`, `MAINTENANCE`, `EQUIPMENT`, `MANAGEMENT`, `UTILITY`, `OTHER` |
| `amount`, `month` (YYYY-MM), `note`, `created_at` | Chi phí |

### Đồng bộ thông báo (khi `GET /notifications`)

Tự tạo notification nếu chưa có (theo `dedupe_key`):

| Loại | Nguồn |
|------|--------|
| `PROPERTY_REVIEW` | Property `PENDING_HOST_REVIEW` |
| `CONTRACT_PENDING` | Tenant contract `PENDING` |
| `MASTER_LEASE_EXPIRING` | Inbound contract ACTIVE, hết hạn trong 60 ngày |

Badge unread: `GET /notifications?unreadOnly=true&page=0&size=1` → dùng `totalElements`.

---

## 4. Danh sách endpoint Host Portal

> Base: `/api/v1/host`  
> Phân trang: Spring `Page<T>` → `{ content, totalElements, totalPages, number, size }`

### 4.1 Notifications

| Method | Path | Query / Body | Response |
|--------|------|--------------|----------|
| GET | `/notifications` | `unreadOnly?`, `page?`, `size?` | `Page<HostNotificationDto>` |
| PUT | `/notifications/{id}/read` | — | 200 |
| PUT | `/notifications/read-all` | — | 200 |

`HostNotificationDto`: `id`, `type`, `title`, `message`, `isRead`, `priority?`, `createdAt`

### 4.2 Dashboard

| Method | Path | Query | Response |
|--------|------|-------|----------|
| GET | `/dashboard/summary` | `month` (YYYY-MM) **bắt buộc** | `HostDashboardSummaryResponse` |

### 4.3 Finance

| Method | Path | Query | Response |
|--------|------|-------|----------|
| GET | `/finance/cashflow` | `from`, `to` (YYYY-MM) | `{ series: CashflowPoint[] }` |
| GET | `/finance/expense-breakdown` | `month` | `{ month, breakdown[] }` |
| GET | `/finance/property-pnl` | `month` | `HostPropertyPnlResponse` |
| GET | `/finance/receivables-aging` | — | `HostReceivablesAgingResponse` |
| GET | `/finance/deposits` | `status?` (`HELD`/`REFUNDED`/`FORFEITED`) | `HostDepositsResponse` |
| GET | `/invoices` | `month?`, `status?`, `page?`, `size?` | `Page<HostInvoiceDto>` |

### 4.4 Expenses (CRUD)

| Method | Path | Ghi chú |
|--------|------|---------|
| GET | `/expenses` | `propertyId?`, `category?`, `month?`, `page?`, `size?` |
| POST | `/expenses` | Body `HostExpenseUpsertRequest` |
| PUT | `/expenses/{id}` | Body JSON một phần |
| DELETE | `/expenses/{id}` | 204 |

`category` gửi UPPER: `LEASE`, `MAINTENANCE`, `EQUIPMENT`, `MANAGEMENT`, `UTILITY`, `OTHER`

### 4.5 Reports

| Method | Path | Query |
|--------|------|-------|
| GET | `/reports/financial-summary` | `from`, `to` |
| GET | `/reports/manager-performance` | `month` |
| GET | `/reports/property-performance` | `month` |

### 4.6 Contracts (Host duyệt HĐ tenant)

| Method | Path | Body |
|--------|------|------|
| GET | `/contracts` | `status?`, `page?`, `size?` |
| PUT | `/contracts/{id}/approve` | — → `PENDING` → `ACTIVE` |
| PUT | `/contracts/{id}/reject` | `{ "reason": "..." }` → `TERMINATED` |

### 4.7 Master Leases (map `inbound_contracts`)

| Method | Path | Ghi chú |
|--------|------|---------|
| GET | `/master-leases` | `status?`, `propertyId?` |
| GET | `/master-leases/{id}` | Chi tiết |
| POST | `/master-leases` | Body `MasterLeaseUpsertRequest` — 1 property chỉ 1 HĐ |
| PUT | `/master-leases/{id}` | Body một phần |
| POST | `/master-leases/{id}/terminate` | `status` → `TERMINATED` |

Status master lease (response): `ACTIVE`, `EXPIRING` (≤60 ngày), `EXPIRED`, `TERMINATED`

---

## 5. Logic tính toán tài chính (tạm thời)

Chưa có module billing/hóa đơn riêng. Số liệu suy từ dữ liệu hiện có:

| Chỉ số | Nguồn |
|--------|--------|
| Doanh thu tháng | Tổng `rentAmount` HĐ tenant `ACTIVE` còn hiệu lực trong tháng |
| Chi phí thuê gốc | `InboundContract.totalRentAmount` phân bổ đều theo số tháng HĐ |
| Chi phí khác | `host_expenses` theo `month` |
| Hóa đơn / công nợ | Sinh từ HĐ tenant ACTIVE; `PAID`/`UNPAID`/`OVERDUE` theo `dueDate` và `paymentStatus` |
| Tiền cọc | `deposit` trên HĐ tenant ACTIVE → `HELD` |

Khi có module thu tiền hàng tháng thật, cần thay logic invoices/receivables.

---

## 6. Repository / service khác cập nhật

| File | Thay đổi |
|------|----------|
| `TenantContractRepository.java` | `findByStatus`, `Page findByStatus` |
| `PropertyRepository.java` | `findByStatus(PropertyStatus)` |

---

## 7. Checklist QA

- [ ] Restart BE (migration tạo `host_notifications`, `host_expenses`)
- [ ] Login `ROLE_OWNER` → `GET /api/v1/host/notifications?unreadOnly=true&page=0&size=1` → **200**, không toast 500
- [ ] Có căn `PENDING_HOST_REVIEW` → xuất hiện notification `PROPERTY_REVIEW`
- [ ] Import cải tạo bổ sung căn có manifest PURCHASED trùng catalog khác giá → **200**
- [ ] `GET /api/v1/properties/{id}/onboarding-summary` với property có 2 inbound contract → **200** (lấy HĐ mới nhất)
- [ ] `GET /api/v1/host/dashboard/summary?month=2026-06` → summary hợp lệ
- [ ] CRUD `/api/v1/host/expenses` hoạt động

---

## 8. Việc chưa làm / hạn chế

1. **ROLE_HOST** — FE doc ghi HOST; BE chỉ có `ROLE_OWNER`. Cần thống nhất FE hoặc thêm alias role.
2. **Billing thật** — invoices, receivables, revenue theo tháng là dữ liệu suy luận, chưa gắn PayOS/thu tiền hàng tháng.
3. **Master lease** — `deposit`, `paymentDay`, `ownerPhone`, `escalationPct` chưa lưu DB; response dùng default / null.
4. **Maintenance resolved** — `manager-performance.resolvedMaintenance` luôn `0` (chưa có bảng ticket bảo trì).
