package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.PropertyStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Column(name = "floor_count")
    private Integer floorCount;

    @Column(name = "rooms_per_floor")
    private Integer roomsPerFloor;

    @Column(name = "is_whole_house")
    private Boolean wholeHouse;

    @Column(name = "has_renovation")
    private Boolean hasRenovation;

    @Column(name = "total_rooms")
    private Integer totalRooms;

    @ElementCollection
    @CollectionTable(name = "property_images", joinColumns = @JoinColumn(name = "property_id"))
    @Column(name = "image_url")
    private List<String> imageUrls;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyStatus status = PropertyStatus.DRAFT;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "operation_manager_id")
    private Long operationManagerId;

    @Column(name = "managed_by")
    private Long managedBy;

    @Column(name = "descriptions", nullable = false)
    private String descriptions;

    @Column
    private BigDecimal price;

    @Column(name = "renovation_start_date")
    private LocalDate renovationStartDate;

    @Column(name = "renovation_end_date")
    private LocalDate renovationEndDate;

    @Column(name = "renovation_completed", nullable = false)
    private boolean renovationCompleted = false;

    @Column(name = "submitted_to_host_at")
    private LocalDateTime submittedToHostAt;

    @Column(name = "host_contingency_percent")
    private BigDecimal hostContingencyPercent;

    @OneToOne(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private InboundContract inboundContract;

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Equipment> equipments;

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<EquipmentManifest> equipmentManifests;

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RenovationLine> renovationLines;

    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MonthlyReading> utilityReadings;
}
