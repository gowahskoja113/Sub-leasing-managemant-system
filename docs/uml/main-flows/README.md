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

## F4 — quy tắc luồng bảo trì (chốt)

1. Tenant tạo phiếu như cũ (`POST /maintenance`).
2. Manager **xem phiếu không cần quét QR**. Quét QR chỉ khi **bắt đầu xử lý** (`PUT /{id}/confirm-arrival` + `qrCode` khớp thiết bị).
3. GET phiếu trả snapshot thiết bị: số lần bảo trì, khấu hao còn lại, ngày mua, thời hạn BH, BH còn lại.
4. Chẩn đoán / phân lỗi **không bắt buộc thêm ảnh** (đã có ảnh tenant báo + xác nhận hiện trường).
5. Nhập **một lần chi phí** → lập hoá đơn → sửa ngay → `WAITING_PAYMENT` nếu thu tenant; **CLOSED chỉ khi PAID** (hạn 3 ngày; quá hạn giữ phiếu + cron escalate).
6. Complete ghi `EquipmentMaintenanceHistory` kèm ảnh. Admin xem trên web `GET /equipment/{id}` + `.../maintenance-history`. Tenant xem trên mobile `GET /tenant/me/equipments`.
7. `penaltyFee` bắt buộc trên mọi thiết bị; hết BH đền theo `penaltyFee` (không fallback giá mua).


## System domain class diagram (UML 2.1)

Xem `docs/uml/erd/` — `SLMS-core-erd.puml` (overview) và `SLMS-system-erd.puml` (đầy đủ theo package).
