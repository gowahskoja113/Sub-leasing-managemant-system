package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.InvoiceDisputeReason;
import com.sep490.slms2026.enums.InvoiceDisputeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoice_disputes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceDispute implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Spec: invoice_id → utility_invoices */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private UtilityInvoice utilityInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_invoice_id", nullable = false)
    private TenantInvoice tenantInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_contract_id", nullable = false)
    private TenantContract tenantContract;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceDisputeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InvoiceDisputeReason reason;

    @Column(nullable = false, length = 500)
    private String note;

    @ElementCollection
    @CollectionTable(name = "invoice_dispute_photos", joinColumns = @JoinColumn(name = "dispute_id"))
    @Column(name = "photo_url", length = 1024)
    @Builder.Default
    private List<String> photos = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replacement_invoice_id")
    private UtilityInvoice replacementInvoice;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = InvoiceDisputeStatus.OPEN;
        }
    }
}
