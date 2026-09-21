# SLMS Domain Class Diagram (UML 2.1)

Conceptual class model mapped from JPA `@Entity` (`com.sep490.slms2026.entity`).
Ký pháp **UML 2.1 Class Diagram** (không dùng crow’s foot / Chen ERD).

## Files

| File | Dùng khi |
|------|----------|
| `SLMS-core-erd.puml` | Slide / overview — backbone |
| `SLMS-system-erd.puml` | Báo cáo — overview 8 package + hub bridges |
| `modules/SLMS-m1-identity.puml` … `m8-checkout.puml` | Chi tiết quan hệ từng module (đọc được) |

**Không nhét toàn bộ FK vào 1 tờ** — dễ class văng khỏi package và dây cắt chéo. Overview chỉ hub; full relations nằm ở `modules/`.

**Layout:** `SLMS-system-erd.puml` dùng lưới **2 cột × 4 hàng** (`top to bottom` + hidden edges) + `legend bottom` để tránh canvas quá rộng bị cắt trên preview/web.

## UML 2.1 notation

| Ký hiệu | Ý nghĩa |
|---------|---------|
| `"1" -- "0..1"` | Association (tùy chọn một) |
| `"1" -- "*"` | Association (nhiều) |
| `"1" o-- "*"` | Aggregation (whole–part / has) |
| `"*" -- "*"` | Many-to-many |
| `..` | Soft FK (không có `@ManyToOne`) |
| `<<external>>` | Class thuộc module khác (trên diagram module) |

## Layout (trái → phải / theo số)

1. Identity & Access  
2. Zone & Property  
3. Renovation & Equipment  
4. Tenant Contract  
5. Meter & Utility  
6. Tenant Invoice & Payment  
7. Maintenance  
8. Checkout & Settlement  

## Khác bản vẽ tay cũ

| Vẽ tay (cũ) | Codebase (đúng) |
|-------------|-----------------|
| `TenantContent` / `Precontract` | `TenantContract` |
| `CommercialAgent` | `OperationManagement` |
| `Floor` / `OrganizationUnit` | không có — `Property.totalFloor` + `Room` |
| `WaterReading` / `UnitReading` | `MeterReading` / `MonthlyReading` |
| `AssistantRequest` | `MaintenanceRequest` |

## Render

```text
java -jar plantuml.jar -charset UTF-8 -tpng SLMS-*.puml
java -jar plantuml.jar -charset UTF-8 -tpng modules/*.puml
```

## Cố ý bỏ

`MeterOverride*`, `InvoiceUnlock*`, `ZoneManager*`, `UserPushToken`, `HostNotification`, `PricingConfig`, `Invoice` (legacy).
