package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.MaintenanceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "maintenance_history")
@Getter
@Setter
public class MaintenanceHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maintenance_request_id", nullable = false)
    private MaintenanceRequest maintenanceRequest;

    @Enumerated(EnumType.STRING)
    private MaintenanceStatus oldStatus;

    @Enumerated(EnumType.STRING)
    private MaintenanceStatus newStatus;

    private UUID changedBy;

    @CreationTimestamp
    private LocalDateTime changedAt;
}