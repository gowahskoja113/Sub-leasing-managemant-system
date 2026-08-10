# BE HANDOFF — Meter override passcode (OTP do admin gen)

**Ngày:** 10/08/2026  
**Người gửi:** team BE  
**Người nhận:** team FE (mobile + web admin)  
**Phạm vi:** thay cơ chế mã **cố định env** bằng **OTP admin gen → dùng 1 lần → chết**  
**API base:** `https://slms-api.duckdns.org` (prod) / local `http://localhost:8080`

---

## 1. Tóm tắt một câu

Manager **không còn** nhớ/dùng mã demo cố định. Mỗi lần cần nhập chỉ số tay: **admin gen mã 6 số (TTL)** → manager nhập **1 lần** → mã chết → app nhận `overrideToken` như cũ để submit.

Luồng đón khách mới: **`contractId` vẫn được gửi `null`** lúc verify (HĐ chưa tạo).

---

## 2. Vì sao đổi

| Cũ | Mới |
|----|-----|
| `MANAGER_OVERRIDE_PASSCODE` cố định trong env | Admin bấm gen mỗi lần cần |
| Mọi manager biết 1 mã → dễ lạm dụng | Manager không giữ chìa vĩnh viễn |
| Demo: `slms-demo-override` | Mỗi case một mã OTP |

---

## 3. Qui trình nghiệp vụ (FE bám theo)

```text
Manager đang đón khách → bước chỉ số → không chụp được ảnh
        │
        ▼
Gọi / báo admin: "em cần mã nhập tay"
        │
        ▼
Admin (web/app admin) → bấm "Tạo mã" → thấy mã 6 số + hạn dùng
        │
        ▼
Admin đọc/gửi mã cho manager (chat / gọi điện)
        │
        ▼
Manager nhập mã → verify → nhận overrideToken
        │
        ▼
Submit onboard / update chỉ số kèm:
  overrideToken + reason + chỉ số (không có ảnh meter)
        │
        ▼
Xong. Lần sau cần lại → admin gen mã mới (lặp)
```

---

## 4. API — Admin

### 4.1 Gen mã OTP

```http
POST /api/v1/admin/meter-override/passcodes
Authorization: Bearer <ADMIN_JWT>
Content-Type: application/json
```

Body (optional):

```json
{
  "ttlMinutes": 10,
  "note": "Manager An — đón khách P.302"
}
```

| Field | Bắt buộc | Ghi chú |
|-------|----------|---------|
| `ttlMinutes` | Không | 1–60; mặc định server **10** (`MANAGER_OVERRIDE_PASSCODE_TTL_MINUTES`) |
| `note` | Không | Ghi chú nội bộ admin |

**200**

```json
{
  "id": 1,
  "code": "482910",
  "createdBy": "uuid-admin",
  "note": "Manager An — đón khách P.302",
  "expiresAt": "2026-08-10T09:00:00",
  "usedAt": null,
  "usedBy": null,
  "createdAt": "2026-08-10T08:50:00",
  "usable": true,
  "message": "Gửi mã này cho manager. Mã dùng 1 lần, hết hạn sau 10 phút."
}
```

**UI gợi ý admin**

- Nút **"Tạo mã nhập tay đồng hồ"** (to, rõ).
- Sau gen: hiện **`code` cỡ lớn** (copy / đọc được), countdown tới `expiresAt`.
- Optional: ô note trước khi gen.
- **Không** hardcode mã cũ (`slms-demo-override`).

### 4.2 Xem danh sách mã

```http
GET /api/v1/admin/meter-override/passcodes?activeOnly=true
Authorization: Bearer <ADMIN_JWT>
```

| Query | Ý nghĩa |
|-------|---------|
| `activeOnly=true` | Chỉ mã **chưa dùng + chưa hết hạn** |
| `activeOnly=false` (default) | Tất cả (audit) |

Response: mảng object giống gen (`usable`, `message` = `"Còn hiệu lực"` / `"Đã dùng"` / `"Hết hạn"`).

### 4.3 Log nhập tay (sau khi manager submit chỉ số)

```http
GET /api/v1/admin/meter-overrides
Authorization: Bearer <ADMIN_JWT>
```

(Unchanged) — ai / hợp đồng / loại đồng hồ / số / lý do / thời điểm.

---

## 5. API — Manager (verify — gần như giữ nguyên)

```http
POST /api/v1/manager/meter-override/verify
Authorization: Bearer <MANAGER_JWT>
Content-Type: application/json
```

```json
{
  "passcode": "482910",
  "contractId": null,
  "meterKind": "ELEC"
}
```

| Field | Bắt buộc | Ghi chú |
|-------|----------|---------|
| `passcode` | Có | **Mã 6 số admin vừa gen** (không phải mã env) |
| `contractId` | Không | **`null` khi đón khách mới** (HĐ chưa tạo). Có HĐ rồi thì gửi id |
| `meterKind` | Có | `ELEC` \| `WATER` (nhận thêm `ELECTRIC` / `ELECTRICITY` → `ELEC`) |

**200**

```json
{
  "valid": true,
  "overrideToken": "17f48c11-aaaa-bbbb-cccc-dddddddddddd",
  "expiresAt": "2026-08-10T09:05:00",
  "message": "OK"
}
```

| Status | Body gợi ý | FE xử lý |
|--------|------------|----------|
| **200** | `valid: true` + `overrideToken` | Bật nhập tay; lưu token theo loại ELEC/WATER |
| **403** | `{ "valid": false, "message": "Mã không đúng hoặc đã hết hạn…" }` | Toast; bảo xin admin **mã mới** |
| **429** | Sai ≥5 lần → khoá ~5 phút | Báo chờ |
| **400** | validation | Kiểm tra `meterKind` / `passcode` blank |

**Sau verify thành công:** mã OTP **chết ngay** (dùng lại mã đó → 403).  
Muốn ELEC + WATER → cần **2 lần verify** (2 mã OTP, hoặc 2 lần gen — mỗi lần một `meterKind`).

---

## 6. Gửi kèm khi onboard / update draft (không đổi contract FE)

Khi **không có ảnh** đồng hồ tương ứng:

```json
{
  "initialElectricReading": 3082,
  "electricMeterImageUrl": null,
  "electricMeterOverrideToken": "<overrideToken từ verify ELEC>",
  "electricMeterOverrideReason": "Camera hỏng — không chụp được",

  "initialWaterReading": 12.5,
  "waterMeterImageUrl": null,
  "waterMeterOverrideToken": "<overrideToken từ verify WATER>",
  "waterMeterOverrideReason": "Đồng hồ bị che"
}
```

| Rule BE | FE |
|---------|-----|
| Token **chỉ consume** khi **không có** ảnh meter tương ứng | Có ảnh → không cần gửi token |
| `reason` **bắt buộc** khi dùng override | Modal bắt buộc lý do |
| `overrideToken` one-time + TTL (~15 phút) | Dùng ngay, đừng cache lâu |
| Token đã dùng / hết hạn | Hiện lỗi BE, bắt verify lại (cần **OTP admin mới**) |

---

## 7. Hai lớp mã — đừng gộp

| | **Passcode OTP (admin gen)** | **overrideToken (sau verify)** |
|--|------------------------------|--------------------------------|
| Ai tạo | Admin | BE sau verify OK |
| Hình dạng | 6 chữ số (`482910`) | UUID |
| TTL default | **10 phút** | **15 phút** |
| Dùng mấy lần | **1** (chết lúc verify) | **1** (chết lúc submit chỉ số) |
| FE AI giữ | Admin screen gen/hiện mã | Manager app sau verify |

---

## 8. Checklist FE

### Admin (bắt buộc demo ý 5)

```
[ ] Màn / nút gen mã: POST /admin/meter-override/passcodes
[ ] Hiện code lớn + hết hạn (expiresAt)
[ ] (Optional) danh sách activeOnly=true
[ ] (Optional) màn log GET /admin/meter-overrides
[ ] Bỏ mọi chỗ hardcode slms-demo-override / MANAGER_OVERRIDE_PASSCODE
```

### Manager (mobile)

```
[ ] Modal "Xin mã từ admin" → input passcode (text 6 số)
[ ] Verify: contractId = null ở luồng đón khách mới
[ ] meterKind đúng ELEC / WATER khi verify
[ ] Lưu overrideToken theo loại; gắn reason khi submit
[ ] 403 → copy rõ "mã sai/hết hạn — xin admin tạo mã mới"
[ ] Không còn placeholder mã demo cố định
```

### Smoke test với BE

```
1) Admin gen → nhận code
2) Manager verify code + contractId:null + ELEC → 200 + overrideToken
3) Manager verify lại đúng code → 403 (đã chết)
4) Onboard không ảnh + token + reason → OK; GET /admin/meter-overrides có 1 dòng
5) Dùng lại overrideToken → lỗi đã sử dụng
```

---

## 9. Breaking change / env

| Hạng mục | Hành động FE |
|----------|----------------|
| Mã cố định env | **Không còn** — đừng phụ thuộc |
| Endpoint verify path | **Giữ** `POST /api/v1/manager/meter-override/verify` |
| Field request/response verify | **Giữ** (`passcode`, `contractId?`, `meterKind` → `overrideToken`) |
| Endpoint mới | `POST/GET /api/v1/admin/meter-override/passcodes` |

BE config (tham khảo, FE không set):

- `MANAGER_OVERRIDE_PASSCODE_TTL_MINUTES` (default 10)
- `MANAGER_OVERRIDE_TTL_MINUTES` (default 15)

---

## 10. Copy UI gợi ý

| Chỗ | Copy |
|-----|------|
| Manager | *"Không chụp được — xin mã từ quản trị"* |
| Manager sau khi có mã | *"Nhập mã 6 số admin vừa cấp (dùng 1 lần)"* |
| Admin | *"Tạo mã nhập tay đồng hồ"* → *"Gửi mã này cho manager trước khi hết hạn"* |
| Lỗi 403 | *"Mã không đúng hoặc đã hết hạn. Nhờ admin tạo mã mới."* |

---

## 11. Liên hệ / escalate

| Sự cố | Gửi BE kèm |
|-------|------------|
| Gen 403/401 | Role JWT có `ADMIN`? |
| Verify luôn 403 | Screenshot request + mã đã dùng/hết hạn? |
| Onboard token fail | Có gửi `reason`? Có ảnh meter không? Token còn hạn? |
| `contractId` 400 | FE đã bỏ required local-side chưa? BE nhận `null` |

---

**Hết handoff.**  
Tóm lại FE: **thêm màn admin gen OTP**; **manager đổi nguồn passcode** (OTP mỗi lần); **verify + overrideToken + reason** giữ contract cũ; **`contractId: null`** khi đón khách mới.
