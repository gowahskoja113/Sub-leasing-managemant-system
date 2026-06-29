package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.TenantInvoiceStatus;
import com.sep490.slms2026.enums.TenantInvoiceType;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantInvoice implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "tenant_user_id", nullable = false)
    private UUID tenantUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_contract_id", nullable = false)
    private TenantContract tenantContract;

    @Column(name = "utility_invoice_id", unique = true)
    private Long utilityInvoiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false)
    private TenantInvoiceType invoiceType;

    @Column(name = "property_name", nullable = false)
    private String propertyName;

    @Column(name = "room_number")
    private String roomNumber;

    @Column(name = "billing_month")
    private Integer billingMonth;

    @Column(name = "billing_year")
    private Integer billingYear;

    @Column(name = "billing_period")
    private String billingPeriod;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "late_fee", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal lateFee = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal grandTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TenantInvoiceStatus status = TenantInvoiceStatus.PENDING;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "kwh_used", precision = 19, scale = 4)
    private BigDecimal kwhUsed;

    @Column(name = "electricity_rate", precision = 19, scale = 4)
    private BigDecimal electricityRate;

    @Column(name = "m3_used", precision = 19, scale = 4)
    private BigDecimal m3Used;

    @Column(name = "water_rate", precision = 19, scale = 4)
    private BigDecimal waterRate;

    @Column(name = "payos_order_code")
    private Long payosOrderCode;

    @Column(name = "payos_checkout_url", length = 1024)
    private String payosCheckoutUrl;

    @Column(name = "payos_qr_code", columnDefinition = "TEXT")
    private String payosQrCode;
}
