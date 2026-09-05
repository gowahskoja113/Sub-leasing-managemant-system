package com.sep490.slms2026.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaintenanceRescheduleRepairRequest {
    private LocalDateTime repairAppointmentAt;
}
