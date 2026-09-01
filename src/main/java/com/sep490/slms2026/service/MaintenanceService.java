package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.MaintenanceDashboardResponse;
import com.sep490.slms2026.dto.response.MaintenanceRequestResponse;
import com.sep490.slms2026.dto.response.OutstandingDamageResponse;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
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

    /** Manager check OK → IN_REPAIR (Luồng A). */
    MaintenanceRequestResponse approve(Long id, MaintenanceApproveRequest request);

    /** Manager reject lỗi tenant → TENANT_FAULT / PENDING_TENANT_REPAIR (Luồng B). */
    MaintenanceRequestResponse rejectFault(Long id, MaintenanceRejectFaultRequest request);

    /** Manager báo lỗi do khách — chờ admin duyệt (không rẽ nhánh sửa/hoá đơn). */
    MaintenanceRequestResponse reportFault(Long id, MaintenanceReportFaultRequest request);

    /** Admin duyệt / không duyệt báo lỗi do khách. */
    MaintenanceRequestResponse adminReviewFault(Long id, MaintenanceAdminReviewRequest request);

    /** Tenant upload bằng chứng đã tự sửa. */
    MaintenanceRequestResponse submitSelfRepair(Long id, MaintenanceSubmitSelfRepairRequest request,
                                                List<MultipartFile> files);

    /** Manager verify tenant đã tự sửa. */
    MaintenanceRequestResponse verifyRepair(Long id, MaintenanceVerifyRepairRequest request);

    /** Manager hoàn tất → CLOSED + notify. */
    MaintenanceRequestResponse complete(Long id, MaintenanceCompleteRequest request);

    MaintenanceRequestResponse cancel(Long id, String reason);

    MaintenanceRequestResponse uploadPhotos(Long id, List<MultipartFile> files, String type);

    List<OutstandingDamageResponse> getOutstandingDamages(Long propertyId, Long tenantContractId);

    /** Cron: quá hạn tự sửa → OUTSTANDING_DAMAGE. */
    int processOverdueSelfRepairs();

    /** Đánh dấu outstanding damage đã xử lý tại checkout. */
    void markOutstandingDamageResolved(Long maintenanceRequestId, Long checkoutDamageItemId, BigDecimal actualAmount);
}
