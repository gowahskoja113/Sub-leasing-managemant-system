# Sequence diagrams — chuẩn luận văn SLMS

Chuẩn vẽ bám theo mẫu *CreateAppointment* (Figure 32):

| Thành phần mẫu | Tương ứng SLMS (Spring Boot) |
|----------------|------------------------------|
| Customer / Actor | Manager, Admin, Tenant, Owner |
| Front-end | FE (web/app) |
| `*Controller` | `MeterReadingController`, … |
| `*CommandHandler` | `*ServiceImpl` (không dùng CQRS) |
| `GenericRepository<T>` | `*Repository` (Spring Data JPA) |
| `*Service` phụ | Service khác được inject |
| DB | PostgreSQL / DB |

## Quy tắc bắt buộc trên mỗi diagram

1. **Thứ tự lifeline:** Actor → Front-end → Controller → ServiceImpl → Repository / Service phụ → **DB**
2. **Đánh số** mọi mũi tên `(1) (2) …`
3. **Transaction tường minh:** `BeginTransaction` → … → `CommitTransaction`; lỗi dùng fragment **`break`** + `RollbackTransaction`
4. Nhánh nghiệp vụ: **`alt` / `opt` / `else`**
5. Return: mũi tên nét đứt (`-->`)
6. Kết thúc: FE hiển thị kết quả cho Actor

> Spring dùng `@Transactional` (không gọi Begin/Commit tay). Vẫn **vẽ tường minh** như mẫu để đồng bộ báo cáo.

## File trong thư mục này

| File | Luồng |
|------|--------|
| `01-save-locked-meter-reading.puml` | Manager chốt chỉ số (+ override / auto-issue) |
| `02-meter-override-passcode.puml` | Admin gen OTP → Manager verify → overrideToken |
| `03-create-utility-bill-publish.puml` | Admin publish EVN/nước → issue invoice |

## Render

- [PlantUML online](https://www.plantuml.com/plantuml/uml)
- VS Code / Cursor: extension PlantUML → Preview
- Export PNG/SVG rồi chèn vào báo cáo (`Figure xx — … Sequence Diagram`)

## Bộ diagram đề xuất tiếp (cùng chuẩn)

4. Tenant PayOS + `PayosWebhookController`  
5. Onboarding HĐ (cọc → OTP → Host duyệt)  
6. Checkout settlement **hoặc** Maintenance (1 path demo)
