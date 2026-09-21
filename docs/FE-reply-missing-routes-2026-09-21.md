# BE trả lời FE — 3 route thiếu (đối chiếu HEAD `f69b6ea`)

> **Ngày:** 21/09/2026  
> **Kết luận:** 3 route **đã có** trên BE working tree (sẽ có sau khi BE merge/deploy bản mới). FE đối chiếu `f69b6ea` nên chưa thấy.

---

## 1. Equipment catalog — sửa / xoá

**Base:** `/api/v1/equipment-catalog`  
**Controller:** `PropertyOnboardingController`

| Method | Path | Role | Ghi chú |
|--------|------|------|---------|
| `GET` | `/api/v1/equipment-catalog` | (auth) | List catalog `active=true` |
| `POST` | `/api/v1/equipment-catalog` | (auth) | Tạo mới. Body: `{ "name", "description?" }` |
| `PUT` | `/api/v1/equipment-catalog/{id}` | **ADMIN** | Sửa tên/mô tả. Body giống create |
| `DELETE` | `/api/v1/equipment-catalog/{id}` | **ADMIN** | Soft-delete (`active=false`), **204 No Content** |

```http
PUT /api/v1/equipment-catalog/12
Content-Type: application/json

{ "name": "Máy lạnh 1HP", "description": "Daikin" }
```

```http
DELETE /api/v1/equipment-catalog/12
→ 204
```

Trùng tên (ignore case) → `409 Conflict`.

---

## 2. Gán manager đón khách trên HĐ

**Không bị xoá** — path đúng là:

```http
PATCH /api/v1/tenant-contracts/{id}/assign-manager
Content-Type: application/json

{ "managerId": "<uuid ROLE_MANAGER>" }
```

| | |
|--|--|
| Role | `MANAGER`, `ADMIN` |
| Response | `TenantContractResponse` |
| Side effect | Gán `assignedManager` + gửi thông báo cho manager (nếu đổi người) |

Lỗi thường gặp: user không phải `ROLE_MANAGER` → `400`; HĐ `TERMINATED` → `400`.

---

## 3. Import khu vực Excel

**Còn hỗ trợ:**

```http
POST /api/v1/import/zones-excel
Content-Type: multipart/form-data
Role: ADMIN

file=<xlsx>
dryRun=false   # optional, default false
```

| | |
|--|--|
| Sheet | `Zones` hoặc sheet đầu tiên |
| Cột bắt buộc | `Tỉnh/Thành phố` |
| Cột tuỳ chọn | `Quận/Huyện`, `Mô tả` |
| Hành vi | Idempotent — đã tồn tại → `SKIPPED` |
| Response | `BulkImportResponse` (`contractsProcessed` = số dòng import, `contractsSkipped`, `results`, `errors`) |

---

## Checklist FE

- [ ] Dùng đúng 4 method catalog (PUT/DELETE chỉ ADMIN)
- [ ] `PATCH /api/v1/tenant-contracts/{id}/assign-manager` với `{ managerId }`
- [ ] `POST /api/v1/import/zones-excel` (multipart `file` + optional `dryRun`)
- [ ] Đợi BE deploy bản có 3 route này (sau `f69b6ea`) rồi bỏ mock / bỏ 404 workaround

---

*BE đã implement sẵn trong working tree; FE chỉ cần đổi URL theo contract trên sau khi BE push/deploy.*
