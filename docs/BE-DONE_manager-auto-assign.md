# BE DONE — Manager auto-assign (đối chiếu handoff FE)

Nguồn yêu cầu FE: `BE_HANDOFF_manager-auto-assign.md`  
Ngày làm: 2026-07-21  
Repo: `slms2026` (package `com.sep490.slms2026`)

## Tóm tắt quyết định sản phẩm (đã implement)

- Quản lý phụ trách nhà = `Property.operationManagerId` (gán lúc kích hoạt nhà).
- Người đó là **người duy nhất** đón khách + phụ trách vòng đời tenant (HĐ, notify…).
- **Không** còn khái niệm “quản lý đón khách” riêng trên từng hợp đồng.
- FE **không** gửi / chọn / đổi `assignedManagerId` khi tạo / sửa / import HĐ.

---

## Checklist đối chiếu từng mục FE

| # | Yêu cầu FE | BE đã làm? | Ghi chú |
|---|------------|:----------:|---------|
| 1 | Auto-set `assignedManager` từ `property.operationManagerId` khi onboard | ✅ | Không còn đọc từ client |
| 1 | Update draft không cho sửa `assignedManagerId` tay | ✅ | Field đã xoá khỏi DTO |
| 1 | Import Excel không đọc cột “SĐT quản lý đón khách” | ✅ | Xoá `resolveManagerId` + field row |
| 1 | Xoá / bỏ field `assignedManagerId` khỏi request DTO | ✅ | Xoá hẳn; FE cũ gửi field lạ → Jackson ignore |
| 1 | Deprecate / bỏ `PATCH /{id}/assign-manager` | ✅ | **Xoá hẳn** (không giữ ADMIN-only) |
| 2 | Chặn tạo HĐ nếu nhà chưa có `operationManagerId` | ✅ | Guard trong `onboardTenant` (import cũng đi qua đây) |
| 3 | Cascade `assignedManager` khi đổi quản lý nhà | ✅ | DRAFT / PENDING / ACTIVE + notify |
| 4 | Bỏ `OR c.assignedManager` trong query managed contracts | ✅ | Chỉ còn `p.managedBy` |
| 5 | Bỏ cột Excel + cập nhật doc | ✅ | Template + 3 file doc FE |

---

## Chi tiết theo mục

### 1. Auto-assign — không cho client chọn manager

**Đã làm:**

- `TenantOnboardingServiceImpl.onboardTenant()`
  - Bỏ `request.getAssignedManagerId()`
  - Luôn: `assignedManager = userRepository.findById(property.getOperationManagerId())`
- `TenantOnboardingServiceImpl.updateDraftContract()`
  - Không còn set / đổi `assignedManager` từ request
- `BulkTenantDraftContractImportServiceImpl`
  - Xoá `resolveManagerId(...)`
  - Không set manager từ Excel; để `onboardTenant` tự gán
- DTO
  - Xoá `assignedManagerId` khỏi `OnboardTenantRequest`
  - Xoá `assignedManagerId` khỏi `UpdateDraftContractRequest`
- Endpoint
  - Xoá `PATCH /api/v1/tenant-contracts/{id}/assign-manager`
  - Xoá `AssignManagerRequest`
  - Xoá method `assignManager` khỏi service interface/impl

**Lệch nhẹ so với handoff (cố ý):**

- Handoff cho phép “giữ endpoint chỉ ADMIN để fix data”.
- BE chọn **xoá hẳn**: nếu cần sửa → đổi quản lý **cấp nhà** (`changeOperationManager` / `assignOperationManager`), cascade tự cập nhật HĐ. Tránh tái tạo manager per-contract.

**Ảnh hưởng FE:**

- Ngừng gửi `assignedManagerId` khi create/update/import.
- Ngừng gọi `PATCH .../assign-manager`.
- Response vẫn có `assignedManagerId` / `assignedManagerName` (read-only, mirror Operation Manager nhà).

---

### 2. Chặn tạo HĐ nếu nhà chưa có quản lý

**Đã làm:**

Trong `onboardTenant()` (đầu flow, trước khi tạo HĐ):

```text
Nếu property.operationManagerId == null
→ BusinessException:
  "Nhà chưa có quản lý phụ trách, vui lòng gán quản lý cho nhà trước khi tạo hợp đồng"
```

Import Excel cũng đi qua `onboardTenant` → **cùng rule**.

**Bối cảnh validate hiện có:**

- Import đã check nhà phải `ACTIVE`.
- Nhà chỉ lên `ACTIVE` sau khi gán Operation Manager → với luồng import bình thường, `operationManagerId` luôn có.
- Guard `operationManagerId == null` chủ yếu chặn API onboard trực tiếp lên nhà chưa gán quản lý.

`updateDraftContract` không đổi property → không cần validate lại manager.

**Rủi ro dữ liệu (FE nhắc):**

- Nên audit DB: `Property` `ACTIVE`/`RENTED` mà `operationManagerId IS NULL` (hiếm vì flow activation bắt buộc gán).
- Nếu có → backfill trước khi release production.

---

### 3. Cascade khi đổi quản lý nhà

**Đã làm:**

- Method mới: `TenantOnboardingService.reassignManagerForProperty(propertyId, newManagerId)`
  - Lấy HĐ status ∈ `DRAFT`, `PENDING`, `ACTIVE`
  - Set `assignedManager` = manager mới
  - Gọi `notifyAssignedManager(...)` cho từng HĐ đổi
- Gọi cascade từ:
  - `PropertyOnboardingServiceImpl.changeOperationManager(...)`
  - `PropertyOnboardingServiceImpl.assignOperationManager(...)` (nhánh nhà đã `ACTIVE`/`RENTED` và đổi sang manager khác)

**Ticket / maintenance:**

- Không cần cascade riêng — list ticket đã filter theo `property.managedBy` / `operationManagerId`.
- Đổi quản lý nhà → ticket tự theo quản lý mới.

---

### 4. Dọn code phụ thuộc field cũ

**Đã làm:**

- `TenantContractRepository.findManagedContractsByApprovalStatuses`:
  - Trước: `(p.managedBy = :id OR c.assignedManager.id = :id)`
  - Sau: `p.managedBy = :id`

**Chưa đổi (không ảnh hưởng nghiệp vụ):**

- `notifyAssignedManager()` vẫn đọc `contract.getAssignedManager()`.
- Sau auto-assign + cascade, giá trị này **luôn =** Operation Manager nhà → đúng hành vi.
- Có thể đổi nguồn sang `property.operationManagerId` sau (cosmetic).

---

### 5. Excel template + docs

**Đã làm:**

- Script: `scripts/generate-tenant-draft-import-excel.mjs` — bỏ cột `SĐT quản lý đón khách`
- File mẫu đã regenerate: `docs/SLMS2026_import_tenant_draft_contracts.xlsx`
- Docs cập nhật:
  - `docs/FE-bulk-tenant-draft-contract-import.md`
  - `docs/FE-import-tenant-draft-contracts.md`
  - `docs/FE-tenant-onboarding-otp-flow.md`

**FE cần sync:**

- Copy file mẫu mới sang `frontend-web/public/templates/` (nếu FE đang serve template từ đó).
- Xoá UI chọn manager khi create/update draft / import.

---

## File BE đã đụng

| File | Thay đổi |
|------|----------|
| `TenantOnboardingServiceImpl.java` | Auto-assign, guard null, cascade method, xoá assignManager |
| `TenantOnboardingService.java` | Interface: xoá assignManager, thêm reassignManagerForProperty |
| `TenantContractActionController.java` | Xoá endpoint assign-manager |
| `AssignManagerRequest.java` | **Deleted** |
| `OnboardTenantRequest.java` | Xoá field assignedManagerId |
| `UpdateDraftContractRequest.java` | Xoá field assignedManagerId |
| `BulkTenantDraftContractImportServiceImpl.java` | Bỏ resolveManagerId / manager từ Excel |
| `TenantDraftContractImportRow.java` | Xoá assignedManagerRaw |
| `ExcelTenantDraftContractWorkbookReader.java` | Không đọc cột manager |
| `TenantContractRepository.java` | Bỏ OR assignedManager; thêm findByPropertyIdAndStatusIn |
| `PropertyOnboardingServiceImpl.java` | Cascade khi đổi/gán Operation Manager |
| `scripts/generate-tenant-draft-import-excel.mjs` | Bỏ cột manager |
| `docs/SLMS2026_import_tenant_draft_contracts.xlsx` | Regenerate |
| Docs FE import/onboard (3 file) | Cập nhật mô tả |

---

## Không đổi (đúng handoff)

- Invoice / PaymentClaim / PendingCharge / Maintenance filter / Renewal / Termination — vẫn derive từ `property.operationManagerId` / `managedBy`.
- `MaintenanceRequest.assignedManager` — dead code, ngoài scope.
- Mobile app — không có UI chọn manager cho tenant/ticket.

---

## Việc FE cần xác nhận / làm

1. Ngừng gửi `assignedManagerId` khi create / update draft.
2. Xoá / ẩn UI chọn “quản lý đón khách”.
3. Ngừng gọi `PATCH /tenant-contracts/{id}/assign-manager` (endpoint đã 404).
4. Sync file Excel mẫu mới từ BE (`docs/SLMS2026_import_tenant_draft_contracts.xlsx`) sang FE public templates.
5. Khi đổi quản lý nhà, kỳ vọng HĐ DRAFT/PENDING/ACTIVE cập nhật manager mới + manager mới nhận notify.
6. Response HĐ vẫn có `assignedManagerId` / `assignedManagerName` để hiển thị read-only.

---

## Smoke test gợi ý

- [ ] Import Excel nhà ACTIVE → HĐ DRAFT có `assignedManagerId` = Operation Manager nhà.
- [ ] Import Excel **không** cần cột “SĐT quản lý đón khách”.
- [ ] Create draft API **không** gửi manager → vẫn gán đúng Operation Manager nhà.
- [ ] Create draft nhà chưa có `operationManagerId` → 4xx message rõ.
- [ ] Update draft **không** đổi được manager qua body.
- [ ] `PATCH .../assign-manager` → **404** (không còn 500).
- [ ] Đổi Operation Manager nhà → HĐ DRAFT/PENDING/ACTIVE đổi theo; ticket list theo manager mới.
- [ ] Restart BE → backfill HĐ cũ `assignedManager=null` (vd `HD-MT-2026-00023`).
- [ ] `GET /properties` và `GET /properties/{id}` có `operationManagerName`.
- [ ] Compile BE: `mvnw compile` đã pass lúc implement.

---

## Follow-up (21/07/2026) — sau verify FE

| # | Yêu cầu FE follow-up | BE đã làm? | Chi tiết |
|---|----------------------|:----------:|----------|
| 1 | Backfill HĐ cũ `assignedManager = null` | ✅ | `AssignedManagerBackfillRunner` + `backfillMissingAssignedManagers()` — chạy mỗi startup, idempotent, có notify |
| 2 | `operationManagerName` trên Property API | ✅ | Thêm vào `PropertyResponse` + `PropertyActivationResponse`; map ở `PropertyServiceImpl` / `PropertyOnboardingServiceImpl` |
| 3 | Route đã xoá trả 404 thay vì 500 | ✅ | Handler `NoResourceFoundException` / `NoHandlerFoundException` + `spring.mvc.throw-exception-if-no-handler-found=true` |

**Verify lại sau restart BE:**

1. `GET /tenant-contracts/{id}` của `HD-MT-2026-00023` → có `assignedManagerId/Name`.
2. `GET /properties/23` → có `operationManagerName`.
3. `PATCH /tenant-contracts/1/assign-manager` → `404 Not Found`.
