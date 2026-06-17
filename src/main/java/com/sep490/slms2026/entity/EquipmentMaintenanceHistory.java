package com.sep490.slms2026.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "equipment_maintenance_histories")
@Getter
@Setter
public class EquipmentMaintenanceHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Thay thế UUID rời rạc bằng quan hệ @ManyToOne
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maintenance_request_id", nullable = false)
    private MaintenanceRequest maintenanceRequest;

    private LocalDateTime maintenanceDate;

    @Column(columnDefinition = "TEXT")
    private String note;
}