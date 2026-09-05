package com.sep490.slms2026.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerAvailabilitySlotResponse {
    private Long requestId;
    private String requestCode;
    /** VISIT | REPAIR */
    private String type;
    private LocalDateTime start;
    private LocalDateTime end;
    private String propertyName;
    private String roomNumber;
}
