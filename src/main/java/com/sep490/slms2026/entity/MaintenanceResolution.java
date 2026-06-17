package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "maintenance_resolution")
@Getter
@Setter
public class MaintenanceResolution {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maintenance_request_id", nullable = false)
    private MaintenanceRequest maintenanceRequest;

    @Column(columnDefinition = "TEXT")
    private String resolutionNote;

    private BigDecimal laborCost = BigDecimal.ZERO;
    private BigDecimal materialCost = BigDecimal.ZERO;
    private BigDecimal externalServiceCost = BigDecimal.ZERO;
    private BigDecimal totalCost = BigDecimal.ZERO;

    private LocalDateTime completedAt;
}