package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.EquipmentStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class EquipmentResponse {

    private UUID id;
    private String name;
    private String category;
    private EquipmentStatus status;
    private LocalDate installedDate;
    private BigDecimal purchaseCost;
    private String description;
    private String qrCode;
    private String qrPayload;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Thông tin phòng được gán (nullable)
    private UUID roomId;
    private String roomNumber;

    // Thông tin property (luôn có - lấy qua room hoặc trực tiếp)
    private UUID propertyId;
    private String propertyTitle;
    private String propertyAddress;

    // Loại gán: "ROOM" hoặc "WHOLE_HOUSE"
    private String assignmentType;
}