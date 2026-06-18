package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.MaintenanceCategory;
import com.sep490.slms2026.enums.MaintenancePriority;
import com.sep490.slms2026.enums.MaintenanceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
public class MaintenanceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String requestCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private User tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id")
    private Equipment equipment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaintenanceCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaintenancePriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaintenanceStatus status = MaintenanceStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_manager_id")
    private User assignedManager;

    private LocalDateTime scheduledDate;

    // --- Resolution fields (merged from MaintenanceResolution) ---
    @Column(name = "repair_cost")
    private Long repairCost;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // 1 Request có nhiều hình ảnh đính kèm
    @OneToMany(mappedBy = "maintenanceRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MaintenanceImage> images = new ArrayList<>();

    // 1 Request có nhiều lần đổi trạng thái (audit timeline)
    @OneToMany(mappedBy = "maintenanceRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MaintenanceHistory> statusHistories = new ArrayList<>();
}