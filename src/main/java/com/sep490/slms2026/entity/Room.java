package com.sep490.slms2026.entity;

import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.enums.RoomType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "Room")
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "room_number", nullable = false)
    private String roomNumber; // Ví dụ: "101", "202" hoặc "Nguyên Căn"

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private BigDecimal deposit;

    @Column(nullable = false)
    private Double area;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomStatus status; // AVAILABLE, RENTED, MAINTENANCE

    @Column(name = "max_occupants")
    private Integer maxOccupants; // Số người ở tối đa trong 1 phòng/căn

    // Chỉ dùng cho nhà nguyên căn: Khai báo cấu trúc bên trong (vd: "3 Phòng ngủ, 1 Bếp, 2 WC")
    @Column(name = "structure_description", columnDefinition = "TEXT")
    private String structureDescription;

    // Tiền thu được từ phòng này (cộng dồn từ các hóa đơn)
    @Column(name = "total_revenue", nullable = false)
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    @Column(name = "image_urls", columnDefinition = "TEXT")
    private String imageUrls;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false)
    private RoomType roomType;

    // Nhiều Room thuộc về 1 Property
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;
}