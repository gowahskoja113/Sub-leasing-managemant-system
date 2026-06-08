package com.sep490.slms2026.entity;
import com.sep490.slms2026.enums.PropertyStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Entity
@Table(name = "properties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Property implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_name", nullable = false)
    private String propertyName;

    @Column(nullable = false)
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @Column(name = "area_size")
    private Double areaSize;

    @Column(name = "is_whole_house", nullable = false)
    private Boolean wholeHouse;

    @Column(name = "total_rooms")
    private Integer totalRooms;

    @ElementCollection
    @CollectionTable(name = "property_images", joinColumns = @JoinColumn(name = "property_id"))
    @Column(name = "image_url")
    private List<String> imageUrls;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyStatus status = PropertyStatus.DRAFT;

    @Column(name = "managed_by", nullable = false)
    private Long managedBy;

    @Column(name = "descriptions", nullable = false)
    private String descriptions;

    // Chỉ dùng cho nhà nguyên căn (wholeHouse = true)
    @Column
    private BigDecimal price;

    @Column
    private BigDecimal deposit;

    @OneToOne(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private InboundContract inboundContract;

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Equipment> equipments;

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Renovation> renovations;

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MonthlyReading> utilityReadings;
}