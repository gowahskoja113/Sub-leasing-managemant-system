package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.TenantInvoiceType;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_payments", uniqueConstraints = {
    @UniqueConstraint(columnNames = "tenant_invoice_id")
})
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

    /** Nullable khi onboard chưa có account tenant — backfill lúc confirm HĐ. */
    @Column(name = "tenant_user_id")
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

    @Column(name = "collection_mode", length = 30)
    private String collectionMode;

    @Column(name = "remitted_by")
    private UUID remittedBy;

    @Column(name = "remit_method", length = 50)
    private String remitMethod;

    @Column(name = "payer_name")
    private String payerName;

    @Column(name = "payer_phone", length = 20)
    private String payerPhone;

    @Column(name = "facilitated_by")
    private UUID facilitatedBy;

    @Column(name = "unlocked_by_admin")
    private UUID unlockedByAdmin;

    @Column(name = "payment_note", columnDefinition = "TEXT")
    private String paymentNote;
}
