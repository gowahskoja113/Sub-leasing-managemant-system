package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CreateMaintenanceRequest;
import com.sep490.slms2026.dto.request.ResolveMaintenanceRequest;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.MaintenanceStatus;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.service.MaintenanceService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MaintenanceServiceImpl implements MaintenanceService {

    private final MaintenanceRequestRepository requestRepository;
    private final MaintenanceHistoryRepository historyRepository;
    private final MaintenanceResolutionRepository resolutionRepository;
    private final MaintenanceImageRepository imageRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentMaintenanceHistoryRepository equipmentHistoryRepository;

    // Gọi sang các module khác (Mở comment khi bạn code xong các Service này)
    // private final ExpenseService expenseService;
    // private final NotificationService notificationService;

    @Override
    @Transactional
    public MaintenanceRequest createRequest(CreateMaintenanceRequest dto, UUID tenantId) {
        MaintenanceRequest request = new MaintenanceRequest();
        request.setRequestCode("MNT-" + System.currentTimeMillis());
        request.setTenantId(tenantId);
        request.setRoomId(dto.getRoomId());
        request.setPropertyId(dto.getPropertyId());
        request.setCategory(dto.getCategory());
        request.setPriority(dto.getPriority());
        request.setDescription(dto.getDescription());
        request.setStatus(MaintenanceStatus.PENDING);

        // Nối thẳng Equipment vào Request nếu DTO có truyền ID thiết bị (tùy chọn)
        /*
        if (dto.getEquipmentId() != null) {
            equipmentRepository.findById(dto.getEquipmentId()).ifPresent(request::setEquipment);
        }
        */

        request = requestRepository.save(request);

        // Lưu ảnh bằng Object JPA
        if (dto.getImageUrls() != null && !dto.getImageUrls().isEmpty()) {
            for (String url : dto.getImageUrls()) {
                MaintenanceImage img = new MaintenanceImage();
                img.setMaintenanceRequest(request); // SET BẰNG OBJECT THAY VÌ ID
                img.setImageUrl(url);
                imageRepository.save(img);
            }
        }

        // notificationService.sendNotification(NotificationEvent.NEW_MAINTENANCE_REQUEST, request.getPropertyId());
        return request;
    }

    @Override
    @Transactional
    public void assignRequest(UUID requestId, UUID managerId, UUID assignedBy) {
        MaintenanceRequest request = getRequest(requestId);
        MaintenanceStatus oldStatus = request.getStatus();

        request.setAssignedManagerId(managerId);
        request.setStatus(MaintenanceStatus.ASSIGNED);
        requestRepository.save(request);

        saveHistory(request, oldStatus, MaintenanceStatus.ASSIGNED, assignedBy); // TRUYỀN OBJECT

        // notificationService.sendNotification(NotificationEvent.REQUEST_ASSIGNED, managerId);
    }

    @Override
    @Transactional
    public void updateStatus(UUID requestId, MaintenanceStatus newStatus, UUID changedBy) {
        MaintenanceRequest request = getRequest(requestId);
        MaintenanceStatus oldStatus = request.getStatus();

        request.setStatus(newStatus);
        requestRepository.save(request);

        saveHistory(request, oldStatus, newStatus, changedBy); // TRUYỀN OBJECT

        // if (newStatus == MaintenanceStatus.IN_PROGRESS) {
        //     notificationService.sendNotification(NotificationEvent.REQUEST_IN_PROGRESS, request.getTenantId());
        // }
    }

    @Override
    @Transactional
    public void resolveRequest(UUID requestId, ResolveMaintenanceRequest dto, UUID managerId) {
        MaintenanceRequest request = getRequest(requestId);

        // 1. Cập nhật trạng thái
        updateStatus(requestId, MaintenanceStatus.RESOLVED, managerId);

        // 2. Lưu Resolution & Tính tổng phí
        MaintenanceResolution resolution = new MaintenanceResolution();
        resolution.setMaintenanceRequest(request); // SET BẰNG OBJECT THAY VÌ ID
        resolution.setResolutionNote(dto.getResolutionNote());
        resolution.setLaborCost(dto.getLaborCost());
        resolution.setMaterialCost(dto.getMaterialCost());
        resolution.setExternalServiceCost(dto.getExternalServiceCost());

        BigDecimal total = dto.getLaborCost().add(dto.getMaterialCost()).add(dto.getExternalServiceCost());
        resolution.setTotalCost(total);
        resolution.setCompletedAt(LocalDateTime.now());
        resolutionRepository.save(resolution);

        // 3. Financial Integration: Tạo Expense nếu có chi phí
        // if (total.compareTo(BigDecimal.ZERO) > 0) {
        //     expenseService.createExpense("MAINTENANCE", request.getPropertyId(), request.getRoomId(), requestId, total, "Chi phí sửa chữa " + request.getRequestCode());
        // }

        // 4. Update Equipment History (Dùng Object liên kết trực tiếp)
        if (dto.getEquipmentIds() != null) {
            // LƯU Ý: Khóa chính của Equipment là Long, hãy đảm bảo dto.getEquipmentIds() trả về List<Long>
            for (Long equipmentId : dto.getEquipmentIds()) {
                Equipment eq = equipmentRepository.findById(equipmentId).orElse(null);
                if (eq != null) {
                    EquipmentMaintenanceHistory eqHist = new EquipmentMaintenanceHistory();
                    eqHist.setEquipment(eq);               // SET BẰNG OBJECT
                    eqHist.setMaintenanceRequest(request); // SET BẰNG OBJECT
                    eqHist.setMaintenanceDate(LocalDateTime.now());
                    eqHist.setNote(dto.getResolutionNote());
                    equipmentHistoryRepository.save(eqHist);
                }
            }
        }

        // 5. Notify Tenant
        // notificationService.sendNotification(NotificationEvent.REQUEST_RESOLVED, request.getTenantId());
    }

    private MaintenanceRequest getRequest(UUID requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Maintenance Request not found"));
    }

    // Hàm lưu lịch sử đã được sửa để nhận trực tiếp Object MaintenanceRequest
    private void saveHistory(MaintenanceRequest request, MaintenanceStatus oldStatus, MaintenanceStatus newStatus, UUID changedBy) {
        MaintenanceHistory history = new MaintenanceHistory();
        history.setMaintenanceRequest(request); // LƯU THEO OBJECT MAPPER CỦA JPA
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(changedBy);
        historyRepository.save(history);
    }
}