package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.UtilityType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

@Entity
@Table(name = "monthly_readings",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"property_id", "room_id", "utility_type", "billing_month"}
        ))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MonthlyReading implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    // null = wholehouse, có giá trị = nhà phòng
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(name = "utility_type", nullable = false)
    private UtilityType utilityType;        // ELECTRIC | WATER

    @Column(name = "billing_month", nullable = false)
    private YearMonth billingMonth;         // 2025-06

    @Column(name = "reading_start", nullable = false)
    private Integer readingStart;           // chỉ số đầu kỳ

    @Column(name = "reading_end", nullable = false)
    private Integer readingEnd;             // chỉ số cuối kỳ

    @Column(name = "units_used", nullable = false)
    private Integer unitsUsed;              // = end - start, tính khi save

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;           // giá/kWh hoặc giá/m³ tháng đó

    @Column(name = "amount_charged", nullable = false)
    private BigDecimal amountCharged;       // = unitsUsed × unitPrice, tính khi save

    @Column(name = "recorded_date", nullable = false)
    private LocalDate recordedDate;

    @PrePersist
    @PreUpdate
    void calculate() {
        this.unitsUsed = this.readingEnd - this.readingStart;
        this.amountCharged = BigDecimal.valueOf(this.unitsUsed)
                .multiply(this.unitPrice);
    }
}