package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.*;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "maintenance_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceRequest implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = true)
    private Room room;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String category;
    private String priority;

    @Enumerated(EnumType.STRING)
    private MaintenanceStatus status = MaintenanceStatus.PENDING;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // New Fields from specs
    private LocalDateTime acknowledgedAt;

    @Column(name = "scheduled_slots", columnDefinition = "TEXT")
    private String scheduledSlots; // comma-separated or JSON

    private String confirmedSlot;
    
    @Column(columnDefinition = "TEXT")
    private String onHoldReason;

    @Enumerated(EnumType.STRING)
    private ApprovalStatus approvalStatus = ApprovalStatus.NONE;

    private LocalDateTime doneAt;
    private LocalDateTime tenantConfirmedAt;

    private Integer reopenCount = 0;
    
    private String technicianId; // reference to technician

    @Enumerated(EnumType.STRING)
    private CostPaidBy costPaidBy = CostPaidBy.HOST;

    @Enumerated(EnumType.STRING)
    private DamageCause cause;

    private BigDecimal repairCost;

    @Column(columnDefinition = "TEXT")
    private String resolutionNote;
    
    // Images
    @Column(name = "before_image_urls", columnDefinition = "TEXT")
    private String beforeImageUrls;

    @Column(name = "after_image_urls", columnDefinition = "TEXT")
    private String afterImageUrls;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
