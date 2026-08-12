package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class BillingRealtimeEvent {
    String event;
    Long invoiceId;
    String invoiceCode;
    String invoiceType;
    String status;
    String propertyName;
    String roomNumber;
    String tenantName;
    String paymentMethod;
    String transactionId;
    LocalDateTime paidAt;
}
