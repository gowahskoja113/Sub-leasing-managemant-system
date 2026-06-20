package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.PropertyType;
import com.sep490.slms2026.enums.RoomStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "room_number", nullable = false)
    private String roomNumber;

    @Column(name = "floor")
    private Integer floor;

    @Column(nullable = true)
    private BigDecimal price;

    @Column(nullable = true)
    private BigDecimal deposit;

    @Column(nullable = false)
    private Double area;

    @Column(name = "max_occupants")
    private Integer maxOccupants;

    // Dùng cho wholehouse: mô tả cấu trúc bên trong ("3PN, 1 bếp, 2WC")
    @Column(name = "structure_description", columnDefinition = "TEXT")
    private String structureDescription;

    @Column(name = "image_urls", columnDefinition = "TEXT")
    private String imageUrls;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false)
    private PropertyType propertyType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomStatus status = RoomStatus.DRAFT;

    // Null nếu tòa dùng đồng hồ chung, có giá trị nếu phòng có đồng hồ riêng
    @Column(name = "electric_meter_code")
    private String electricMeterCode;

    @Column(name = "water_meter_code")
    private String waterMeterCode;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;
}