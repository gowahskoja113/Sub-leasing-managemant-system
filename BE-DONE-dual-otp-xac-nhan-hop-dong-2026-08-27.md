# Dual-OTP xác nhận hợp đồng (BE đã triển khai)

Ngày: 2026-08-27  
Phạm vi: **chỉ BE** (`slms2026`). FE tự tích hợp theo contract dưới đây.

## Luồng mới

```
Manager chốt hiện trạng → QR cọc → Tenant CK
        ↓
completeDepositPayment
  + getOrCreateTenant()     ← tạo/gắn User ngay khi PAID
  + notify
        ↓
Tenant chưa có MK (firstLogin) → kích hoạt TK (TENANT_ACTIVATION)
        ↓
Tenant login → có HĐ PENDING+PAID → màn xác nhận HĐ
        ↓
Tenant POST .../send-confirm-otp  → sinh 2 mã (TENANT + MANAGER)
        ↓
Tenant confirm-otp ─┐
                    ├→ đủ 2 mốc → activateContract() 1 lần
Manager confirm ────┘     ACTIVE + phòng RENTED + hoá đơn kỳ đầu
```

## OTP purposes

| Purpose | Ai dùng |
|---------|---------|
| `TENANT_ACTIVATION` | Kích hoạt tài khoản (giữ nguyên) |
| `CONTRACT_CONFIRM_TENANT` | Khách xác nhận HĐ |
| `CONTRACT_CONFIRM_MANAGER` | Manager xác nhận HĐ |
| `CONTRACT_CONFIRM` | Legacy — không còn dùng gửi mới |

Cả mã confirm vẫn gửi về số override budget `0352393203`. Log DEV có prefix `[TENANT]` / `[MANAGER]`.

## Cột DB mới (`tenant_contracts`)

- `tenant_otp_verified_at TIMESTAMP NULL`
- `manager_otp_verified_at TIMESTAMP NULL`

Migration tự chạy trong `DatabaseSchemaMigration` + cập nhật check constraint `otp_verifications_purpose_check`.

## Endpoint

### Tenant (`ROLE_TENANT`)

| Method | Path | Việc |
|--------|------|------|
| `GET` | `/api/v1/tenant/me/contracts/pending-confirm` | HĐ chờ confirm — **200** body hoặc **204** |
| `POST` | `/api/v1/tenant/me/contracts/{id}/send-confirm-otp` | Sinh 2 mã (chỉ gửi lại mã bên chưa verify) |
| `POST` | `/api/v1/tenant/me/contracts/{id}/confirm-otp` | Body `{ "otp": "123456" }` |

Alias prefix: `/api/v1/me/tenant-contracts/...` cũng được.

Điều kiện:
- `contract.tenant` = user hiện tại
- `status` PENDING (hoặc DRAFT đã PAID) + `paymentStatus == PAID`
- Cửa sổ nhận sớm kiểm **lúc gửi OTP**, không chặn lúc verify

### Manager (`MANAGER` / `ADMIN`) — path cũ, đổi ruột

| Method | Path | Việc |
|--------|------|------|
| `POST` | `/api/v1/tenant-contracts/{id}/send-otp` | **Chỉ** gửi lại mã MANAGER |
| `POST` | `/api/v1/tenant-contracts/{id}/confirm` | Verify mã MANAGER → thử activate |

Manager **không** auto-activate nếu tenant chưa verify. Response vẫn PENDING kèm progress.

## Response thêm field

```json
{
  "tenantOtpVerifiedAt": "2026-08-27T20:00:00",
  "managerOtpVerifiedAt": null,
  "activatedAt": null,
  "status": "PENDING",
  "paymentStatus": "PAID"
}
```

Khi đủ 2 mốc → `status: ACTIVE`, `activatedAt` có giá trị.

## Kích hoạt tài khoản sớm

`TenantActivationService`: đủ điều kiện nếu có HĐ **ACTIVE** **hoặc** `(PENDING && PAID)`.

→ Khách tạo TK lúc thanh toán xong có thể kích hoạt **trước** khi HĐ ACTIVE.

## Realtime (cùng queue `/user/queue/billing`)

| `event` | Ý nghĩa |
|---------|---------|
| `CONTRACT_CONFIRM_PROGRESS` | Một bên vừa verify / vừa gửi OTP |
| `CONTRACT_ACTIVATED` | HĐ vừa ACTIVE |

Payload thêm: `tenantOtpVerified`, `managerOtpVerified`, `contractId`, `contractStatus`, `paymentStatus`.

## FE cần làm (không nằm trong BE)

1. Bỏ auto-send OTP phía manager sau khi PAID — chờ tenant bấm gửi.
2. Manager UI: chờ kích hoạt → chờ OTP → nhập mã MANAGER + tiến độ 2 bên.
3. Tenant: sau login/activate → ép `ContractConfirmScreen` nếu `GET pending-confirm` = 200.
4. Tenant bấm gửi OTP → nhập mã TENANT; subscribe realtime.
5. Chỉ navigate success khi `status === 'ACTIVE'`.

## Ca biên BE đã xử lý

- Gửi lại OTP: không reset mốc bên đã verify
- Confirm khi đã ACTIVE: idempotent, trả response
- Activate chỉ chạy khi đủ 2 timestamp
- `onboardedByManager` ghi lúc manager verify OTP (không đợi activate, tránh ghi nhầm nếu tenant verify cuối)
