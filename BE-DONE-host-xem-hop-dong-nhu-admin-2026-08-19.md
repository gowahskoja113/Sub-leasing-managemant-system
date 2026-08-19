# BE-DONE — Host xem hợp đồng đầy đủ như Admin (19/08/2026)

Đối chiếu file `BE-NEED-host-xem-hop-dong-nhu-admin-2026-08-19.md`.

**Kết luận:** đã làm hết phần A, B, B2, C trên BE. Chi tiết dưới đây.

---

## A — Mở chi tiết / file hợp đồng cho Host (`ROLE_OWNER`)

| Việc | Trạng thái |
|---|---|
| `GET /api/v1/tenant-contracts/{id}` cho OWNER | Đã làm |
| `GET /api/v1/tenant-contracts/{id}/document` cho OWNER | Đã làm |
| `GET /api/v1/tenant-contracts/{id}/document/download` cho OWNER | Đã làm |
| `GET /api/v1/tenant-contracts` (list) cho OWNER — cùng nguồn với admin | Đã làm |
| `getHighestRole()` nhận diện OWNER (không rơi xuống TENANT) | Đã làm |
| `assertCanView()` nhánh OWNER | Đã làm |

### File đụng

- `TenantContractActionController`
- `TenantContractDocumentServiceImpl`

Host xem chi tiết HĐ không còn 403. Response chi tiết **cùng shape** `TenantContractResponse` với admin (biên bản bàn giao, người ở cùng, CCCD, PDF, …).

OWNER list HĐ gọi `getContractsByStatus(status)` như admin, **không** đi nhánh manager.

### FE cần biết (A)

- **Bước 1–3:** không cần sửa. Drawer gọi sẵn `GET /tenant-contracts/{id}` — lần mở kế tiếp hiện đủ khối, `blockedNote` biến mất.
- **Bước 4 (tuỳ chọn):** `pages/host/contracts/ContractList.tsx` có thể đổi `hostService.listContracts()` → `tenantService.listByStatus()`, bỏ `toDetailSeed()`. Host và admin dùng chung một kiểu dữ liệu. Endpoint host cũ vẫn chạy.

---

## B — Tên khách trên hợp đồng chưa kích hoạt

| Việc | Trạng thái |
|---|---|
| Fallback `draftTenantName` / `draftTenantPhone` / `draftTenantCccd` trong `toContractDto()` | Đã làm |

HĐ `DRAFT`/`PENDING` chưa có `tenant` thì Host thấy tên/SĐT/CCCD đã nhập lúc onboard, khớp admin.

Nếu đã làm A4 (list admin), trang `/host/contracts` dùng `TenantContractResponse` thì fallback này chỉ còn phục vụ API host cũ (`/host/contracts`, `/host/tenants`).

---

## B2 — `lessorName` không còn gán nhầm tên toà

| Việc | Trạng thái |
|---|---|
| Không map `propertyName` vào `lessorName` | Đã làm — trả `null` |

Entity không có tên bên cho thuê thật. Trả `null` thay vì nhồi tên nhà. FE vốn đã ẩn field này.

---

## C — Master lease: bỏ số bịa, trả field thật

Entity `InboundContract` **không có** `owner_phone`, `deposit`, `payment_day`, `escalation_pct`. Không thêm cột (chưa có luồng nhập thật).

| Việc | Trạng thái |
|---|---|
| C1 — bỏ 4 field hard-code khỏi `MasterLeaseDto` | Đã làm |
| C2 — trả `contractCode`, `contractScanUrl` | Đã làm |
| C3 — trả `totalRentAmount` (kèm `monthlyRent`) | Đã làm |
| Create/update nhận `contractScanUrl` | Đã làm |

`MasterLeaseDto` sau sửa:

```
id, propertyId, ownerName, contractCode, contractScanUrl,
monthlyRent, totalRentAmount, startDate, endDate, status
```

**Không còn** `ownerPhone`, `deposit`, `paymentDay`, `escalationPct` trên response. Request create vẫn nhận 4 field đó (không 400) nhưng **không lưu** — tránh FE tin là “cọc 0”, “trả ngày 1”.

### FE cần biết (C)

- Tab master lease: bỏ UI “Cọc 0 đ / Trả ngày 1 / Không tăng giá / SĐT trống” nếu đang đọc 4 field cũ (sẽ `undefined`).
- Hiện `contractCode`, nút xem/tải `contractScanUrl` khi có URL.
- Tổng tiền cả kỳ dùng `totalRentAmount`, **không** nhân `monthlyRent × số tháng`.
- Tạo/sửa master lease: gửi `contractScanUrl` nếu có file scan.

---

## Tóm tắt cho hôm nay

| # | Việc | Kết quả |
|---|---|---|
| A1–A4 | Host xem / list / tải HĐ như admin | Xong |
| B | Tên khách HĐ nháp | Xong |
| B2 | `lessorName` | Xong (`null`) |
| C1–C3 | Master lease dữ liệu thật | Xong (bỏ field bịa, expose field entity) |

**Chưa làm (nằm ngoài ticket / để sau):** nhiều host thì lọc sở hữu **cả** `findHostContracts` và `assertCanView`; lưu thật 4 cột cọc/SĐT/ngày trả/tăng giá khi nghiệp vụ cần.

**Cách kiểm tra nhanh**

1. Login `ROLE_OWNER` → `GET /api/v1/tenant-contracts/{id}` → 200, đủ field chi tiết.
2. `GET /api/v1/tenant-contracts/{id}/document/download` → PDF, không 403.
3. `GET /api/v1/tenant-contracts` → cùng list với admin.
4. HĐ DRAFT chưa có tài khoản khách → `lesseeName` có tên draft.
5. `GET /api/v1/host/master-leases` → có `contractCode` / `totalRentAmount`; không có `deposit: 0` / `paymentDay: 1`.
