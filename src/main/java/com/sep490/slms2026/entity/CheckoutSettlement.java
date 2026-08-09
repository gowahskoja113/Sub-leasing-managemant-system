package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "checkout_settlements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutSettlement implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkout_request_id", nullable = false)
    private CheckoutRequest checkoutRequest;

    @Column(name = "deposit_amount", nullable = false)
    private BigDecimal depositAmount;

    @Column(name = "unpaid_total", nullable = false)
    private BigDecimal unpaidTotal;

    @Column(name = "damage_total", nullable = false)
    private BigDecimal damageTotal;

    @Column(name = "adjustment_total", nullable = false)
    private BigDecimal adjustmentTotal;

    @Column(name = "refund_amount", nullable = false)
    private BigDecimal refundAmount;

    @Column(name = "extra_charge_amount", nullable = false)
    private BigDecimal extraChargeAmount;

    @Column(name = "extra_charge_invoice_id")
    private Long extraChargeInvoiceId;

    @Column(name = "refund_method")
    private String refundMethod;

    @Column(name = "refund_proof_url")
    private String refundProofUrl;

    @Column(name = "refund_paid_at")
    private LocalDateTime refundPaidAt;

    @Column(name = "refund_note", columnDefinition = "TEXT")
    private String refundNote;

    @OneToMany(mappedBy = "checkoutSettlement", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CheckoutSettlementInvoice> settlementInvoices = new ArrayList<>();

    @OneToMany(mappedBy = "checkoutSettlement", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CheckoutSettlementAdjustment> settlementAdjustments = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
