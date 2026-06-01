package com.sep490.slms2026.dto.response;

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
    private UUID roomId;
    private String roomNumber;
    private String name;
    private String category;
    private String status;
    private LocalDate installedDate;
    private BigDecimal purchaseCost;
    private String description;
    private String qrCode;      // base64 hoặc URL ảnh QR
    private String qrPayload;   // link/deep-link nhúng trong QR
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}