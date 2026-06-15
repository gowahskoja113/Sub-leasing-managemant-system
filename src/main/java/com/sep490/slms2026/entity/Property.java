package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.PropertyStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "Property")
public class Property {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String address;

    @Column(name = "is_whole_house", nullable = false)
    private Boolean wholeHouse;

    @Column(name = "total_rooms", nullable = false)
    private Integer totalRooms;

    @Column(name = "electricity_price", nullable = false)
    private BigDecimal electricityPrice;

    @Column(name = "water_price", nullable = false)
    private BigDecimal waterPrice;

    // Tiền thu được từ BĐS này (cộng dồn từ các hóa đơn đã thanh toán)
    @Column(name = "total_revenue", nullable = false)
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "image_urls", columnDefinition = "TEXT")
    private String imageUrls;

    @Column(name = "authorized_owner_name", nullable = false)
    private String authorizedOwnerName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Room> rooms = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "property_status", nullable = false)
    private PropertyStatus propertyStatus;
}