package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Column(unique = true)
    private String requestCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = true)
    private Room room;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", insertable = false, updatable = false)
    private Equipment equipment;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_manager_id")
    private User assignedManager;

    private LocalDateTime scheduledDate;

    // We keep main's category and priority types to ensure main code compiles
    private String category;
    
    private String priority;

    @Enumerated(EnumType.STRING)
    private MaintenanceStatus status = MaintenanceStatus.PENDING;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // New Fields from specs (main)
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
    
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    private Integer reopenCount = 0;
    
    private String technicianId; // reference to technician

    @Enumerated(EnumType.STRING)
    private CostPaidBy costPaidBy = CostPaidBy.HOST;

    @Enumerated(EnumType.STRING)
    private DamageCause cause;

    // repairCost is BigDecimal in main, Long in maintenance. We keep main's BigDecimal.
    private BigDecimal repairCost;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;
    
    // Images
    @Column(name = "before_image_urls", columnDefinition = "TEXT")
    private String beforeImageUrls;

    @Column(name = "after_image_urls", columnDefinition = "TEXT")
    private String afterImageUrls;

    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    // 1 Request có nhiều hình ảnh đính kèm
    @OneToMany(mappedBy = "maintenanceRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MaintenanceImage> images = new ArrayList<>();

    // 1 Request có nhiều lần đổi trạng thái (audit timeline)
    @OneToMany(mappedBy = "maintenanceRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MaintenanceHistory> statusHistories = new ArrayList<>();
    
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
