package com.sep490.slms2026.service;

import com.sep490.slms2026.entity.MaintenanceRequest;
import com.sep490.slms2026.entity.TenantInvoice;

import com.sep490.slms2026.dto.billing.InvoicePaymentContext;

public interface RealtimeEventService {

    String EVT_MAINTENANCE_CREATED = "MAINTENANCE_CREATED";
    String EVT_MAINTENANCE_APPROVED = "MAINTENANCE_APPROVED";
    String EVT_MAINTENANCE_REJECT_FAULT = "MAINTENANCE_REJECT_FAULT";
    String EVT_MAINTENANCE_FAULT_REPORTED = "MAINTENANCE_FAULT_REPORTED";
    String EVT_MAINTENANCE_ADMIN_REVIEWED = "MAINTENANCE_ADMIN_REVIEWED";
    String EVT_MAINTENANCE_SELF_REPAIR_SUBMITTED = "MAINTENANCE_SELF_REPAIR_SUBMITTED";
    String EVT_MAINTENANCE_VERIFY_REPAIR = "MAINTENANCE_VERIFY_REPAIR";
    String EVT_MAINTENANCE_COMPLETED = "MAINTENANCE_COMPLETED";
    String EVT_MAINTENANCE_CANCELLED_BY_TENANT = "MAINTENANCE_CANCELLED_BY_TENANT";
    String EVT_MAINTENANCE_CANCELLED_BY_MANAGER = "MAINTENANCE_CANCELLED_BY_MANAGER";

    void publishInvoicePaid(TenantInvoice invoice);

    void publishInvoicePaid(TenantInvoice invoice, InvoicePaymentContext context);

    /** Tiến độ dual-OTP xác nhận HĐ (tenant/manager đã verify chưa). */
    void publishContractConfirmProgress(com.sep490.slms2026.entity.TenantContract contract);

    /** HĐ vừa ACTIVE sau khi đủ 2 OTP. */
    void publishContractActivated(com.sep490.slms2026.entity.TenantContract contract);

    /**
     * Đẩy event bảo trì tới {@code /user/queue/maintenance} (cùng kết nối STOMP với billing).
     * Người nhận phụ thuộc {@code eventType}.
     */
    void publishMaintenanceEvent(MaintenanceRequest request, String eventType);
}
