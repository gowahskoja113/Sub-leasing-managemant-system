package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.MaintenanceRequestDto;
import com.sep490.slms2026.dto.request.MaintenanceResolveRequest;
import com.sep490.slms2026.dto.response.MaintenanceResponse;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.MaintenancePriority;
import com.sep490.slms2026.enums.MaintenanceStatus;
import com.sep490.slms2026.enums.PhotoType;
import com.sep490.slms2026.mapper.MaintenanceMapper;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceServiceImpl implements MaintenanceService {

    private final MaintenanceRepository maintenanceRequestRepository;
    private final MaintenanceHistoryRepository maintenanceHistoryRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final MaintenanceMapper maintenanceMapper;

    @Override
    @Transactional
    public MaintenanceResponse submitRequest(MaintenanceRequestDto dto, UUID tenantId) {
        User tenant = userRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản tenant!"));

        MaintenanceRequestDto request = new MaintenanceRequestDto();
        request.setTenant(tenant);
        request.setCategory(dto.getCategory());
        request.setDescription(dto.getDescription());
        request.setStatus(MaintenanceStatus.PENDING);

        // Parse priority — mặc định NORMAL nếu không hợp lệ
        try {
            request.setPriority(MaintenancePriority.valueOf(
                    dto.getPriority() != null ? dto.getPriority().toUpperCase() : "NORMAL"));
        } catch (IllegalArgumentException e) {
            request.setPriority(MaintenancePriority.NORMAL);
        }

        // Resolve equipment từ equipmentId hoặc từ qrPayload
        if (dto.getEquipmentId() != null) {
            Equipment equipment = equipmentRepository.findById(dto.getEquipmentId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thiết bị!"));
            request.setEquipment(equipment);
        } else if (dto.getQrPayload() != null && !dto.getQrPayload().isBlank()) {
            equipmentRepository.findByQrPayload(dto.getQrPayload())
                    .ifPresent(request::setEquipment);
        }

        // Đính kèm ảnh BEFORE
        if (dto.getPhotoUrls() != null && !dto.getPhotoUrls().isEmpty()) {
            for (String url : dto.getPhotoUrls()) {
                MaintenancePhoto photo = new MaintenancePhoto();
                photo.setMaintenanceRequest(request);
                photo.setPhotoUrl(url);
                photo.setPhotoType(PhotoType.BEFORE);
                request.getPhotos().add(photo);
            }
        }

        MaintenanceRequestDto saved = maintenanceRequestRepository.save(request);

        // Ghi lịch sử: PENDING — lần đầu tạo
        writeHistory(saved, tenant, MaintenanceStatus.PENDING, "Tenant gửi yêu cầu bảo trì.", null);

        return maintenanceMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MaintenanceResponse> getMyRequests(UUID tenantId, String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            try {
                MaintenanceStatus ms = MaintenanceStatus.valueOf(status.toUpperCase());
                return maintenanceRequestRepository.findAllByTenantIdAndStatus(tenantId, ms, pageable)
                        .map(maintenanceMapper::toResponse);
            } catch (IllegalArgumentException ignored) {}
        }
        return maintenanceRequestRepository.findAllByTenantId(tenantId, pageable)
                .map(maintenanceMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MaintenanceResponse> getRequestsForManager(UUID managerId, Pageable pageable) {
        return maintenanceRequestRepository.findAllByManagerZones(managerId, pageable)
                .map(maintenanceMapper::toResponse);
    }

    @Override
    @Transactional
    public MaintenanceResponse resolveRequest(UUID requestId,
                                                     MaintenanceResolveRequest resolveDto,
                                                     UUID performedById) {
        MaintenanceRequestDto request = maintenanceRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu bảo trì!"));

        User performer = userRepository.findById(performedById)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản xử lý!"));

        MaintenanceStatus newStatus;
        try {
            newStatus = MaintenanceStatus.valueOf(resolveDto.getStatus().toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException("Trạng thái không hợp lệ: " + resolveDto.getStatus());
        }

        // Cập nhật trạng thái request
        request.setStatus(newStatus);
        if (resolveDto.getCost() != null) {
            request.setRepairCost(resolveDto.getCost());
        }
        if (newStatus == MaintenanceStatus.RESOLVED) {
            request.setResolvedAt(LocalDateTime.now());
        }

        // Đính kèm ảnh AFTER nếu có
        if (resolveDto.getAfterPhotoUrls() != null && !resolveDto.getAfterPhotoUrls().isEmpty()) {
            for (String url : resolveDto.getAfterPhotoUrls()) {
                MaintenancePhoto photo = new MaintenancePhoto();
                photo.setMaintenanceRequest(request);
                photo.setPhotoUrl(url);
                photo.setPhotoType(PhotoType.AFTER);
                request.getPhotos().add(photo);
            }
        }

        MaintenanceRequestDto saved = maintenanceRequestRepository.save(request);

        // Ghi lịch sử bảo trì
        writeHistory(saved, performer, newStatus, resolveDto.getActionTaken(), resolveDto.getCost());

        return maintenanceMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public MaintenanceResponse getRequestDetail(UUID requestId, UUID userId) {
        MaintenanceRequestDto request = maintenanceRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu bảo trì!"));
        return maintenanceMapper.toResponse(request);
    }

    // ─── Helper ghi lịch sử ────────────────────────────────────────────────────
    private void writeHistory(MaintenanceRequestDto request, User performer,
                              MaintenanceStatus status, String actionTaken,
                              java.math.BigDecimal cost) {
        MaintenanceHistory history = new MaintenanceHistory();
        history.setMaintenanceRequest(request);
        history.setPerformedBy(performer);
        history.setStatusChangedTo(status);
        history.setActionTaken(actionTaken);
        history.setCost(cost);
        maintenanceHistoryRepository.save(history);
    }
}