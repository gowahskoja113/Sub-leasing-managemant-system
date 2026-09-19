# UML 2.1 — 6 luồng chính SLMS

Bám proposal + mẫu class (VitalSign) / sequence (CreateAppointment).

## Map mẫu → Spring Boot SLMS

| Mẫu tham khảo | SLMS |
|---------------|------|
| `*Controller` + `IMediator` | `*Controller` gọi thẳng `*Service` |
| `*Command` | `*Request` DTO |
| `*CommandHandler` | `*ServiceImpl` |
| `IUnitOfWork` / `UnitOfWork` | `@Transactional` trên ServiceImpl |
| `IGenericRepository<T>` | `*Repository extends JpaRepository` |
| Entity + quan hệ 1..\* | Entity JPA |

## Bộ diagram (12 file)

| Luồng | Class | Sequence |
|-------|-------|----------|
| **F1** Initial setup | `F1-initial-setup-class.puml` | `F1-initial-setup-sequence.puml` |
| **F2** Tenant onboard | `F2-tenant-onboard-class.puml` | `F2-tenant-onboard-sequence.puml` |
| **F3** Monthly billing | `F3-monthly-billing-class.puml` | `F3-monthly-billing-sequence.puml` |
| **F4** Maintenance | `F4-maintenance-class.puml` | `F4-maintenance-sequence.puml` |
| **F5** Checkout | `F5-checkout-class.puml` | `F5-checkout-sequence.puml` |
| **F6** Cash flow | `F6-cashflow-class.puml` | `F6-cashflow-sequence.puml` |

## Render

PlantUML Preview (VS Code/Cursor) hoặc:

```text
java -jar plantuml.jar -charset UTF-8 -tpng *.puml
```

Đặt tên figure trong báo cáo ví dụ: *Figure xx — Monthly Billing Class Diagram*.

## Không đưa vào bộ này

Bulk import, zone CRUD, viewing lead, user admin CRUD, dispute/unlock edge — ngoài 6 luồng proposal.
