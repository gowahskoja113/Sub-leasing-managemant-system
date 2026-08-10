# BE DONE — Bắt buộc bằng chứng chỉ số đồng hồ + trần tạo mã

**Ngày:** 10/08/2026  
**Người gửi:** team BE  
**Người nhận:** team FE (mobile)  
**Phản hồi:** `BE-NEED-meter-evidence-enforcement-2026-08-10.md`

---

## Tóm tắt

BE đã siết 2 mục 🔴 trong NEED. Cơ chế passcode/override **không còn chỉ là quy ước app** — thiếu cả ảnh lẫn token khi có ghi chỉ số sẽ **bị chặn ở server**.

| # | Việc FE yêu cầu | Trạng thái |
|---|-----------------|------------|
| 1 | Bắt buộc ảnh **HOẶC** mã (token) khi ghi chỉ số — 2 call site | ✅ Đã bật |
| 2 | Trần 20 mã / giờ / admin → **429** | ✅ Đã bật |
| 3 | Job dọn mã hết hạn chưa dùng | ⏸ Sau demo (chưa làm) |

---

## 1. Bắt buộc bằng chứng (`requireMeterEvidence`)

### Quy tắc

Khi request **có gửi chỉ số** (`initialElectricReading` / `initialWaterReading` ≠ null):

| Đồng hồ | Cần một trong hai |
|---------|-------------------|
| Điện | `electricMeterImageUrl` (không blank) **hoặc** `electricMeterOverrideToken` |
| Nước | `waterMeterImageUrl` (không blank) **hoặc** `waterMeterOverrideToken` |

- Xét **riêng từng đồng hồ**.
- `reading == null` → **không chặn** (draft chưa tới bước đồng hồ).
- Có ảnh thì **không bắt** token; token + reason chỉ khi nhập tay không ảnh (giữ hành vi cũ).

### Áp dụng ở đâu

| API / luồng | Ghi chú |
|-------------|---------|
| Onboard tenant (`onboardTenant`) | Check trên field request **trước** `applyMeterOverridesIfAny` |
| Update draft (`updateDraftContract`) | Check **sau khi merge** ảnh vào contract (URL sau merge, không dùng ảnh cũ miss cache) |

### Lỗi FE nhận khi thiếu bằng chứng

- Type: business error (giống các `BusinessException` khác)
- Message mẫu:

```text
Thiếu bằng chứng chỉ số điện: cần ảnh mặt đồng hồ, hoặc mã do admin cấp kèm lý do.
Thiếu bằng chứng chỉ số nước: cần ảnh mặt đồng hồ, hoặc mã do admin cấp kèm lý do.
```

### Luồng hợp lệ (nhắc lại)

```
[Có ảnh]
  initialXxxReading + electric|waterMeterImageUrl
  → OK (không cần passcode)

[Không ảnh]
  1) Admin: POST /api/v1/admin/meter-override/passcodes
  2) Manager: POST /api/v1/manager/meter-override/verify
     { passcode, meterKind: ELEC|WATER, contractId?: null khi đón khách mới }
  3) Onboard / update draft:
     initialXxxReading
     + electric|waterMeterOverrideToken
     + electric|waterMeterOverrideReason  (bắt buộc khi consume)
     + image = null
  → OK + dòng trong GET /api/v1/admin/meter-overrides
```

### Case Postman / app bỏ qua

| Request | Trước | Sau |
|---------|-------|-----|
| Có reading, null ảnh, null token | ✅ tạo HĐ | ❌ BusinessException |
| Có reading + ảnh | ✅ | ✅ |
| Có reading + token (+ reason) | ✅ | ✅ |
| Không reading (chưa ghi chỉ số) | ✅ | ✅ (vẫn không chặn) |

---

## 2. Trần gen mã passcode

`POST /api/v1/admin/meter-override/passcodes`

| Hằng số | Giá trị | Ý nghĩa |
|---------|---------|---------|
| `MAX_GEN_PER_HOUR` | **20** | Tối đa mã **một admin** tạo trong **1 giờ** (cửa sổ lăn theo `createdAt`) |

Khi vượt:

- **HTTP 429** `TOO_MANY_REQUESTS`
- Message: `Đã tạo quá nhiều mã trong 1 giờ. Chờ ít phút rồi thử lại.`

FE đã bắt 429 + hiện `message` → **không cần đổi FE**.

> Không nhầm với `MAX_FAILS = 5` (sai mã verify) hay `MAX_GEN_ATTEMPTS = 20` (retry sinh số trùng).

---

## 3. Chưa làm (sau demo)

- `@Scheduled` xóa `meter_override_passcodes` **chưa dùng** và `expires_at` cũ hơn 7 ngày.
- Mã **đã dùng** vẫn giữ để audit.

---

## 4. Giữ nguyên (không đụng)

| Chi tiết |
|----------|
| OTP chết ngay khi verify (`usedAt` / `usedBy`) |
| `overrideToken` theo `managerId` + `meterKind` + TTL + 1 lần |
| Bắt `reason` khi consume override → `meter_override_logs` |
| Chỉ consume token khi **không có ảnh** |
| Sai 5 lần verify → khoá 5 phút |
| `contractId: null` lúc đón khách (HĐ chưa có) |

---

## 5. Checklist test nhanh (FE / QA)

- [ ] Onboard **có reading + null ảnh + null token** → **lỗi** message bằng chứng điện/nước
- [ ] Onboard **có reading + ảnh** → OK
- [ ] Admin gen mã → manager verify ELEC → onboard **null ảnh + token + reason** → OK; log admin có 1 dòng
- [ ] Chỉ điện có ảnh, nước chỉ token (hoặc ngược lại) → OK
- [ ] Update draft gửi reading mới mà không ảnh/token, contract cũng không ảnh → **lỗi**
- [ ] Update draft gửi reading, contract **đã có** ảnh từ trước → OK (merge ảnh)
- [ ] Draft **không** gửi reading → không bị chặn vì meter
- [ ] Admin gen > 20 mã / giờ → **429** + message trần

---

## 6. File BE đã chạm

| File | Thay đổi |
|------|----------|
| `TenantOnboardingServiceImpl` | `requireMeterEvidence` + gọi ở onboard & updateDraft |
| `MeterOverrideServiceImpl` | chặn gen khi `count ≥ 20` / giờ / admin |
| `MeterOverridePasscodeRepository` | `countByCreatedByAndCreatedAtAfter` |

---

**FE không bắt buộc đổi contract API** — field giữ nguyên; chỉ cần biết BE giờ **reject** case thiếu bằng chứng (trước đây lọt) và gen mã có trần 429.
