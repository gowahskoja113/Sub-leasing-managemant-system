package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.AssignZoneManagerRequest;
import com.sep490.slms2026.dto.request.ManagerTransferRequest;
import com.sep490.slms2026.dto.response.IdleManagerResponse;
import com.sep490.slms2026.dto.response.ZoneAssignmentHistoryResponse;
import com.sep490.slms2026.dto.response.ZoneAssignmentResponse;
import com.sep490.slms2026.dto.response.ZoneHandoverResponse;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.PropertyOnboardingService;
import com.sep490.slms2026.service.UserPushTokenService;
import com.sep490.slms2026.service.ZoneAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final UserPushTokenService userPushTokenService;
    private final PropertyOnboardingService propertyOnboardingService;

    private static final DateTimeFormatter DATE_VN = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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

        // Từng nhà qua assignOperationManager — không JPQL bulk ACTIVE (bỏ sót mở phòng / managerAcceptedAt).
        int affectedProperties = propertyOnboardingService.applyZoneOperationManager(
                zoneId, request.getManagerId());

        // Update contracts
        List<TenantContract> affected = tenantContractRepository.findActiveAndPendingByZoneId(zoneId);
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
        sendHandoffNotifications(zone, fromManagerId, request.getManagerId(), affectedProperties, affectedContracts, affected);

        return toHandoverResponse(handover);
    }

    @Override
    @Transactional
    public void removeManager(UUID zoneId) {
        assertNoLiveContracts(zoneId);
        doRemoveManager(zoneId);
    }

    /** Nhả khu vực khi bàn giao — không chặn HĐ còn sống (host đã xác nhận bảng trước/sau). */
    private void doRemoveManager(UUID zoneId) {
        CustomUserDetails currentUser = SecurityUtils.requireCurrentUser();
        ZoneManager existingZm = zoneManagerRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phân công quản lý cho khu vực này"));

        UUID fromManagerId = existingZm.getManagerId();
        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found: " + zoneId));

        zoneManagerRepository.delete(existingZm);

        // ACTIVE → PENDING_OPERATION_MANAGER + phòng trống AVAILABLE → DRAFT (không bulk JPQL).
        int affectedProperties = propertyOnboardingService.releaseZoneOperationManager(zoneId);
        List<TenantContract> affected = tenantContractRepository.findActiveAndPendingByZoneId(zoneId);
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

        sendHandoffNotifications(zone, fromManagerId, null, affectedProperties, affectedContracts, affected);
    }

    @Override
    @Transactional
    public ZoneHandoverResponse transferManager(ManagerTransferRequest request) {
        AssignZoneManagerRequest assign = new AssignZoneManagerRequest();
        assign.setManagerId(request.getManagerId());
        ZoneHandoverResponse assigned = assignManager(request.getToZoneId(), assign);

        if (request.getReleaseZoneIds() != null) {
            for (UUID releaseZoneId : request.getReleaseZoneIds()) {
                if (releaseZoneId == null || releaseZoneId.equals(request.getToZoneId())) {
                    continue;
                }
                ZoneManager zm = zoneManagerRepository.findById(releaseZoneId).orElse(null);
                if (zm == null || !request.getManagerId().equals(zm.getManagerId())) {
                    continue;
                }
                doRemoveManager(releaseZoneId);
            }
        }
        return assigned;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IdleManagerResponse> listIdleManagers() {
        Set<UUID> busy = zoneManagerRepository.findAll().stream()
                .map(ZoneManager::getManagerId)
                .collect(Collectors.toCollection(HashSet::new));
        return userRepository.findByRoleAndStatus(Role.ROLE_MANAGER, UserStatus.ACTIVE).stream()
                .filter(u -> !busy.contains(u.getId()))
                .map(u -> IdleManagerResponse.builder()
                        .id(u.getId())
                        .username(u.getUsername())
                        .fullName(u.getFullName())
                        .phoneNumber(u.getPhoneNumber())
                        .status(u.getStatus())
                        .zoneCount(0)
                        .build())
                .toList();
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

    /**
     * Chặn DELETE khi còn việc phải làm. Đếm hợp đồng DRAFT/PENDING/ACTIVE — không đếm
     * PropertyStatus.RENTED (nhà chia phòng có khách vẫn mang status ACTIVE).
     * EXPIRED không chặn.
     */
    private void assertNoLiveContracts(UUID zoneId) {
        List<TenantContract> stillLive = tenantContractRepository.findActiveAndPendingByZoneId(zoneId);
        long active = stillLive.stream().filter(c -> c.getStatus() == ContractStatus.ACTIVE).count();
        long pending = stillLive.stream().filter(c -> c.getStatus() == ContractStatus.PENDING).count();
        long draft = stillLive.stream().filter(c -> c.getStatus() == ContractStatus.DRAFT).count();
        if (active == 0 && pending == 0 && draft == 0) {
            return;
        }

        List<String> reasons = new ArrayList<>();
        if (active > 0) {
            reasons.add(active + " hợp đồng đang có khách ở");
        }
        if (pending > 0) {
            reasons.add(pending + " hợp đồng đã chốt chưa nhận khách");
        }
        if (draft > 0) {
            reasons.add(draft + " hợp đồng chờ đón khách");
        }

        throw new BusinessException(
                "Không gỡ được quản lý: khu vực còn " + String.join(", ", reasons)
                        + ". Hãy đổi sang quản lý khác để những việc này được chuyển giao.");
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

    private void sendHandoffNotifications(Zone zone, UUID fromManagerId, UUID toManagerId,
                                          int properties, int contracts, List<TenantContract> affected) {
        User newManager = toManagerId != null ? userRepository.findById(toManagerId).orElse(null) : null;

        if (fromManagerId != null) {
            long remaining = zoneManagerRepository.countByManagerId(fromManagerId);
            String content = "Đã bàn giao khu vực " + zone.getName()
                    + " cho quản lý mới. Số nhà: " + properties + ", Số HĐ: " + contracts
                    + ". Sau bàn giao, bạn còn phụ trách " + remaining + " khu vực.";
            notifyUser(fromManagerId, "Bàn giao khu vực " + zone.getName(), content, "ZONE_REVOKED", null, null);
        }

        if (toManagerId != null) {
            long pendingOnboards = affected.stream()
                    .filter(c -> c.getStatus() == ContractStatus.DRAFT || c.getStatus() == ContractStatus.PENDING)
                    .count();
            LocalDate nearest = affected.stream()
                    .filter(c -> c.getExpectedReceptionDate() != null)
                    .map(TenantContract::getExpectedReceptionDate)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            StringBuilder content = new StringBuilder();
            content.append("Bạn tiếp nhận khu vực ").append(zone.getName())
                    .append(": ").append(properties).append(" nhà · ").append(contracts).append(" hợp đồng.");
            if (pendingOnboards > 0) {
                content.append(" ⚠ ").append(pendingOnboards).append(" hợp đồng chờ đón khách");
                if (nearest != null) {
                    content.append(", sớm nhất ngày ").append(nearest.format(DATE_VN));
                }
                content.append(".");
            }
            notifyUser(toManagerId, "Tiếp nhận khu vực " + zone.getName(),
                    content.toString(), "ZONE_ASSIGNED", null, null);
        }

        notifyTenants(affected, newManager);
    }

    private void notifyTenants(List<TenantContract> affected, User newManager) {
        for (TenantContract c : affected) {
            User tenantUser = c.getTenant() != null ? c.getTenant().getUser() : null;
            if (tenantUser == null) {
                continue;
            }
            String content;
            if (newManager != null) {
                String phone = newManager.getPhoneNumber() != null && !newManager.getPhoneNumber().isBlank()
                        ? newManager.getPhoneNumber()
                        : "chưa có SĐT";
                content = String.format(
                        "Từ hôm nay, %s do %s phụ trách. Liên hệ: %s. "
                                + "Mọi yêu cầu sửa chữa, hoá đơn, trả phòng vui lòng liên hệ số này.",
                        unitLabel(c), newManager.getFullName(), phone);
            } else {
                content = String.format(
                        "Từ hôm nay, %s tạm thời chưa có quản lý phụ trách. "
                                + "Khu vực đang chờ phân công quản lý mới, mọi yêu cầu vui lòng liên hệ tổng đài/chủ nhà.",
                        unitLabel(c));
            }
            String paramsJson = "{\"contractId\":" + c.getId() + "}";
            notifyUser(tenantUser.getId(), "Thay đổi người phụ trách", content,
                    "MANAGER_CHANGED", "ContractDetail", paramsJson);
        }
    }

    private String unitLabel(TenantContract c) {
        String propertyName = c.getProperty() != null ? c.getProperty().getPropertyName() : "nhà";
        if (c.getRoom() != null && c.getRoom().getRoomNumber() != null
                && !c.getRoom().getRoomNumber().isBlank()) {
            return "phòng " + c.getRoom().getRoomNumber() + " (" + propertyName + ")";
        }
        return propertyName + " (nguyên căn)";
    }

    private void notifyUser(UUID userId, String title, String content, String type,
                            String screen, String paramsJson) {
        notificationRepository.save(Notification.builder()
                .userId(userId)
                .title(title)
                .content(content)
                .type(type)
                .screen(screen)
                .paramsJson(paramsJson)
                .read(false)
                .build());
        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        if (screen != null) {
            data.put("screen", screen);
        }
        if (paramsJson != null) {
            data.put("params", paramsJson);
        }
        userPushTokenService.sendToUser(userId, title, content, data);
    }
}
