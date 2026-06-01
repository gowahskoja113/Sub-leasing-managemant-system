package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.MaintenanceResolveRequest;
import com.sep490.slms2026.dto.response.MaintenanceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface MaintenanceService {

    // Tenant gửi yêu cầu bảo trì (có thể từ QR scan hoặc tự gửi)
    MaintenanceResponse submitRequest(MaintenanceResponse dto, UUID tenantId);

    // Tenant xem danh sách request của mình
    Page<MaintenanceResponse> getMyRequests(UUID tenantId, String status, Pageable pageable);

    // Manager xem tất cả request trong vùng quản lý
    Page<MaintenanceResponse> getRequestsForManager(UUID managerId, Pageable pageable);

    // Manager/Staff cập nhật tiến độ và ghi lịch sử
    MaintenanceResponse resolveRequest(UUID requestId, MaintenanceResolveRequest resolveRequest, UUID performedById);

    // Xem chi tiết request (kèm history + photos)
    MaintenanceResponse getRequestDetail(UUID requestId, UUID userId);
}