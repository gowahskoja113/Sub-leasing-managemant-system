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
    private String status;
    private BigDecimal price;
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
}
