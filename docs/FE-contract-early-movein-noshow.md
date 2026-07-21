# FE Handoff — Nhận nhà sớm / No-show auto-cancel (21/07/2026)

Nguồn: BE vừa triển khai vòng đời hợp đồng thuê liên quan ngày vào ở (`moveInDate`).  
Repo BE: `slms2026` (package `com.sep490.slms2026`).

---

## Tóm tắt nghiệp vụ

| Tình huống | Rule BE | Ảnh hưởng FE |
|------------|---------|--------------|
| Khách **đến sớm** (trước `moveInDate` draft) | Cho phép tối đa **3 ngày**. Ghi `moveInDate = startDate = hôm nay`, **giữ nguyên `endDate`** (ở free những ngày sớm). | Confirm OTP sớm hơn lịch vẫn OK trong 3 ngày; sớm hơn nữa → BE trả lỗi. |
| Khách **đến trễ** (sau `moveInDate`) | Khách tự chịu thiệt — **không dời `endDate`**. | Không cần FE xử lý gì đặc biệt khi confirm muộn (nếu HĐ vẫn DRAFT/PENDING và chưa bị hủy). |
| Khách **không đến** quá hạn | Quá **`moveInDate + 10 ngày`** mà HĐ còn `DRAFT`/`PENDING` → BE **tự hủy** (`TERMINATED`, `terminationType = NO_SHOW`). | List/detail hiện lý do hủy; căn được mở lại. |
| Tiền giữ chỗ bên ngoài | **Không nằm trong scope này** — ops thu ngoài rồi mới import Excel. | Không có API thu/hoàn phí giữ chỗ. |

Config BE (có thể đổi, không hardcode):

```yaml
contract:
  max-early-move-in-days: 3
  no-show-grace-days: 10
```

---

## 1. Nhận nhà sớm (early move-in)

### Khi nào chạy

API confirm OTP sau khi đã thanh toán cọc:

```http
POST /api/v1/tenant-contracts/{id}/confirm
```

(Body OTP như flow hiện tại — xem `docs/FE-tenant-onboarding-otp-flow.md`.)

### Logic BE trong `confirmContract()`

1. Verify OTP + `paymentStatus == PAID` (như cũ).
2. Nếu `hôm nay < moveInDate` (đón sớm):
   - `daysEarly = moveInDate − hôm nay`
   - Nếu `daysEarly > 3` → **422** BusinessException, message dạng:
     > Chỉ được nhận nhà sớm tối đa 3 ngày so với ngày vào ở dự kiến (yyyy-MM-dd). Vui lòng cập nhật lại ngày vào ở hoặc nhận đúng lịch.
   - Nếu `daysEarly ≤ 3` → set:
     - `moveInDate = hôm nay`
     - `startDate = hôm nay`
     - **`endDate` không đổi**
3. Set `status = ACTIVE` (như cũ).

### Việc FE nên làm

- Cho phép gọi confirm khi còn trước `moveInDate` **tối đa 3 ngày**.
- Nếu BE trả lỗi early > 3 ngày: hiện message BE; hướng dẫn manager cập nhật draft (`PUT /tenant-contracts/{id}` đổi `moveInDate`) rồi confirm lại.
- Sau confirm thành công: refresh contract — `moveInDate`/`startDate` có thể đã đổi thành hôm nay; `endDate` giữ nguyên.
- UI copy gợi ý (optional):
  > Nhận nhà sớm tối đa 3 ngày. Thời hạn hợp đồng (`endDate`) không thay đổi — những ngày sớm được miễn phí.

### Không đổi

- Không có field request mới.
- Không dời `endDate`, không tính prorate / thêm tiền.

---

## 2. Auto-cancel no-show (quá 10 ngày)

### Điều kiện hủy

Cron BE chạy **mỗi ngày 08:05** (`Asia/Ho_Chi_Minh`):

- Status ∈ `DRAFT`, `PENDING`
- `moveInDate < hôm nay − 10 ngày`  
  (tức đã quá ngày vào ở dự kiến **hơn 10 ngày**)
- → `status = TERMINATED`

### Ghi nhận lý do (field sẵn có — không thêm cột mới)

| Field | Giá trị |
|-------|---------|
| `status` | `TERMINATED` |
| `terminationType` | **`NO_SHOW`** *(enum mới)* |
| `terminationReason` | Text: `Tự động hủy: khách không đến nhận nhà quá 10 ngày kể từ ngày vào ở dự kiến (yyyy-MM-dd)` |
| `terminatedAt` | Timestamp lúc cron chạy |
| Phòng / căn | Được giải phóng (slot mở lại cho HĐ mới) |

### Enum `ContractTerminationType` (cập nhật)

```text
EARLY_MOVE_OUT
VIOLATION
MUTUAL_AGREEMENT
NO_SHOW          ← mới
OTHER
```

FE: map label hiển thị, ví dụ:

| Enum | Label gợi ý |
|------|-------------|
| `NO_SHOW` | Không đến nhận nhà (tự động hủy) |
| `EARLY_MOVE_OUT` | Trả phòng sớm |
| `VIOLATION` | Vi phạm HĐ |
| `MUTUAL_AGREEMENT` | Hai bên thỏa thuận |
| `OTHER` | Khác |

### Notify

BE gửi in-app (+ push nếu có token) cho **assigned manager** của HĐ:

- Title: `Hợp đồng tự động hủy (no-show)`
- Type: `TENANT_CONTRACT_NO_SHOW`

### Việc FE nên làm

1. **List / detail HĐ**: hiện `terminationType` + `terminationReason` khi `status === TERMINATED`.
2. **Filter / badge**: tách `NO_SHOW` khỏi hủy tay (cancel / terminate) nếu UI có phân loại.
3. **Căn trống**: sau no-show, phòng/nhà có thể import / tạo HĐ mới — không block vì HĐ cũ đã `TERMINATED`.
4. **Không cần gọi API mới** để trigger hủy — BE cron tự chạy. (Không expose endpoint manual trong scope này.)
5. Optional UX: trên màn DRAFT/PENDING sắp tới / đã qua `moveInDate`, hiện cảnh báo:
   > Quá 10 ngày sau ngày vào ở dự kiến mà chưa kích hoạt → hệ thống tự hủy.

---

## 3. Khách đến trễ (trong grace 10 ngày)

- Vẫn **confirm OTP bình thường** nếu HĐ chưa bị hủy.
- BE **không** đổi `moveInDate` / `endDate` vì đến trễ.
- Khách tự chịu những ngày trễ (thời hạn vẫn kết thúc đúng `endDate` cũ).

FE: không cần logic đặc biệt ngoài không chặn confirm khi `hôm nay > moveInDate` (miễn chưa quá hạn auto-cancel).

---

## 4. Ngoài scope (nhắc lại)

- **Tiền giữ chỗ bên ngoài**: không API, không field, không hoàn. Ops deal ngoài → import Excel.
- **ACTIVE** quá hạn `endDate`: vẫn theo flow `EXPIRED` cũ (không gộp vào `NO_SHOW`).
- **Thanh lý tay** (`/terminate`): vẫn dùng các type `EARLY_MOVE_OUT` / `VIOLATION` / … — khác `NO_SHOW`.

---

## Checklist verify cho FE

- [ ] Confirm OTP **1–3 ngày trước** `moveInDate` → 200, `moveInDate`/`startDate` = hôm nay, `endDate` không đổi.
- [ ] Confirm OTP **> 3 ngày trước** `moveInDate` → 422, hiện message BE.
- [ ] Confirm OTP **sau** `moveInDate` (chưa quá 10 ngày, HĐ còn DRAFT/PENDING) → 200, ngày không bị dời.
- [ ] HĐ DRAFT/PENDING có `moveInDate` cách đây > 10 ngày → sau cron (hoặc ngày hôm sau) `TERMINATED` + `terminationType = NO_SHOW`.
- [ ] UI hiện đúng label / reason cho `NO_SHOW`.
- [ ] Enum `NO_SHOW` không làm crash map cũ (thêm case / fallback `OTHER`).

---

## File BE liên quan (tham khảo)

| File | Thay đổi |
|------|----------|
| `ContractTerminationType.java` | + `NO_SHOW` |
| `TenantOnboardingServiceImpl.confirmContract()` | Early move-in ≤ 3 ngày |
| `TenantOnboardingServiceImpl.autoCancelNoShowContracts()` | Hủy no-show + notify |
| `ContractLifecycleCron.java` | Cron 08:05 hằng ngày |
| `application.yaml` | `contract.max-early-move-in-days`, `contract.no-show-grace-days` |
