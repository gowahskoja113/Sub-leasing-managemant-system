package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.ViewingLeadStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ViewingLeadResponse {
    private Long id;
    private String customerName;
    private String customerPhone;
    private String note;
    private ViewingLeadStatus status;
    private UUID assignedManagerId;
    private String assignedManagerName;
    private UUID createdBy;
    private String createdByName;
    private UUID linkedUserId;
    private LocalDateTime preferredViewingAt;
    private LocalDateTime scheduledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ViewingLeadPropertyResponse> properties;
    private int propertyCount;
}
