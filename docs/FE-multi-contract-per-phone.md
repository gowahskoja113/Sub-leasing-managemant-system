# FE Handoff — 1 account / nhiều hợp đồng thuê

> Ngày: 2026-07-27  
> BE đã hỗ trợ: 1 SĐT = 1 account tenant, gắn **nhiều** HĐ `ACTIVE` ở nhà/phòng khác nhau.

---

## 1. Quy tắc nghiệp vụ

| Rule | Chi tiết |
|------|----------|
| 1 SĐT → 1 account | Unique phone trên `User`; tạo HĐ thứ 2+ **tái dùng** cùng tenant |
| Nhiều HĐ ACTIVE | Được phép nếu khác phòng / khác nhà |
| Không được | 2 HĐ ACTIVE cùng 1 phòng, hoặc 2 nguyên căn ACTIVE cùng 1 property |

Manager tạo/import HĐ với SĐT đã có → BE gắn vào account cũ, **không** tạo user mới, **không** báo lỗi “đã thuê”.

---

## 2. API tenant cần truyền `contractId` khi có nhiều nhà

### 2.1 Dashboard

```http
GET /api/v1/tenant/me/dashboard
GET /api/v1/tenant/me/dashboard?contractId={id}
```

**Response bổ sung:**

| Field | Ý nghĩa |
|-------|---------|
| `contracts[]` | Tất cả HĐ `ACTIVE` (để hiện picker) |
| `contract` / `room` / `building` | HĐ đang chọn (primary) |

Mỗi phần tử `contracts[]`:

```json
{
  "id": 101,
  "code": "HD-MT-2026-00001",
  "startDate": "2026-07-01",
  "endDate": "2027-07-01",
  "daysLeft": 340,
  "status": "ACTIVE",
  "propertyId": 12,
  "propertyName": "Nhà Lê Lợi 01",
  "roomId": 5,
  "roomNumber": "P01"
}
```

**Hành vi primary:**

- Có `contractId` → primary = HĐ đó (phải thuộc tenant + ACTIVE).
- Không có `contractId` → primary = HĐ ACTIVE mới nhất (`startDate`/`id` desc) — FE cũ vẫn chạy.
- Không có HĐ ACTIVE → `contracts: []`, các field primary `null`.

### 2.2 Handover

```http
GET  /api/v1/tenant/me/handover?contractId={id}
POST /api/v1/tenant/me/handover/acknowledge?contractId={id}
```

| Số HĐ ACTIVE | `contractId` | Kết quả |
|--------------|--------------|---------|
| 1 | optional | Dùng HĐ đó |
| ≥ 2 | **bắt buộc** | Thiếu → `BusinessException`: *“Bạn đang thuê nhiều nhà. Vui lòng chọn hợp đồng (truyền contractId)”* |
| 0 | — | `404` không có HĐ hiệu lực |

### 2.3 Thiết bị của tôi

```http
GET /api/v1/tenant/me/equipments?contractId={id}
```

Cùng rule resolve như handover. Trả thiết bị theo property/room của HĐ đã chọn.

### 2.4 Bảo trì

`POST /api/v1/maintenance` — tenant chỉ tạo được cho `roomId` thuộc HĐ ACTIVE của mình (kể cả nguyên căn: mọi phòng trong property). Báo phòng nhà khác → lỗi business.

Checkout / hóa đơn vốn đã theo `contractId` hoặc list nhiều HĐ — không đổi.

---

## 3. Gợi ý UI

```
App mở → GET dashboard
        ↓
contracts.length <= 1  → dùng luôn primary (như cũ)
contracts.length > 1   → hiện picker “Nhà đang thuê”
        ↓
Lưu selectedContractId (memory / AsyncStorage)
        ↓
Mọi màn (dashboard refresh, handover, equipments, checkout…)
truyền ?contractId=selectedContractId
```

**Picker label gợi ý:** `{propertyName}` + (roomNumber nếu có) — vd. `Nhà Lê Lợi 01 · P01`.

Khi user đổi nhà trong picker → gọi lại dashboard `?contractId=` + refresh các tab phụ thuộc.

---

## 4. Checklist FE

- [ ] Đọc `contracts[]` từ dashboard; hiện picker nếu `length > 1`
- [ ] Persist `selectedContractId` trong session app
- [ ] Truyền `contractId` cho handover / acknowledge / equipments khi đã chọn
- [ ] Xử lý message “Bạn đang thuê nhiều nhà…” → mở picker
- [ ] List HĐ (`GET /me/tenant-contracts` nếu đang dùng) vẫn OK cho màn xem file HĐ
- [ ] Không giả định “chỉ có 1 roomId” khi tạo bảo trì — lấy `roomId` từ HĐ đang chọn

---

## 5. Tham chiếu BE

- Lookup phone dual-format: `TenantOnboardingServiceImpl.getOrCreateTenant`
- Resolve helper: `TenantActiveContractResolver`
- Dashboard / handover / equipments controllers dưới `/api/v1/tenant/me/...`
