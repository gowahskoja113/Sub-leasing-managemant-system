package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.PayosOrderPurpose;
import com.sep490.slms2026.enums.PayosOrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_invoice_payos_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantInvoicePayosOrder implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private TenantInvoice invoice;

    @Column(name = "order_code", nullable = false, unique = true)
    private Long orderCode;

    @Column(name = "checkout_url", length = 1024)
    private String checkoutUrl;

    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_by")
    private UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PayosOrderPurpose purpose;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PayosOrderStatus status = PayosOrderStatus.ACTIVE;

    @Column(name = "payer_name")
    private String payerName;

    @Column(name = "payer_phone", length = 20)
    private String payerPhone;

    @Column(name = "unlocked_by_admin")
    private UUID unlockedByAdmin;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
