package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.EquipmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "equipments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(nullable = false)
    private String name; // Tivi, Tủ lạnh, Máy lạnh...

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "purchase_price", nullable = false)
    private BigDecimal purchasePrice = BigDecimal.ZERO; // Bàn giao ban đầu = 0, mua thêm > 0

    @Column(name = "installation_date")
    private LocalDate installationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EquipmentStatus condition = EquipmentStatus.NEW;
}