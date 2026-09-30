# SLMS ERD — Core (clean)

Mở file `.puml` bằng PlantUML / VS Code PlantUML, hoặc preview Mermaid bên dưới.

```mermaid
%%{init: {"er": {"layoutDirection": "LR"}, "themeVariables": {"lineColor": "#222"}}}%%
erDiagram
  %% Identity
  User ||--o| Admin : is
  User ||--o| Owner : is
  User ||--o| Tenant : is
  User ||--o| OperationManagement : is
  OperationManagement }o--o{ Zone : manages

  %% Property
  Zone ||--|{ Property : has
  Property ||--|{ Room : has
  Property ||--o| InboundContract : has
  Property ||--|{ MasterLease : has

  %% Lease
  Property ||--|{ TenantContract : has
  Tenant |o--|{ TenantContract : signs
  Room |o--|{ TenantContract : assigned
  TenantContract ||--|{ HouseholdMember : has
  TenantContract ||--|{ ExtensionRequest : has

  %% Utility
  Property ||--|{ UtilityBill : has
  Property ||--|{ MeterReading : has
  Property ||--|{ MonthlyReading : has
  Property ||--|{ UtilityInvoice : has
  Room |o--|{ MeterReading : has
  Room |o--|{ MonthlyReading : has
  Room |o--|{ UtilityInvoice : for
  TenantContract |o--|{ UtilityInvoice : billed

  %% Billing
  TenantContract ||--|{ TenantInvoice : has
  TenantInvoice ||--o| TenantPayment : paid_by
  TenantInvoice ||--|{ TenantPaymentClaim : claimed
  Property ||--|{ Expense : has
  Property ||--|{ HostExpense : has

  %% Ops
  Property ||--|{ Equipment : has
  Room |o--|{ Equipment : has
  Property ||--|{ MaintenanceRequest : on
  Tenant ||--|{ MaintenanceRequest : requests
  TenantContract |o--|{ MaintenanceRequest : on
  Equipment |o--|{ MaintenanceRequest : concerns
  TenantContract ||--|{ CheckoutRequest : requests
  CheckoutRequest ||--o| CheckoutSettlement : settles
```

## Quan hệ (bảng nhanh)

| From | Card. | To | Ghi chú |
|------|-------|-----|---------|
| User | 1—0..1 | Admin / Owner / Tenant / OperationManagement | role profile |
| OperationManagement | *—* | Zone | quản lý khu vực |
| Zone | 1—* | Property | |
| Property | 1—* | Room | |
| Property | 1—0..1 | InboundContract | HĐ chủ nhà |
| Property | 1—* | MasterLease | |
| Property | 1—* | TenantContract | hub thuê |
| Tenant | 0..1—* | TenantContract | |
| Room | 0..1—* | TenantContract | null = thuê cả nhà |
| TenantContract | 1—* | HouseholdMember / ExtensionRequest | |
| Property | 1—* | UtilityBill | HĐ điện/nước nhà nước (tháng) |
| Property | 1—* | MeterReading / MonthlyReading / UtilityInvoice | |
| TenantContract | 0..1—* | UtilityInvoice | hóa đơn ĐN cho khách |
| TenantContract | 1—* | TenantInvoice | hóa đơn thuê tháng |
| TenantInvoice | 1—0..1 | TenantPayment | |
| TenantInvoice | 1—* | TenantPaymentClaim | |
| Property | 1—* | Expense / HostExpense | |
| Property | 1—* | Equipment / MaintenanceRequest | |
| TenantContract | 1—* | CheckoutRequest | |
| CheckoutRequest | 1—0..1 | CheckoutSettlement | |
