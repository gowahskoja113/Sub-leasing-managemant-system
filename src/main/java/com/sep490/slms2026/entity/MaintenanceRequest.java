package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    @JoinColumn(name = "tenant_contract_id")
    private TenantContract tenantContract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", insertable = false, updatable = false)
    private Equipment equipment;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String category;

    private String priority;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MaintenanceStatus status = MaintenanceStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_type")
    private MaintenanceFlowType flowType;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime acknowledgedAt;
    private LocalDateTime doneAt;
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "repair_description", columnDefinition = "TEXT")
    private String repairDescription;

    @Column(name = "before_image_urls", columnDefinition = "TEXT")
    private String beforeImageUrls;

    @Column(name = "after_image_urls", columnDefinition = "TEXT")
    private String afterImageUrls;

    @Column(name = "invoice_image_urls", columnDefinition = "TEXT")
    private String invoiceImageUrls;

    @Column(name = "invoice_vendor")
    private String invoiceVendor;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "invoice_amount")
    private BigDecimal invoiceAmount;

    @Column(name = "previous_request_id")
    private Long previousRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "damage_cause")
    private DamageCause damageCause;

    @Column(name = "fault_reason", columnDefinition = "TEXT")
    private String faultReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "fault_resolution_path")
    private FaultResolutionPath faultResolutionPath;

    @Column(name = "self_repair_deadline")
    private LocalDate selfRepairDeadline;

    @Column(name = "estimated_damage_amount")
    private BigDecimal estimatedDamageAmount;

    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @OneToMany(mappedBy = "maintenanceRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MaintenanceImage> images = new ArrayList<>();

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
