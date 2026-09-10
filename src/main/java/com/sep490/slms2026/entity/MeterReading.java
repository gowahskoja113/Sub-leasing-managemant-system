package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.UtilityType;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "meter_readings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeterReading implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(name = "utility_type", nullable = false)
    private UtilityType utilityType;

    @Column(name = "period", nullable = false)
    private String period;

    @Column(name = "reading", nullable = false, precision = 19, scale = 4)
    private BigDecimal reading;

    /** Chỉ số đầu kỳ (prev) lúc quản lý chốt — dùng khi auto-phát hành. */
    @Column(name = "prev_reading", precision = 19, scale = 4)
    private BigDecimal prevReading;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    /** Liên kết hoá đơn tiện ích khi đã phát hành; null = mới chốt, chưa ra hoá đơn. */
    @Column(name = "utility_invoice_id")
    private Long utilityInvoiceId;
}
