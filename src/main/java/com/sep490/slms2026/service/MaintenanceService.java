package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.CreateMaintenanceRequest;
import com.sep490.slms2026.dto.request.ResolveMaintenanceRequest;
import com.sep490.slms2026.entity.MaintenanceRequest;
import com.sep490.slms2026.enums.MaintenanceStatus;

import java.util.UUID;

public interface MaintenanceService {
    MaintenanceRequest createRequest(CreateMaintenanceRequest dto, UUID tenantId);
    void assignRequest(UUID requestId, UUID managerId, UUID assignedBy);
    void updateStatus(UUID requestId, MaintenanceStatus newStatus, UUID changedBy);
    void resolveRequest(UUID requestId, ResolveMaintenanceRequest dto, UUID managerId);
}