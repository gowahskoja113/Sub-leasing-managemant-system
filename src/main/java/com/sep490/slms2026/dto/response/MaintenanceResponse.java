package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.MaintenancePriority;
import com.sep490.slms2026.enums.MaintenanceStatus;

import java.time.LocalDateTime;

public class MaintenanceResponse {
    private Long id;

    private String requestCode;

    private String roomName;

    private MaintenanceStatus status;

    private MaintenancePriority priority;

    private LocalDateTime createdAt;

    public void setId(Long id) {
    }

    public void setRequestCode(String requestCode) {
    }

    public void setStatus(MaintenanceStatus status) {
    }

    public void setPriority(MaintenancePriority priority) {
    }

    public void setCreatedAt(LocalDateTime createdAt) {
    }

    public void setRoomName(Object o) {
    }
}
