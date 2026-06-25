package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.EquipmentStatus;
import com.sep490.slms2026.enums.HouseArea;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

/**
 * Thiết bị chủ nhà bàn giao — chỉ hiển thị, không gán vận hành / khấu hao.
 */
@Entity
@Table(name = "handover_equipments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HandoverEquipment implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_id", nullable = false)
    private EquipmentCatalog catalog;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "room_number")
    private String roomNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "house_area")
    private HouseArea houseArea;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EquipmentStatus status;

    @Column(nullable = false)
    private Integer quantity;

    @Column(columnDefinition = "TEXT")
    private String note;
}
