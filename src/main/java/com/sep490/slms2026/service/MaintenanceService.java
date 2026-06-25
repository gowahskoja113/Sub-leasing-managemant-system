package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.MaintenanceRequestResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MaintenanceService {
    MaintenanceRequestResponse acknowledge(Long id, MaintenanceAcknowledgeRequest request);
    MaintenanceRequestResponse schedule(Long id, MaintenanceScheduleRequest request);
    MaintenanceRequestResponse confirmSchedule(Long id, MaintenanceConfirmScheduleRequest request);
    MaintenanceRequestResponse updateStatus(Long id, MaintenanceStatusRequest request);
    MaintenanceRequestResponse resolve(Long id, MaintenanceResolveRequest request);
    MaintenanceRequestResponse approve(Long id, MaintenanceApproveRequest request);
    MaintenanceRequestResponse confirm(Long id, MaintenanceConfirmRequest request);
    MaintenanceRequestResponse uploadPhotos(Long id, java.util.List<org.springframework.web.multipart.MultipartFile> files, String type);
    Page<MaintenanceRequestResponse> getRequests(Pageable pageable);
}
