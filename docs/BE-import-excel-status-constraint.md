# BE BUG — Import Excel onboarding chặn bởi `properties_status_check`

> Ghi chú cho team BE. FE chỉ đọc repo SEPBE, không sửa BE.
> Ngày phát hiện: 2026-06-17 · Người báo: FE (Tien)

## 1. Triệu chứng

Màn **Import Excel hàng loạt** (`/admin/buildings/import`):

- Bước **Kiểm tra file** (`dryRun=true`) → OK (1 căn nhà, 2 dòng cải tạo, 3 dòng thiết bị).
- Bước **Import dữ liệu** (`dryRun=false`) → HTTP 400, BE trả message:

```
could not execute statement
[ERROR: new row for relation "properties" violates check constraint "properties_status_check"
 Detail: Failing row contains (17, Số 18, Biệt thự ...)]
SQL [update properties set address=?,area_size=?,...,status=?,... where id=?];
constraint [properties_status_check]
```

FE hiển thị đúng message này trong banner lỗi — **không phải lỗi FE**.

## 2. Nguyên nhân

Luồng import set trạng thái property cuối cùng = **`RENOVATION_COMPLETED`** (đúng theo `docs/excel-import-frontend.md` §6). Nhưng CHECK constraint `properties_status_check` **trên database hiện tại** chưa có giá trị này → Postgres chặn câu `UPDATE`.

Bằng chứng (đều đã đúng ở phía code, chỉ DB chưa cập nhật):

- `enums/PropertyStatus.java` → đã có `RENOVATION_COMPLETED`.
- `src/main/resources/db/manual-migration-v2.sql` (dòng 21–34) → đã `DROP` và `ADD` lại constraint bao gồm `RENOVATION_COMPLETED`.

→ File `manual-migration-v2.sql` là **migration THỦ CÔNG** và **chưa được chạy** trên DB đang dùng. DB vẫn giữ constraint cũ.

## 3. Cách sửa (chạy 1 lần trên DB)

Chạy đúng đoạn trong `manual-migration-v2.sql`:

```sql
ALTER TABLE properties DROP CONSTRAINT IF EXISTS properties_status_check;
ALTER TABLE properties ADD CONSTRAINT properties_status_check
    CHECK (status IN (
        'DRAFT',
        'PENDING',
        'UNDER_RENOVATION',
        'PENDING_EQUIPMENT_INSTALLATION',
        'RENOVATION_COMPLETED',
        'PENDING_HOST_REVIEW',
        'PENDING_OPERATION_MANAGER',
        'ACTIVE',
        'DISABLED',
        'MAINTENANCE',
        'INACTIVE'
    ));
```

Sau khi chạy, import lại file Excel → thành công.

## 4. Khuyến nghị

- Đưa nội dung `manual-migration-v2.sql` vào pipeline migration tự động (Flyway/Liquibase) để mọi môi trường (dev/staging/prod) không bị sót.
- Kiểm tra DB còn dữ liệu property nào `status` ngoài danh sách trên trước khi `ADD` constraint (nếu có sẽ ALTER lỗi).
- Lưu ý: căn import dở dang (property đã tạo nhưng UPDATE status fail) có thể đã tồn tại ở DB với status trước đó — kiểm tra rollback transaction của luồng import (`PropertyOnboardingServiceImpl` / import service) đảm bảo toàn bộ thao tác trong 1 transaction, tránh để lại property "mồ côi".

---

# BE BUG #2 — Xóa property không xóa được vì còn ràng buộc khóa ngoại

> Phát hiện khi cố xóa property mồ côi (#19) do BUG #1 để lại.

## 1. Triệu chứng

Bấm **Xóa** một tòa nhà chưa ACTIVE → HTTP 400:

```
ERROR: update or delete on table "properties" violates foreign key constraint
"fk35r032kwh410ggyqcbqrnhcut" on table "rooms"
Detail: Key (id)=(19) is still referenced from table "rooms".
[delete from properties where id=?]
```

## 2. Nguyên nhân

`PropertyServiceImpl.deleteProperty()` (dòng ~129) chỉ gọi `propertyRepository.delete(property)` →
hard `DELETE FROM properties` **không xóa bản ghi con và không có cascade**. Postgres chặn vì còn
các bảng tham chiếu `property_id` / `room_id`.

Bản đồ phụ thuộc (tất cả tham chiếu tới property qua `property_id`, hoặc tới room qua `room_id`):

| Bảng | FK | Ghi chú |
|------|-----|---------|
| `rooms` | property_id | |
| `equipments` | property_id, room_id, manifest_id | |
| `equipment_manifests` | property_id | sau `equipments` |
| `inbound_contracts` | property_id | |
| `depreciation_results` | inbound_contract_id, room_id | xóa sớm nhất |
| `monthly_readings` | property_id, room_id | |
| `renovation_lines` | property_id, session_id | trước `renovation_sessions` |
| `renovation_sessions` | property_id | |
| `property_images` | property_id | @ElementCollection |

## 3. Cách sửa BE (chọn 1)

- **Khuyên dùng — soft delete:** đặt `is_deleted=true` (rooms đã có cột `is_deleted` từ migration v2). Không xóa cứng.
- Hoặc trong `deleteProperty()` xóa con theo đúng thứ tự phụ thuộc trước khi xóa property (1 transaction).
- Hoặc thêm `ON DELETE CASCADE` cho các FK (rủi ro mất dữ liệu — cân nhắc).

## 4. Dọn ngay property mồ côi #19 (chạy 1 lần trên DB)

```sql
BEGIN;
-- 1) Khấu hao (ref inbound_contract_id + room_id) — xóa sớm nhất
DELETE FROM depreciation_results
 WHERE room_id IN (SELECT id FROM rooms WHERE property_id = 19)
    OR inbound_contract_id IN (SELECT id FROM inbound_contracts WHERE property_id = 19);
-- 2) Thiết bị (property_id, room_id, manifest_id)
DELETE FROM equipments WHERE property_id = 19;
-- 3) Chỉ số điện/nước
DELETE FROM monthly_readings WHERE property_id = 19;
-- 4) Hợp đồng đầu vào (sau khấu hao)
DELETE FROM inbound_contracts WHERE property_id = 19;
-- 5) Cải tạo: dòng trước, đợt sau
DELETE FROM renovation_lines WHERE property_id = 19;
DELETE FROM renovation_sessions WHERE property_id = 19;
-- 6) Manifest thiết bị (sau equipments)
DELETE FROM equipment_manifests WHERE property_id = 19;
-- 7) Ảnh
DELETE FROM property_images WHERE property_id = 19;
-- 8) Phòng (sau khấu hao/thiết bị/chỉ số)
DELETE FROM rooms WHERE property_id = 19;
-- 9) Cuối cùng: căn nhà
DELETE FROM properties WHERE id = 19;
COMMIT;
```

> Đổi `19` thành id thực tế nếu khác. Chạy bằng pgAdmin / DBeaver / `psql`.
> Nếu chạy từng dòng mà dòng nào báo "relation does not exist" thì bỏ qua dòng đó (bảng có thể chưa có dữ liệu).
