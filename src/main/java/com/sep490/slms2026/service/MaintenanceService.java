package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.MaintenanceDashboardResponse;
import com.sep490.slms2026.dto.response.MaintenanceRequestResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MaintenanceService {

    Page<MaintenanceRequestResponse> getRequests(
            String status, String priority, String category, Long propertyId, Long roomId, Pageable pageable);

    MaintenanceRequestResponse createRequest(MaintenanceCreateRequest request);

    Page<MaintenanceRequestResponse> getMyRequests(Pageable pageable);

    MaintenanceRequestResponse getRequestById(Long id);

    MaintenanceDashboardResponse getDashboardStats();

    List<MaintenanceRequestResponse> getEquipmentMaintenanceHistory(Long equipmentId);

    /** Manager duyệt yêu cầu: PENDING → APPROVED */
    MaintenanceRequestResponse approve(Long id, MaintenanceApproveRequest request);

    /** Manager báo sửa xong: APPROVED → WAITING_TENANT_CONFIRM */
    MaintenanceRequestResponse complete(Long id, MaintenanceCompleteRequest request);

    /** Tenant xác nhận đã sửa xong: WAITING_TENANT_CONFIRM → CLOSED */
    MaintenanceRequestResponse confirm(Long id, MaintenanceConfirmRequest request);

    /** Tenant từ chối kết quả sửa (lý do + ảnh): WAITING_TENANT_CONFIRM → REJECTED */
    MaintenanceRequestResponse reject(Long id, MaintenanceRejectRequest request, List<MultipartFile> files);

    /**
     * Manager xem xét reject của tenant.
     * approve=true  → quay lại APPROVED (sửa lại)
     * approve=false → giữ / đưa lại WAITING_TENANT_CONFIRM (manager không đồng ý reopen)
     */
    MaintenanceRequestResponse reviewReject(Long id, MaintenanceApproveRequest request);

    /** Manager hủy yêu cầu */
    MaintenanceRequestResponse cancel(Long id, String reason);

    MaintenanceRequestResponse uploadPhotos(Long id, List<MultipartFile> files, String type);

    /** Auto-confirm các ticket chờ tenant quá hạn (cron). */
    int autoConfirmOverdue();
}
