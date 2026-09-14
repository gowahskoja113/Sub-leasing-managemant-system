package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.PricingCapitalItemKind;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pricing_capital_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingCapitalItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_id", nullable = false)
    private Long propertyId;

    @Column(name = "pricing_version", nullable = false)
    private Integer pricingVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 30, nullable = false)
    private PricingCapitalItemKind kind;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "house_area")
    private Boolean houseArea;

    @Column(name = "amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "months", nullable = false)
    private Integer months;

    @Column(name = "monthly_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal monthlyAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
