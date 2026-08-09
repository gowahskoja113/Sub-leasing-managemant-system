package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "checkout_settlement_adjustments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutSettlementAdjustment implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkout_settlement_id", nullable = false)
    private CheckoutSettlement checkoutSettlement;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private BigDecimal amount;
}
