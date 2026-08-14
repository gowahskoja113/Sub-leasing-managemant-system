package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.AssignZoneManagerRequest;
import com.sep490.slms2026.dto.response.ZoneAssignmentHistoryResponse;
import com.sep490.slms2026.dto.response.ZoneAssignmentResponse;
import com.sep490.slms2026.dto.response.ZoneHandoverResponse;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.ZoneAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZoneAssignmentServiceImpl implements ZoneAssignmentService {

    private final ZoneManagerRepository zoneManagerRepository;
    private final ZoneManagerHandoverRepository zoneManagerHandoverRepository;
    private final ZoneRepository zoneRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final TenantContractRepository tenantContractRepository;
    private final NotificationRepository notificationRepository;

    @Override
    public List<ZoneAssignmentResponse> getAllAssignments() {
        return zoneManagerRepository.findAll().stream().map(zm -> {
            Zone zone = zoneRepository.findById(zm.getZoneId()).orElse(null);
            User manager = userRepository.findById(zm.getManagerId()).orElse(null);
            User assignedBy = zm.getAssignedBy() != null ? userRepository.findById(zm.getAssignedBy()).orElse(null) : null;

            return ZoneAssignmentResponse.builder()
                    .zoneId(zm.getZoneId())
                    .zoneName(zone != null ? zone.getName() : null)
                    .managerId(zm.getManagerId())
                    .managerUsername(manager != null ? manager.getUsername() : null)
                    .managerFullName(manager != null ? manager.getFullName() : null)
                    .managerPhone(manager != null ? manager.getPhoneNumber() : null)
                    .assignedAt(zm.getAssignedAt())
                    .assignedBy(zm.getAssignedBy())
                    .assignedByUsername(assignedBy != null ? assignedBy.getUsername() : null)
                    .activeProperties(propertyRepository.findByOperationManagerId(zm.getManagerId()).size())
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ZoneHandoverResponse assignManager(UUID zoneId, AssignZoneManagerRequest request) {
        CustomUserDetails currentUser = SecurityUtils.requireCurrentUser();
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found: " + zoneId));
        User manager = userRepository.findById(request.getManagerId())
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found: " + request.getManagerId()));

        ZoneManager existingZm = zoneManagerRepository.findById(zoneId).orElse(null);
        UUID fromManagerId = existingZm != null ? existingZm.getManagerId() : null;

        if (fromManagerId != null && fromManagerId.equals(request.getManagerId())) {
            // Already assigned to this manager
            return getZoneHandovers(zoneId).stream().findFirst().orElse(null);
        }

        // Upsert zone_managers
        ZoneManager zm = existingZm != null ? existingZm : new ZoneManager();
        zm.setZoneId(zoneId);
        zm.setManagerId(request.getManagerId());
        zm.setAssignedBy(currentUser.getId());
        zm.setAssignedAt(LocalDateTime.now());
        zoneManagerRepository.save(zm);

        // Update properties
        List<PropertyStatus> validStatuses = List.of(
                PropertyStatus.PENDING_OPERATION_MANAGER,
                PropertyStatus.ACTIVE,
                PropertyStatus.RENTED,
                PropertyStatus.MAINTENANCE,
                PropertyStatus.INACTIVE
        );
        int affectedProperties = propertyRepository.updateOperationManagerByZoneId(request.getManagerId(), zoneId, validStatuses);

        // Update contracts
        int affectedContracts = tenantContractRepository.updateAssignedManagerByZoneId(manager, zoneId);

        // Create handover history
        ZoneManagerHandover handover = ZoneManagerHandover.builder()
                .zoneId(zoneId)
                .fromManagerId(fromManagerId)
                .toManagerId(request.getManagerId())
                .changedBy(currentUser.getId())
                .changedAt(LocalDateTime.now())
                .affectedProperties(affectedProperties)
                .affectedContracts(affectedContracts)
                .build();
        zoneManagerHandoverRepository.save(handover);

        // Send notifications
        sendHandoffNotifications(zone, fromManagerId, request.getManagerId(), affectedProperties, affectedContracts);

        return toHandoverResponse(handover);
    }

    @Override
    @Transactional
    public void removeManager(UUID zoneId) {
        CustomUserDetails currentUser = SecurityUtils.requireCurrentUser();
        ZoneManager existingZm = zoneManagerRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone manager assignment not found for zone: " + zoneId));

        UUID fromManagerId = existingZm.getManagerId();
        
        zoneManagerRepository.delete(existingZm);

        List<PropertyStatus> validStatuses = List.of(
                PropertyStatus.PENDING_OPERATION_MANAGER,
                PropertyStatus.ACTIVE,
                PropertyStatus.RENTED,
                PropertyStatus.MAINTENANCE,
                PropertyStatus.INACTIVE
        );
        int affectedProperties = propertyRepository.removeOperationManagerByZoneId(zoneId, validStatuses);
        int affectedContracts = tenantContractRepository.removeAssignedManagerByZoneId(zoneId);

        ZoneManagerHandover handover = ZoneManagerHandover.builder()
                .zoneId(zoneId)
                .fromManagerId(fromManagerId)
                .toManagerId(null)
                .changedBy(currentUser.getId())
                .changedAt(LocalDateTime.now())
                .affectedProperties(affectedProperties)
                .affectedContracts(affectedContracts)
                .build();
        zoneManagerHandoverRepository.save(handover);
    }

    @Override
    public List<ZoneHandoverResponse> getZoneHandovers(UUID zoneId) {
        return zoneManagerHandoverRepository.findByZoneIdOrderByChangedAtDesc(zoneId)
                .stream().map(this::toHandoverResponse).collect(Collectors.toList());
    }

    @Override
    public List<ZoneAssignmentHistoryResponse> getUserAssignmentHistory(UUID userId) {
        List<ZoneManagerHandover> handovers = zoneManagerHandoverRepository.findHistoryByUserId(userId);
        List<ZoneAssignmentHistoryResponse> history = new ArrayList<>();
        
        for (ZoneManagerHandover h : handovers) {
            Zone zone = zoneRepository.findById(h.getZoneId()).orElse(null);
            User changedBy = userRepository.findById(h.getChangedBy()).orElse(null);
            
            if (userId.equals(h.getToManagerId())) {
                history.add(ZoneAssignmentHistoryResponse.builder()
                        .zoneId(h.getZoneId())
                        .zoneName(zone != null ? zone.getName() : null)
                        .action("ASSIGNED")
                        .at(h.getChangedAt())
                        .byUserId(h.getChangedBy())
                        .byUsername(changedBy != null ? changedBy.getUsername() : null)
                        .properties(h.getAffectedProperties())
                        .build());
            }
            if (userId.equals(h.getFromManagerId())) {
                history.add(ZoneAssignmentHistoryResponse.builder()
                        .zoneId(h.getZoneId())
                        .zoneName(zone != null ? zone.getName() : null)
                        .action("REVOKED")
                        .at(h.getChangedAt())
                        .byUserId(h.getChangedBy())
                        .byUsername(changedBy != null ? changedBy.getUsername() : null)
                        .properties(h.getAffectedProperties())
                        .build());
            }
        }
        
        return history.stream()
                .sorted((a, b) -> b.getAt().compareTo(a.getAt()))
                .collect(Collectors.toList());
    }

    private ZoneHandoverResponse toHandoverResponse(ZoneManagerHandover h) {
        User fromManager = h.getFromManagerId() != null ? userRepository.findById(h.getFromManagerId()).orElse(null) : null;
        User toManager = h.getToManagerId() != null ? userRepository.findById(h.getToManagerId()).orElse(null) : null;
        User changedBy = userRepository.findById(h.getChangedBy()).orElse(null);

        return ZoneHandoverResponse.builder()
                .id(h.getId())
                .zoneId(h.getZoneId())
                .fromManagerId(h.getFromManagerId())
                .fromManagerUsername(fromManager != null ? fromManager.getUsername() : null)
                .toManagerId(h.getToManagerId())
                .toManagerUsername(toManager != null ? toManager.getUsername() : null)
                .changedBy(h.getChangedBy())
                .changedByUsername(changedBy != null ? changedBy.getUsername() : null)
                .changedAt(h.getChangedAt())
                .affectedProperties(h.getAffectedProperties())
                .affectedContracts(h.getAffectedContracts())
                .build();
    }

    private void sendHandoffNotifications(Zone zone, UUID fromManagerId, UUID toManagerId, int properties, int contracts) {
        // Basic notification logic according to requirements
        if (fromManagerId != null) {
            notificationRepository.save(Notification.builder()
                    .userId(fromManagerId)
                    .title("Bàn giao khu vực " + zone.getName())
                    .content("Đã bàn giao khu vực " + zone.getName() + " cho quản lý mới. Số nhà: " + properties + ", Số HĐ: " + contracts)
                    .type("ZONE_REVOKED")
                    .read(false)
                    .build());
        }

        if (toManagerId != null) {
            notificationRepository.save(Notification.builder()
                    .userId(toManagerId)
                    .title("Tiếp nhận khu vực " + zone.getName())
                    .content("Bạn tiếp nhận khu vực " + zone.getName() + ". Vui lòng kiểm tra danh sách nhà và hợp đồng.")
                    .type("ZONE_ASSIGNED")
                    .read(false)
                    .build());
        }

        // Ideally, we would also notify host and tenants, but keeping it simple for now as it meets core requirements.
    }
}
