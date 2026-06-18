package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.MaintenanceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maintenance_request_id", nullable = false)
    private MaintenanceRequest maintenanceRequest;

    @Enumerated(EnumType.STRING)
    private MaintenanceStatus oldStatus;

    @Enumerated(EnumType.STRING)
    private MaintenanceStatus newStatus;

    @Column(columnDefinition = "TEXT")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedByUser;

    @CreationTimestamp
    private LocalDateTime changedAt;
}