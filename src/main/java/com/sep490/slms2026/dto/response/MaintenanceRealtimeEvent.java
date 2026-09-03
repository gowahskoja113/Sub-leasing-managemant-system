package com.sep490.slms2026.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/**
 * Payload đẩy qua STOMP {@code /user/queue/maintenance}.
 * Đủ để FE refetch / patch UI, không mang toàn bộ entity.
 */
@Value
@Builder
public class MaintenanceRealtimeEvent {
    String event;
    Long requestId;
    String requestCode;
    String status;
    Long propertyId;
    String propertyName;
    Long roomId;
    String roomNumber;
    UUID tenantUserId;
    UUID assignedManagerId;
    Boolean adminApproved;
}
