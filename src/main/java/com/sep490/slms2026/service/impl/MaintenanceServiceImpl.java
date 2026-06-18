package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CreateMaintenanceRequest;
import com.sep490.slms2026.dto.request.ResolveMaintenanceRequest;
import com.sep490.slms2026.dto.request.UpdateMaintenanceStatusRequest;
import com.sep490.slms2026.dto.response.MaintenanceDashboardResponse;
import com.sep490.slms2026.dto.response.MaintenanceRequestResponse;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.*;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class MaintenanceServiceImpl implements MaintenanceService {

    private final MaintenanceRequestRepository requestRepository;
    private final MaintenanceHistoryRepository historyRepository;
    private final MaintenanceImageRepository imageRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentMaintenanceHistoryRepository equipmentHistoryRepository;
    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;

    // Simple counter for request code — in production use DB sequence
    private static final AtomicLong CODE_COUNTER = new AtomicLong(1);

    // ========== CREATE ==========

    @Override
    @Transactional
    public MaintenanceRequestResponse createRequest(CreateMaintenanceRequest dto, UUID currentUserId) {
        User tenant = findUserById(currentUserId);

        Room room = roomRepository.findById(dto.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phòng ID=" + dto.getRoomId()));
        Property property = room.getProperty();

        Equipment equipment = null;
        if (dto.getEquipmentId() != null) {
            equipment = equipmentRepository.findById(dto.getEquipmentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy thiết bị ID=" + dto.getEquipmentId()));
        }

        MaintenanceRequest request = MaintenanceRequest.builder()
                .requestCode(generateRequestCode())
                .tenant(tenant)
                .room(room)
                .property(property)
                .equipment(equipment)
                .category(dto.getCategory())
                .priority(dto.getPriority())
                .description(dto.getDescription())
                .status(MaintenanceStatus.PENDING)
                .build();

        request = requestRepository.save(request);

        // Save images
        if (dto.getImages() != null && !dto.getImages().isEmpty()) {
            for (String url : dto.getImages()) {
                MaintenanceImage img = MaintenanceImage.builder()
                        .maintenanceRequest(request)
                        .imageUrl(url)
                        .build();
                imageRepository.save(img);
            }
        }

        // Record initial history
        saveHistory(request, null, MaintenanceStatus.PENDING, tenant, null);

        return toResponse(request);
    }

    // ========== TENANT: MY REQUESTS ==========

    @Override
    @Transactional(readOnly = true)
    public Page<MaintenanceRequestResponse> getMyRequests(UUID tenantUserId,
                                                           MaintenanceStatus status,
                                                           Pageable pageable) {
        return requestRepository.findByTenantIdAndOptionalStatus(tenantUserId, status, pageable)
                .map(this::toResponse);
    }

    // ========== GET BY ID ==========

    @Override
    @Transactional(readOnly = true)
    public MaintenanceRequestResponse getRequestById(Long id, UUID currentUserId) {
        MaintenanceRequest request = findRequestById(id);
        // Security: tenant chỉ xem request của mình
        // (OM/Admin sẽ được check ở controller level bằng @PreAuthorize)
        return toResponse(request);
    }

    // ========== MANAGER/ADMIN: GET ALL ==========

    @Override
    @Transactional(readOnly = true)
    public Page<MaintenanceRequestResponse> getAllRequests(
            MaintenanceStatus status, MaintenancePriority priority,
            Long propertyId, Long roomId, MaintenanceCategory category,
            Pageable pageable, UUID currentUserId) {

        User user = findUserById(currentUserId);

        if (user.getRole() == Role.ROLE_MANAGER) {
            // OM chỉ xem request thuộc property mình phụ trách
            List<Long> managedPropertyIds = propertyRepository.findIdsByOperationManagerId(currentUserId);
            if (managedPropertyIds.isEmpty()) {
                return Page.empty(pageable);
            }
            return requestRepository.findByPropertyIdInWithFilters(
                    managedPropertyIds, status, priority, propertyId, roomId, category, pageable)
                    .map(this::toResponse);
        }

        // Admin/Owner: xem toàn hệ thống
        return requestRepository.findAllWithFilters(status, priority, propertyId, roomId, category, pageable)
                .map(this::toResponse);
    }

    // ========== UPDATE STATUS ==========

    @Override
    @Transactional
    public MaintenanceRequestResponse updateStatus(Long id, UpdateMaintenanceStatusRequest dto, UUID currentUserId) {
        MaintenanceRequest request = findRequestById(id);
        User manager = findUserById(currentUserId);
        MaintenanceStatus oldStatus = request.getStatus();
        MaintenanceStatus newStatus = dto.getStatus();

        // Validate transitions theo contract
        validateStatusTransition(oldStatus, newStatus);

        request.setStatus(newStatus);

        // Tự set assignedManager khi rời PENDING
        if (oldStatus == MaintenanceStatus.PENDING && request.getAssignedManager() == null) {
            request.setAssignedManager(manager);
        }

        // Set scheduled date nếu có
        if (dto.getScheduledDate() != null) {
            request.setScheduledDate(dto.getScheduledDate());
        }

        requestRepository.save(request);

        // Ghi maintenance_history
        saveHistory(request, oldStatus, newStatus, manager, dto.getNote());

        return toResponse(request);
    }

    // ========== RESOLVE ==========

    @Override
    @Transactional
    public MaintenanceRequestResponse resolveRequest(Long id, ResolveMaintenanceRequest dto, UUID currentUserId) {
        MaintenanceRequest request = findRequestById(id);
        User manager = findUserById(currentUserId);

        if (request.getStatus() != MaintenanceStatus.IN_PROGRESS) {
            throw new BusinessException("Chỉ có thể resolve request đang IN_PROGRESS");
        }

        MaintenanceStatus oldStatus = request.getStatus();

        // 1. status=RESOLVED, resolved_at=now, lưu repair_cost, resolution_note
        request.setStatus(MaintenanceStatus.RESOLVED);
        request.setResolvedAt(LocalDateTime.now());
        request.setRepairCost(dto.getRepairCost());
        request.setResolutionNote(dto.getResolutionNote());

        // Ensure assigned manager is set
        if (request.getAssignedManager() == null) {
            request.setAssignedManager(manager);
        }

        requestRepository.save(request);

        // 2. Ghi maintenance_history
        saveHistory(request, oldStatus, MaintenanceStatus.RESOLVED, manager, dto.getResolutionNote());

        // 3. Nếu có equipment_id: ghi equipment_maintenance_history, maintenance_count++, status=GOOD
        if (request.getEquipment() != null) {
            Equipment equipment = request.getEquipment();

            EquipmentMaintenanceHistory eqHist = EquipmentMaintenanceHistory.builder()
                    .equipment(equipment)
                    .maintenanceRequest(request)
                    .maintenanceDate(LocalDateTime.now())
                    .repairCost(dto.getRepairCost())
                    .note(dto.getResolutionNote())
                    .build();
            equipmentHistoryRepository.save(eqHist);

            equipment.setMaintenanceCount(equipment.getMaintenanceCount() + 1);
            equipment.setLastMaintenanceDate(LocalDateTime.now());
            equipment.setStatus(EquipmentStatus.GOOD);
            equipmentRepository.save(equipment);
        }

        // 4. Tạo expense MAINTENANCE — placeholder (uncomment khi có ExpenseService)
        // if (dto.getRepairCost() != null && dto.getRepairCost() > 0) {
        //     expenseService.createExpense("MAINTENANCE", request.getProperty().getId(),
        //             request.getRoom().getId(), request.getId(), dto.getRepairCost(),
        //             "Chi phí sửa chữa " + request.getRequestCode());
        // }

        // 5. Push notification REQUEST_RESOLVED cho tenant — placeholder
        // notificationService.send(NotificationEvent.REQUEST_RESOLVED, request.getTenant().getId(),
        //         "maintenance", request.getId());

        return toResponse(request);
    }

    // ========== DASHBOARD ==========

    @Override
    @Transactional(readOnly = true)
    public MaintenanceDashboardResponse getDashboard(Long propertyId, LocalDateTime from, LocalDateTime to) {
        long total = requestRepository.countWithFilters(propertyId, from, to);
        long pending = requestRepository.countByStatusWithFilters(MaintenanceStatus.PENDING, propertyId, from, to);
        long inProgress = requestRepository.countByStatusWithFilters(MaintenanceStatus.IN_PROGRESS, propertyId, from, to);
        long resolved = requestRepository.countByStatusWithFilters(MaintenanceStatus.RESOLVED, propertyId, from, to);
        long cancelled = requestRepository.countByStatusWithFilters(MaintenanceStatus.CANCELLED, propertyId, from, to);
        long totalRepairCost = requestRepository.sumRepairCostWithFilters(propertyId, from, to);

        return MaintenanceDashboardResponse.builder()
                .total(total)
                .pending(pending)
                .inProgress(inProgress)
                .resolved(resolved)
                .cancelled(cancelled)
                .totalRepairCost(totalRepairCost)
                .build();
    }

    // ========== PRIVATE HELPERS ==========

    private void validateStatusTransition(MaintenanceStatus from, MaintenanceStatus to) {
        boolean valid = switch (from) {
            case PENDING -> to == MaintenanceStatus.IN_PROGRESS || to == MaintenanceStatus.CANCELLED;
            case IN_PROGRESS -> to == MaintenanceStatus.CANCELLED;
            default -> false;
        };
        if (!valid) {
            throw new BusinessException(
                    String.format("Không thể chuyển trạng thái từ %s sang %s", from, to));
        }
    }

    private String generateRequestCode() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMM"));
        long seq = CODE_COUNTER.getAndIncrement();
        return String.format("MR-%s-%04d", datePart, seq);
    }

    private void saveHistory(MaintenanceRequest request, MaintenanceStatus oldStatus,
                              MaintenanceStatus newStatus, User changedBy, String note) {
        MaintenanceHistory history = MaintenanceHistory.builder()
                .maintenanceRequest(request)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .changedByUser(changedBy)
                .note(note)
                .build();
        historyRepository.save(history);
    }

    private MaintenanceRequest findRequestById(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy yêu cầu bảo trì ID=" + id));
    }

    private User findUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy người dùng ID=" + userId));
    }

    /**
     * Map entity → flat DTO theo contract section 3.
     * Includes tenant/room/property/equipment names + timeline.
     */
    private MaintenanceRequestResponse toResponse(MaintenanceRequest request) {
        // Images
        List<String> imageUrls = request.getImages() != null
                ? request.getImages().stream().map(MaintenanceImage::getImageUrl).toList()
                : List.of();

        // Timeline (audit)
        List<MaintenanceHistory> histories =
                historyRepository.findByMaintenanceRequestIdOrderByChangedAtAsc(request.getId());
        List<MaintenanceRequestResponse.TimelineEntry> timeline = histories.stream()
                .map(h -> MaintenanceRequestResponse.TimelineEntry.builder()
                        .oldStatus(h.getOldStatus())
                        .newStatus(h.getNewStatus())
                        .note(h.getNote())
                        .changedBy(h.getChangedByUser() != null ? Long.valueOf(h.getChangedByUser().getId().hashCode()) : null)
                        .changedByName(h.getChangedByUser() != null ? h.getChangedByUser().getFullName() : null)
                        .changedAt(h.getChangedAt())
                        .build())
                .toList();

        MaintenanceRequestResponse.MaintenanceRequestResponseBuilder builder = MaintenanceRequestResponse.builder()
                .id(request.getId())
                .requestCode(request.getRequestCode())
                .status(request.getStatus())
                .category(request.getCategory())
                .priority(request.getPriority())
                .description(request.getDescription())
                .scheduledDate(request.getScheduledDate())
                .repairCost(request.getRepairCost())
                .resolutionNote(request.getResolutionNote())
                .resolvedAt(request.getResolvedAt())
                .images(imageUrls)
                .timeline(timeline)
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt());

        // Tenant info
        if (request.getTenant() != null) {
            builder.tenantId(Long.valueOf(request.getTenant().getId().hashCode()))
                    .tenantName(request.getTenant().getFullName())
                    .tenantPhone(request.getTenant().getPhoneNumber());
        }

        // Room info
        if (request.getRoom() != null) {
            builder.roomId(request.getRoom().getId())
                    .roomName(request.getRoom().getRoomNumber());
        }

        // Property info
        if (request.getProperty() != null) {
            builder.propertyId(request.getProperty().getId())
                    .propertyName(request.getProperty().getPropertyName());
        }

        // Equipment info
        if (request.getEquipment() != null) {
            Equipment eq = request.getEquipment();
            builder.equipmentId(eq.getId())
                    .equipmentName(eq.getEquipmentName() != null
                            ? eq.getEquipmentName()
                            : eq.getCatalog().getName());
        }

        // Assigned manager info
        if (request.getAssignedManager() != null) {
            builder.assignedManagerId(Long.valueOf(request.getAssignedManager().getId().hashCode()))
                    .assignedManagerName(request.getAssignedManager().getFullName());
        }

        return builder.build();
    }
}