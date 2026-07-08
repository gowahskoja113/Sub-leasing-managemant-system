package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.EquipmentOperationalStatus;
import com.sep490.slms2026.enums.EquipmentSource;
import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.enums.HouseArea;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "equipments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Equipment implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id", nullable = false)
    private EquipmentCatalog catalog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manifest_id")
    private EquipmentManifest manifest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "renovation_session_id")
    private RenovationSession renovationSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_status")
    @Builder.Default
    private EquipmentOperationalStatus operationalStatus = EquipmentOperationalStatus.ACTIVE;

    @Column(name = "disabled_at")
    private LocalDateTime disabledAt;

    @Column(name = "disabled_reason")
    private String disabledReason;

    @Column(name = "disabled_by_contract_id")
    private Long disabledByContractId;

    @Enumerated(EnumType.STRING)
    @Column(name = "house_area")
    private HouseArea houseArea;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EquipmentSource source = EquipmentSource.INITIAL_HANDOVER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EquipmentStatus status = EquipmentStatus.NEW;

    @Column(name = "price")
    private java.math.BigDecimal price = java.math.BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String note;

    // --- New fields per Maintenance_BE_Contract ---
    @Column(name = "equipment_name")
    private String equipmentName;

    @Column(name = "category")
    private String equipmentCategory;

    @Column(name = "installation_date")
    private LocalDate installationDate;

    @Column(name = "warranty_expired_date")
    private LocalDate warrantyExpiredDate;

    @Column(name = "maintenance_count", nullable = false)
    @Builder.Default
    private int maintenanceCount = 0;

    @Column(name = "last_maintenance_date")
    private LocalDateTime lastMaintenanceDate;

    // 1 Thiết bị có thể tạo yêu cầu sửa chữa nhiều lần
    @OneToMany(mappedBy = "equipment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<MaintenanceRequest> maintenanceRequests = new ArrayList<>();

    // 1 Thiết bị có nhiều lần ghi nhận lịch sử bảo trì
    @OneToMany(mappedBy = "equipment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<EquipmentMaintenanceHistory> maintenanceHistories = new ArrayList<>();

    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    @Column(name = "warranty_start_date")
    private LocalDate warrantyStartDate;

    @Column(name = "warranty_end_date")
    private LocalDate warrantyEndDate;

    @Column(name = "recommend_replacement")
    @Builder.Default
    private Boolean recommendReplacement = false;

    @Column(name = "qr_code", unique = true)
    private String qrCode;
}
