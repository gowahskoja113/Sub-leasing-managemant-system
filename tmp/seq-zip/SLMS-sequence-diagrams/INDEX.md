# SLMS Sequence Diagrams (66)

Each `NN_Name.puml` (PlantUML source) has a matching `NN_Name.png`. Style follows SaveLockedMeterReading.

| # | Group | Diagram |
|---|---|---|
| 01-03 | Authentication | Login, TenantActivation (SendOtp / Confirm) |
| 04-10 | Property onboarding | CreatePropertyDraft, SaveEquipmentManifest, AssignEquipment, AddRenovationLine, SubmitToHost, HostConfirmPricing, AssignOperationManager |
| 11-15 | Bulk import | LeaseExcel, RenovationExcel, RenovationSupplement, TenantDraftContracts, PropertyImagesZip |
| 16-23 | Tenant onboarding and contract | CreateDraftContract, CaptureRoomConditionAndMeterReading, HostApproveContractPrice, CreateDepositPayment, DepositPaymentWebhook, ContractOtpAndActivation, ExtendContract, TerminateOrCancelContract |
| 24-32 | Billing and payment | CreateUtilityBill, ManagerCreateUtilityInvoice, ManagerCreateRentInvoice, TenantPayInvoiceViaPayOS, ManagerVerifyManualPayment, InvoiceUnlockAndManagerPaymentQr, MeterOverridePasscode, DailyBillingSweep, SaveLockedMeterReading |
| 33-41 | Maintenance | SubmitRequest, ConfirmArrivalAndSendForInspection, Diagnose, ApproveOrRejectFault, ReportFaultAndAdminReview, TenantSelfRepairAndVerify, ChargeBeforeRepair, StartRepairHandoverComplete, CancelAndReschedule |
| 42-49 | Checkout | CreateCheckoutRequest, ApproveOrReject, SaveInspection, Settlement, RecordDepositRefund, CompleteCheckoutRequest, ForceSettleAndResolveRefundDispute, ContractExpiryCron |
| 50-52 | Extension, dispute, zone | ContractExtensionRequest, UtilityInvoiceDispute, ZoneManagerAssignment |
| 53-60 | Assets and support | EquipmentLifecycleAndQr, ManageRoom, SignInboundContract, DisableEnableAndPurgeProperty, OcrAndVisionAssist, TenantHandoverAcknowledge, ViewingLeadAndPublicBrowse, IssueInvoiceFromPendingCharges |
| 61-66 | Admin and host | UserAccountManagement, NotificationCenterAndPushToken, PricingConfigUpdate, HostMasterLeaseAndExpense, HostDashboardAndFinanceReports, ZoneManagement |

Re-render: `java -jar plantuml.jar -charset UTF-8 -tpng -DPLANTUML_LIMIT_SIZE=16384 *.puml`
(PlantUML names the PNG after the `@startuml` name; rename to the numbered file name afterwards.)
