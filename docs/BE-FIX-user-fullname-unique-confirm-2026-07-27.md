# BE Fix — Confirm HĐ fail vì unique `full_name`

**Ngày:** 2026-07-27  
**Log:** `duplicate key ... uk… (full_name)=(Trần Thị Bình)` →  
`BusinessException: Tên khách thuê hoặc SĐT đã tồn tại`

---

## Nguyên nhân

Khi confirm/onboard, `getOrCreateTenant` không tìm thấy user theo SĐT → INSERT user mới → vỡ **unique `User.full_name`**.

Họ tên trùng là bình thường; identity đúng phải là **SĐT / username**, không phải `full_name`.

---

## Fix

1. Bỏ `unique = true` trên `User.fullName` (entity).
2. Migration startup: drop unique constraint/index trên `"User".full_name`.
3. Lookup tái dùng account: phone local / `+84` / **username**.
4. Message lỗi không còn gộp “trùng tên hoặc SĐT”.

Restart app để migration chạy. Confirm lại HĐ với cùng tên khách (SĐT khác hoặc cùng account) sẽ không còn chặn vì tên.
