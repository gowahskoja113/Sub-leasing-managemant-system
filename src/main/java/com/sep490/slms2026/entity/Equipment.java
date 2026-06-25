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

    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    @Column(name = "warranty_start_date")
    private LocalDate warrantyStartDate;

    @Column(name = "warranty_end_date")
    private LocalDate warrantyEndDate;

    @Column(name = "recommend_replacement")
    @Builder.Default
    private boolean recommendReplacement = false;
}
