package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "checkout_settlement_invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutSettlementInvoice implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkout_settlement_id", nullable = false)
    private CheckoutSettlement checkoutSettlement;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "invoice_code")
    private String invoiceCode;

    @Column(name = "invoice_type")
    private String invoiceType;

    @Column(nullable = false)
    private BigDecimal amount;
}
