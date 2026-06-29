package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.TenantInvoiceType;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantPayment implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_invoice_id", nullable = false)
    private TenantInvoice tenantInvoice;

    @Column(name = "tenant_user_id", nullable = false)
    private UUID tenantUserId;

    @Column(name = "invoice_code", nullable = false)
    private String invoiceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false)
    private TenantInvoiceType invoiceType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String method;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "property_name")
    private String propertyName;

    @Column(name = "room_number")
    private String roomNumber;
}
