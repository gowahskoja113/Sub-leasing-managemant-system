package com.sep490.slms2026.entity;
import com.sep490.slms2026.enums.PropertyStatus;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
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

    private String zone; // Khu vực/Quận/Huyện

    @Column(name = "area_size")
    private Double areaSize; // Diện tích căn nhà (m2)

    @Column(name = "is_whole_house", nullable = false)
    private Boolean wholeHouse = true;

    @Column(name = "total_rooms")
    private Integer totalRooms; // Bằng null hoặc chỉ số phòng dự kiến nếu wholeHouse = false

    @ElementCollection
    @CollectionTable(name = "property_images", joinColumns = @JoinColumn(name = "property_id"))
    @Column(name = "image_url")
    private List<String> imageUrls; // Danh sách hình ảnh của tòa nhà

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyStatus status = PropertyStatus.DRAFT; // DRAFT, ACTIVE, INACTIVE

    @Column(name = "managed_by", nullable = false)
    private Long managedBy; // ID của User/Staff quản lý tòa nhà này

    // Mối quan hệ 1-1 với Hợp đồng gốc
    @OneToOne(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private InboundContract inboundContract;

    // Danh sách thiết bị (Equipment ban đầu và mua thêm)
    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Equipment> equipments;

    // Chỉ số điện nước đầu vào (Lưu theo kỳ hoặc chỉ số bàn giao ban đầu)
    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<MonthlyReading> utilityReadings;
}