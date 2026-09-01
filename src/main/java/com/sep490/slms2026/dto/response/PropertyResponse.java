package com.sep490.slms2026.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class PropertyResponse {
    private Long id;
    private String propertyCode;
    private String propertyName;
    private String shortAddress;
    private String fullAddress;
    private String descriptions;
    private UUID zoneId;
    private String zoneName;
    private Double areaSize;
    private Double length;
    private Double width;
    private Boolean wholeHouse;
    private Boolean hasRenovation;
    private Integer totalFloor;
    private Integer totalRooms;
    /** Số phòng thật đang có (đếm bản ghi rooms, khác totalRooms khai báo). */
    private Integer roomCount;
    /** Phòng AVAILABLE chưa bị HĐ DRAFT/PENDING giữ chỗ. */
    private Integer availableRooms;
    private Integer rentedRooms;
    private Integer maintenanceRooms;
    /** Phòng còn DRAFT — chưa mở cho thuê. */
    private Integer notOpenedRooms;
    private String status;
    private BigDecimal price;
    private BigDecimal listedPrice;
    private BigDecimal appliedPrice;
    private Boolean priceLocked;
    private Long createdBy;
    private UUID operationManagerId;
    /** Tên đầy đủ của Operation Manager — null nếu chưa gán */
    private String operationManagerName;
    private boolean renovationCompleted;
    private List<String> imageUrls;

    private List<HandoverEquipmentResponse> handoverEquipments;

    /** Đợt cải tạo đang có hiệu lực (status ACTIVE), null nếu chưa có */
    private RenovationSessionResponse activeRenovationSession;

    /** Lịch sử tất cả đợt cải tạo — mới nhất trước */
    private List<RenovationSessionResponse> renovationSessions;

    // true nếu BĐS còn cho thuê được
    private Boolean rentalAvailable;

    private BigDecimal electricityUnitPrice;
    private BigDecimal waterUnitPrice;

    /** Ngày HĐ với chủ nhà (InboundContract) — để FE chặn nhập ngày vào ở. */
    private java.time.LocalDate leaseStartDate;
    private java.time.LocalDate leaseEndDate;
}
