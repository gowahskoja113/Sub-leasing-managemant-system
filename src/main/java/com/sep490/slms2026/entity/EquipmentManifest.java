package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.EquipmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "equipment_manifests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentManifest implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id", nullable = false)
    private EquipmentCatalog catalog;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EquipmentStatus status = EquipmentStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private com.sep490.slms2026.enums.EquipmentSource source = com.sep490.slms2026.enums.EquipmentSource.INITIAL_HANDOVER;

    @Column(name = "price")
    private java.math.BigDecimal price = java.math.BigDecimal.ZERO;
}
