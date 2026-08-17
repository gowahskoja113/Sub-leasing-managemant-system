package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class BillingRealtimeEvent {
    String event;
    Long invoiceId;
    String invoiceCode;
    String invoiceType;
    String cycleType;
    String status;
    Long propertyId;
    String propertyName;
    String roomNumber;
    Long contractId;
    UUID tenantUserId;
    String tenantName;
    Integer billingMonth;
    Integer billingYear;
    String billingPeriod;
    Long utilityInvoiceId;
    String paymentMethod;
    String transactionId;
    LocalDateTime paidAt;
    String collectionMode;
    String remittedByName;
    String payerName;
    String unlockedByAdminName;
}
