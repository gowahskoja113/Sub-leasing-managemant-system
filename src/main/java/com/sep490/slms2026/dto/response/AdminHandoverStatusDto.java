package com.sep490.slms2026.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminHandoverStatusDto {
    private Long propertyId;
    private String propertyName;
    private String propertyStatus;
    private String operationManagerName;
    private LocalDateTime managerAcceptedAt;
    private Integer totalRooms;
    private Integer roomsHandedOver;
    private List<RoomHandover> rooms;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoomHandover {
        private String roomNumber;
        private String tenantName;
        private String contractStatus;
        private LocalDate moveInDate;
        private LocalDateTime activatedAt;
        private Integer conditionPhotoCount;
        private Boolean hasMeterReadings;
    }
}
