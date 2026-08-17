# BE DONE — Lịch sử thu tiền + Cron nhắc đón khách

**Ngày:** 17/08/2026  
**Người gửi:** team BE  
**Người nhận:** team FE (mobile manager/tenant + web admin/host)  
**Phạm vi chat này:** 2 spec

| Spec | Việc BE |
|------|---------|
| `BE-NEED-manager-xem-lich-su-thu-tien-2026-08-17.md` | Endpoint lịch sử thu từ `tenant_payments` |
| `BE-NEED-nhac-lich-don-khach-2026-08-17.md` | Cron nhắc đón khách 07:15 |

Thay `BE-DONE-manager-xem-lich-su-thu-tien-2026-08-17.md` — file này là báo cáo gộp.

---

## Tóm tắt

| Hạng mục | BE | FE |
|----------|----|----|
| `GET /manager/payments` (claims) | Giữ | Hàng chờ đối soát — Xác nhận / Từ chối |
| `GET /manager/payments/history` | ✅ mới | Nguồn **Đã thu** — bỏ workaround hoá đơn `PAID` |
| Mask tiền nhà / `HD-ONBOARD-*` | ✅ | MANAGER: `amount = null` |
| Cron `remindUpcomingReception` 07:15 | ✅ | Không API mới — nhận `RECEPTION_*` như đã normalize |
| `screen` + `params.contractId` | ✅ | Mở `ResumeContract` |
| Host quá hạn đón | ✅ `host_notifications` | `GET /host/notifications` — **không** `/notifications` |

---

# Phần A — Lịch sử thu tiền

## A1. Vì sao màn đối soát trống

`GET /api/v1/manager/payments` đọc **claim chờ đối soát**, không đọc bảng thanh toán. PayOS webhook ghi `tenant_payments` + hoá đơn `PAID` — **không sinh claim**.

Hai nguồn, không gộp một list:

| Nguồn | API | UI |
|-------|-----|----|
| Cần xử lý | `GET /api/v1/manager/payments` | Claim `PENDING_VERIFY` — nút Xác nhận / Từ chối |
| Đã thu | `GET /api/v1/manager/payments/history` | Timeline `tenant_payments` — chỉ đọc |

Bỏ suy “đã thu” từ invoices `PAID`: một hoá đơn một dòng, mất dòng nếu HĐ không còn PAID, không lọc `from`/`to` ở BE.

## A2. API mới

```
GET /api/v1/manager/payments/history
Authorization: Bearer {manager|admin|owner}
```

`@PreAuthorize("hasAnyRole('MANAGER','ADMIN','OWNER')")`

| Param | Bắt buộc | Mặc định | Ý nghĩa |
|-------|----------|----------|---------|
| `propertyId` | ❌ | — | Lọc tòa |
| `contractId` | ❌ | — | Lọc HĐ |
| `from` | ❌ | — | `yyyy-MM-dd` inclusive theo `paidAt` |
| `to` | ❌ | — | `yyyy-MM-dd` inclusive hết ngày |
| `page` | ❌ | `0` | 0-based |
| `size` | ❌ | `20` | |

`from` sau `to` → 400 `from không được sau to`.

MANAGER chỉ nhà mình (`operationManagerId`). ADMIN/OWNER xem hết.

### Response (Spring `Page`, giống `/manager/deposits`)

```json
{
  "content": [
    {
      "id": 12,
      "invoiceId": 7,
      "invoiceCode": "HD-ONBOARD-1",
      "invoiceType": "OTHER",
      "contractId": 45,
      "tenantName": "Nguyễn Văn A",
      "propertyName": "Nhà A",
      "roomNumber": "101",
      "amount": null,
      "method": "QR",
      "paidAt": "2026-08-17T14:30:00",
      "transactionId": "VQR-1723890123456"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

| Field | Ý nghĩa |
|-------|---------|
| `id` | PK `tenant_payments` — **không** trùng id claim |
| `amount` | MANAGER: `null` nếu RENT hoặc `HD-ONBOARD-*`. ADMIN/OWNER: số thật |
| `method` | `QR` / `CASH` / `BANK_TRANSFER` (`PAYOS` → `QR`) |

Sắp xếp `paidAt DESC`.

```http
GET /api/v1/manager/payments/history
GET /api/v1/manager/payments/history?from=2026-08-01&to=2026-08-17
GET /api/v1/manager/payments/history?propertyId=7&contractId=45&page=0&size=20
```

Claims **không đổi:**

```
GET /api/v1/manager/payments?status=
POST /api/v1/manager/payments/{id}/verify
POST /api/v1/manager/payments/{id}/reject
```

## A3. Checklist FE — Thu & Đối soát

- [ ] **Cần xử lý** ← `GET /manager/payments?status=PENDING_VERIFY`
- [ ] **Đã thu** ← `GET /manager/payments/history` (không ghép invoices `PAID`)
- [ ] Date picker gửi `from` / `to`
- [ ] MANAGER `amount == null` → không hiện `0đ`
- [ ] Không trộn id claim với id history
- [ ] Không PATCH/POST history

## A4. Test plan thu tiền

1. Tenant PayOS xong → `/tenant/me/payments` có dòng.
2. Manager `/manager/payments` vẫn `[]` nếu không claim.
3. `/manager/payments/history` **có** dòng (`HD-ONBOARD-1`, …).
4. MANAGER: RENT / onboard `amount: null`. ADMIN: có số.
5. Manager A không thấy nhà manager B.

---

# Phần B — Cron nhắc đón khách

## B1. Vấn đề cũ

Giữa `TENANT_ONBOARDING` (gán hồ sơ) và `TENANT_CONTRACT_NO_SHOW` (đã huỷ sau `no-show-grace-days`) không có nhắc. Ví dụ: gán 16/08, hẹn 31/08 im lặng, 10/09 mới báo HĐ mất.

## B2. Cron

```
0 15 7 * * *   Asia/Ho_Chi_Minh
```

Chạy **trước** `autoCancelNoShowContracts()` (08:05). Quét HĐ `DRAFT`/`PENDING`, mốc `expectedReceptionDate ?? moveInDate`.

`noShowGraceDays` lấy `${contract.no-show-grace-days:10}` — không hard-code.

### Mốc

| Điều kiện | Type | Title (ví dụ) | Ai nhận |
|-----------|------|---------------|---------|
| Còn 1 ngày | `RECEPTION_REMINDER_TOMORROW` | 🗓 Mai đón khách: Nguyễn Minh Quân · Phòng 101 · HD-MT-2026-00014 | Quản lý |
| Đúng ngày | `RECEPTION_DUE_TODAY` | 🔔 Hôm nay đón khách: … | Quản lý |
| Quá hạn **1 / 3 / 7** ngày | `RECEPTION_OVERDUE` | ⚠️ Quá hạn đón 3 ngày — còn 7 ngày nữa hợp đồng tự huỷ | Quản lý + **admin + host** |

Không nhắc “còn 2 ngày” / “còn 3 ngày”.

Tiền tố **`RECEPTION_`** bắt buộc — FE `normalizeType` xếp tab Đón khách. Đổi tên type là rơi nhóm Hệ thống.

### Quản lý phụ trách

`property.operationManagerId` → `managedBy` → `contract.assignedManager` (cùng thứ tự thanh toán).

### Host ≠ notifications

| Vai | API | Bảng |
|-----|-----|------|
| Manager · Tenant · Admin | `GET /api/v1/notifications` | `notifications` |
| **Host** | **`GET /api/v1/host/notifications`** | **`host_notifications`** |

Quá hạn: host `insertIfAbsent` với `dedupe_key`. Ghi `Notification` cho host = chuông host trống.

### Chống trùng

Khoá: `reception-remind:{contractId}:{tomorrow\|today\|overdue-N}`

- `notifications.dedupe_key` + unique `(user_id, dedupe_key)` WHERE NOT NULL
- `host_notifications` sẵn `ON CONFLICT DO NOTHING`

Mỗi mốc quá hạn (1 / 3 / 7) bắn **một lần**.

### `screen` + `params` (bắt buộc)

```json
{
  "type": "RECEPTION_DUE_TODAY",
  "screen": "ResumeContract",
  "params": { "contractId": 14 }
}
```

Push Expo cùng `screen` + `contractId`. FE đã ưu tiên `params.contractId` (17/08) — không cần build app lại.

## B3. Checklist FE — nhắc đón

FE đã làm `normalizeType` + điều hướng `contractId`. Còn:

- [ ] Chuông manager/admin: type `RECEPTION_*` → tab Đón khách, tap mở đúng HĐ
- [ ] Host web: refetch `/host/notifications` — type `RECEPTION_OVERDUE`, priority `HIGH`
- [ ] Không expect API REST mới cho cron này

## B4. Test plan nhắc đón

1. HĐ DRAFT/PENDING, `expectedReceptionDate` = ngày mai → 07:15 manager nhận `RECEPTION_REMINDER_TOMORROW`. Restart app cùng ngày → **không** nhân đôi.
2. Đúng ngày đón → `RECEPTION_DUE_TODAY`.
3. Trễ 3 ngày (`grace=10`) → manager + admin + host: “còn 7 ngày nữa hợp đồng tự huỷ”.
4. 08:05 ngày huỷ → `TENANT_CONTRACT_NO_SHOW` như cũ; buổi sáng hôm đó không nhận “còn 1 ngày” rồi “đã huỷ” cùng lúc (nhắc quá hạn chỉ 1/3/7).
5. Host **không** thấy nhắc trên `/api/v1/notifications`.

---

# File BE

| File | Việc |
|------|------|
| `ManagerBillingController` / `Service` | `GET /manager/payments/history` |
| `TenantPaymentRepository` | `findHistoryForManager` |
| `ManagerPaymentHistoryResponse` | DTO history |
| `ContractLifecycleCron` | `remindUpcomingReception` 07:15 |
| `TenantOnboardingServiceImpl` | Logic nhắc + mask người nhận |
| `Notification` / `NotificationRepository` | `dedupe_key` |
| `DatabaseSchemaMigration` / `schema.sql` | Cột + unique index |

Migration tự chạy lúc start app. Không đổi REST tenant.

---

*Gộp 2 spec 17/08/2026. Claims không thay history. Host chỉ đọc `host_notifications`.*
