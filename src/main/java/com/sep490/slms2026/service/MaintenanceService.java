package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.CreateMaintenanceRequest;
import com.sep490.slms2026.dto.request.ResolveMaintenanceRequest;
import com.sep490.slms2026.dto.request.UpdateMaintenanceStatusRequest;
import com.sep490.slms2026.dto.response.MaintenanceDashboardResponse;
import com.sep490.slms2026.dto.response.MaintenanceRequestResponse;
import com.sep490.slms2026.enums.MaintenanceCategory;
import com.sep490.slms2026.enums.MaintenancePriority;
import com.sep490.slms2026.enums.MaintenanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.UUID;

public interface MaintenanceService {

    /** Tenant tạo yêu cầu bảo trì */
    MaintenanceRequestResponse createRequest(CreateMaintenanceRequest dto, UUID currentUserId);

    /** Tenant xem danh sách request của mình (paging, filter status) */
    Page<MaintenanceRequestResponse> getMyRequests(UUID tenantUserId, MaintenanceStatus status, Pageable pageable);

    /** Xem chi tiết 1 request (phân quyền tenant/OM/admin) */
    MaintenanceRequestResponse getRequestById(Long id, UUID currentUserId);

    /** OM/Admin xem tất cả request (filter nhiều field, paging) */
    Page<MaintenanceRequestResponse> getAllRequests(
            MaintenanceStatus status, MaintenancePriority priority,
            Long propertyId, Long roomId, MaintenanceCategory category,
            Pageable pageable, UUID currentUserId);

    /** OM đổi trạng thái (PENDING→IN_PROGRESS, PENDING→CANCELLED, IN_PROGRESS→CANCELLED) */
    MaintenanceRequestResponse updateStatus(Long id, UpdateMaintenanceStatusRequest dto, UUID currentUserId);

    /** OM hoàn tất sửa chữa */
    MaintenanceRequestResponse resolveRequest(Long id, ResolveMaintenanceRequest dto, UUID currentUserId);

    /** Admin/Owner dashboard tổng hợp */
    MaintenanceDashboardResponse getDashboard(Long propertyId, LocalDateTime from, LocalDateTime to);
}