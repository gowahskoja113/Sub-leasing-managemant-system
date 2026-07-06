package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.MaintenanceRequestResponse;
import com.sep490.slms2026.dto.response.MaintenanceTimelineResponse;
import com.sep490.slms2026.dto.response.MaintenanceDashboardResponse;
import com.sep490.slms2026.entity.MaintenanceRequest;
import com.sep490.slms2026.entity.MaintenanceTimeline;
import com.sep490.slms2026.entity.TenantPendingCharge;
import com.sep490.slms2026.enums.MaintenanceStatus;
import com.sep490.slms2026.repository.MaintenanceRequestRepository;
import com.sep490.slms2026.repository.MaintenanceTimelineRepository;
import com.sep490.slms2026.repository.TenantPendingChargeRepository;
import com.sep490.slms2026.service.MaintenanceService;
import com.sep490.slms2026.service.PushNotificationService;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import jakarta.persistence.criteria.Predicate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaintenanceServiceImpl implements MaintenanceService {

    private final MaintenanceRequestRepository repository;
    private final MaintenanceTimelineRepository timelineRepository;
    private final TenantPendingChargeRepository tenantPendingChargeRepository;
    private final com.sep490.slms2026.service.PropertyImageStorage imageStorage;
    private final com.sep490.slms2026.repository.RoomRepository roomRepository;
    private final com.sep490.slms2026.repository.EquipmentRepository equipmentRepository;
    private final com.sep490.slms2026.repository.ExpenseRepository expenseRepository;
    private final com.sep490.slms2026.repository.PropertyRepository propertyRepository;
    private final com.sep490.slms2026.repository.TenantContractRepository tenantContractRepository;
    private final com.sep490.slms2026.repository.TenantRepository tenantRepository;
    private final com.sep490.slms2026.repository.UserRepository userRepository;
    private final com.sep490.slms2026.repository.NotificationRepository notificationRepository;
    private final PushNotificationService pushNotificationService;

    @Override
    public Page<MaintenanceRequestResponse> getRequests(
            String status, String priority, String category, Long propertyId, Long roomId, Pageable pageable) {
            
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String role = user.getAuthorities().iterator().next().getAuthority();

        Specification<MaintenanceRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));

            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), MaintenanceStatus.valueOf(status.toUpperCase())));
            }
            if (priority != null && !priority.isBlank()) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (propertyId != null) {
                predicates.add(cb.equal(root.join("property").get("id"), propertyId));
            }
            if (roomId != null) {
                predicates.add(cb.equal(root.join("room").get("id"), roomId));
            }

            if ("ROLE_TENANT".equals(role)) {
                predicates.add(cb.equal(root.join("tenant").join("user").get("id"), user.getId()));
            } else if ("ROLE_MANAGER".equals(role)) {
                Predicate managedBy = cb.equal(root.join("property").get("managedBy"), user.getId());
                Predicate opManager = cb.equal(root.join("property").get("operationManagerId"), user.getId());
                predicates.add(cb.or(managedBy, opManager));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return repository.findAll(spec, pageable).map(this::convertToResponse);
    }

    @Override
    public MaintenanceRequestResponse createRequest(com.sep490.slms2026.dto.request.MaintenanceCreateRequest request) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        com.sep490.slms2026.entity.Tenant tenant = tenantRepository.findById(user.getId())
                .orElseThrow(() -> new com.sep490.slms2026.exception.ResourceNotFoundException("Không tìm thấy tenant"));
                
        com.sep490.slms2026.entity.Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new com.sep490.slms2026.exception.ResourceNotFoundException("Phòng không tồn tại"));

        MaintenanceRequest req = MaintenanceRequest.builder()
                .tenant(tenant)
                .property(room.getProperty())
                .room(room)
                .title(request.getDescription())
                .description(request.getDescription())
                .category(request.getCategory())
                .priority(request.getPriority())
                .equipmentId(request.getEquipmentId())
                .beforeImageUrls(request.getImages() != null ? String.join(",", request.getImages()) : null)
                .status(MaintenanceStatus.PENDING)
                .build();
                
        req = repository.save(req);
        addTimeline(req, null, MaintenanceStatus.PENDING, "Khách thuê tạo yêu cầu");
        
        // Notify manager
        if (req.getProperty() != null && req.getProperty().getManagedBy() != null) {
            java.util.UUID managerId = req.getProperty().getManagedBy();
            String title = "Yêu cầu bảo trì mới";
            String body = "Khách thuê " + user.getFullName() + " vừa tạo yêu cầu bảo trì phòng " + req.getRoom().getRoomNumber();
            
            com.sep490.slms2026.entity.Notification notification = com.sep490.slms2026.entity.Notification.builder()
                    .userId(managerId)
                    .title(title)
                    .content(body)
                    .type("MAINTENANCE")
                    .build();
            notificationRepository.save(notification);
            
            final Long requestId = req.getId();
            userRepository.findById(managerId).ifPresent(manager -> {
                String token = manager.getPushToken();
                if (token != null && !token.isBlank()) {
                    pushNotificationService.sendPushNotification(token, title, body, java.util.Map.of("requestId", requestId));
                }
            });
        }
        
        return convertToResponse(req);
    }

    @Override
    public Page<MaintenanceRequestResponse> getMyRequests(Pageable pageable) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return repository.findByTenantIdAndDeletedFalse(user.getId(), pageable).map(this::convertToResponse);
    }

    @Override
    public MaintenanceRequestResponse getRequestById(Long id) {
        return convertToResponse(repository.findById(id).orElseThrow(() -> new com.sep490.slms2026.exception.ResourceNotFoundException("Không tìm thấy request")));
    }

    @Override
    public MaintenanceDashboardResponse getDashboardStats() {
        return MaintenanceDashboardResponse.builder()
                .total(repository.countAll())
                .pending(repository.countPending())
                .inProgress(repository.countInProgress())
                .resolved(repository.countResolved())
                .cancelled(repository.countCancelled())
                .totalRepairCost(repository.sumRepairCost() != null ? repository.sumRepairCost() : java.math.BigDecimal.ZERO)
                .build();
    }

    @Override
    public List<MaintenanceRequestResponse> getEquipmentMaintenanceHistory(Long equipmentId) {
        return repository.findByEquipmentIdAndDeletedFalseOrderByCreatedAtDesc(equipmentId)
                .stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    private void addTimeline(MaintenanceRequest req, MaintenanceStatus oldStatus, MaintenanceStatus newStatus, String note) {
        CustomUserDetails user = null;
        try {
            user = SecurityUtils.requireCurrentUser();
        } catch (Exception e) {
            // Ignore for system automated actions
        }
        MaintenanceTimeline timeline = MaintenanceTimeline.builder()
                .maintenanceRequest(req)
                .oldStatus(oldStatus)
                .newStatus(newStatus)
                .note(note)
                .changedBy(user != null ? user.getId() : null)
                .changedByName(user != null ? user.getFullName() : "System")
                .build();
        timelineRepository.save(timeline);
        
        if (req.getTenant() != null && req.getTenant().getUser() != null) {
            String title = "Cập nhật yêu cầu bảo trì";
            String body = "Yêu cầu #" + req.getId() + " của bạn đã đổi trạng thái thành: " + newStatus;
            
            com.sep490.slms2026.entity.Notification notification = com.sep490.slms2026.entity.Notification.builder()
                    .userId(req.getTenant().getUser().getId())
                    .title(title)
                    .content(body)
                    .type("MAINTENANCE")
                    .build();
            notificationRepository.save(notification);
            
            String token = req.getTenant().getUser().getPushToken();
            if (token != null && !token.isBlank()) {
                pushNotificationService.sendPushNotification(token, title, body, java.util.Map.of("requestId", req.getId()));
            }
        }
    }

    @Override
    public MaintenanceRequestResponse uploadPhotos(Long id, java.util.List<org.springframework.web.multipart.MultipartFile> files, String type) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        java.util.List<String> newUrls = new java.util.ArrayList<>();
        String prefix = "MAINT-" + id;
        
        for (org.springframework.web.multipart.MultipartFile file : files) {
            try {
                String url = imageStorage.store(prefix, file.getOriginalFilename(), file.getBytes());
                newUrls.add(url);
            } catch (Exception e) {
                throw new RuntimeException("Failed to upload photo", e);
            }
        }
        
        if (!newUrls.isEmpty()) {
            if ("BEFORE".equalsIgnoreCase(type)) {
                String existing = req.getBeforeImageUrls();
                req.setBeforeImageUrls(existing == null || existing.isEmpty() ? String.join(",", newUrls) : existing + "," + String.join(",", newUrls));
            } else if ("AFTER".equalsIgnoreCase(type)) {
                String existing = req.getAfterImageUrls();
                req.setAfterImageUrls(existing == null || existing.isEmpty() ? String.join(",", newUrls) : existing + "," + String.join(",", newUrls));
            } else {
                throw new IllegalArgumentException("Type must be BEFORE or AFTER");
            }
            repository.save(req);
        }
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse acknowledge(Long id, MaintenanceAcknowledgeRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        MaintenanceStatus oldStatus = req.getStatus();
        req.setStatus(MaintenanceStatus.ACKNOWLEDGED);
        req.setAcknowledgedAt(LocalDateTime.now());
        req.setTechnicianId(request.getTechnicianId());
        repository.save(req);
        addTimeline(req, oldStatus, MaintenanceStatus.ACKNOWLEDGED, "Tiếp nhận yêu cầu, phân công: " + request.getTechnicianId());
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse schedule(Long id, MaintenanceScheduleRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        MaintenanceStatus oldStatus = req.getStatus();
        req.setStatus(MaintenanceStatus.SCHEDULED);
        req.setScheduledSlots(String.join(",", request.getScheduledSlots()));
        repository.save(req);
        addTimeline(req, oldStatus, MaintenanceStatus.SCHEDULED, "Đề xuất lịch sửa chữa");
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse confirmSchedule(Long id, MaintenanceConfirmScheduleRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        MaintenanceStatus oldStatus = req.getStatus();
        req.setConfirmedSlot(request.getSlot());
        repository.save(req);
        addTimeline(req, oldStatus, req.getStatus(), "Khách chọn lịch: " + request.getSlot());
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse updateStatus(Long id, MaintenanceStatusRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        MaintenanceStatus oldStatus = req.getStatus();
        req.setStatus(request.getStatus());
        req.setOnHoldReason(request.getOnHoldReason());
        
        if (request.getStatus() == MaintenanceStatus.IN_PROGRESS) {
            if (req.getBeforeImageUrls() == null || req.getBeforeImageUrls().isEmpty()) {
                throw new com.sep490.slms2026.exception.BusinessException("Bắt buộc phải có ảnh trước sửa chữa để đổi trạng thái Đang xử lý");
            }
            if (req.getRoom() != null) {
                req.getRoom().setStatus(com.sep490.slms2026.enums.RoomStatus.MAINTENANCE);
                roomRepository.save(req.getRoom());
            }
        } else if (request.getStatus() == MaintenanceStatus.CANCELLED) {
            CustomUserDetails user = SecurityUtils.requireCurrentUser();
            String roleName = user.getAuthorities().iterator().next().getAuthority();
            if ("ROLE_TENANT".equals(roleName)) {
                throw new org.springframework.security.access.AccessDeniedException("Tenant không có quyền hủy yêu cầu");
            }
            if (req.getRoom() != null) {
                boolean hasActiveContract = tenantContractRepository.existsByRoomIdAndStatus(req.getRoom().getId(), com.sep490.slms2026.enums.ContractStatus.ACTIVE);
                req.getRoom().setStatus(hasActiveContract ? com.sep490.slms2026.enums.RoomStatus.RENTED : com.sep490.slms2026.enums.RoomStatus.AVAILABLE);
                roomRepository.save(req.getRoom());
            }
        }
        
        repository.save(req);
        addTimeline(req, oldStatus, request.getStatus(), request.getOnHoldReason() != null ? request.getOnHoldReason() : "Cập nhật trạng thái");
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse resolve(Long id, MaintenanceResolveRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        
        if (req.getAfterImageUrls() == null || req.getAfterImageUrls().isEmpty()) {
            throw new com.sep490.slms2026.exception.BusinessException("Bắt buộc phải có ảnh sau sửa chữa để hoàn tất");
        }
        
        MaintenanceStatus oldStatus = req.getStatus();
        req.setRepairCost(request.getRepairCost());
        req.setCostPaidBy(request.getCostPaidBy());
        req.setCause(request.getCause());
        req.setResolutionNote(request.getResolutionNote());
        req.setEquipmentId(request.getEquipmentId());
        
        // thresholds
        if (request.getRepairCost() != null && request.getRepairCost().doubleValue() > 2000000) {
            req.setStatus(MaintenanceStatus.PENDING_APPROVAL);
        } else {
            req.setStatus(MaintenanceStatus.DONE);
            req.setDoneAt(LocalDateTime.now());
        }
        
        if (request.getEquipmentId() != null) {
            equipmentRepository.findById(request.getEquipmentId()).ifPresent(eq -> {
                // If repair cost > 1 million or cause is severe, recommend replacement
                if (request.getRepairCost() != null && request.getRepairCost().doubleValue() > 1000000) {
                    eq.setRecommendReplacement(true);
                    equipmentRepository.save(eq);
                }
            });
        }
        
        repository.save(req);
        addTimeline(req, oldStatus, req.getStatus(), "Giải quyết yêu cầu: " + request.getResolutionNote());
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse approve(Long id, MaintenanceApproveRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        MaintenanceStatus oldStatus = req.getStatus();
        if (request.isApprove()) {
            req.setStatus(MaintenanceStatus.DONE);
            req.setDoneAt(LocalDateTime.now());
            addTimeline(req, oldStatus, MaintenanceStatus.DONE, "Chủ nhà phê duyệt chi phí > 2tr");
        } else {
            req.setStatus(MaintenanceStatus.IN_PROGRESS);
            addTimeline(req, oldStatus, MaintenanceStatus.IN_PROGRESS, "Chủ nhà TỪ CHỐI chi phí");
        }
        repository.save(req);
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse confirm(Long id, MaintenanceConfirmRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        MaintenanceStatus oldStatus = req.getStatus();
        if (request.isAccept()) {
            req.setStatus(MaintenanceStatus.CONFIRMED);
            req.setTenantConfirmedAt(LocalDateTime.now());
            
            // Auto Expense creation for Host
            if (req.getCostPaidBy() == com.sep490.slms2026.enums.CostPaidBy.HOST && req.getRepairCost() != null) {
                com.sep490.slms2026.entity.Expense expense = new com.sep490.slms2026.entity.Expense();
                expense.setProperty(req.getProperty());
                expense.setCategory(com.sep490.slms2026.enums.ExpenseCategory.MAINTENANCE);
                expense.setAmount(req.getRepairCost());
                expense.setMonth(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
                expense.setNote("Chi phí bảo trì ticket #" + req.getId() + ": " + req.getTitle());
                expense.setCreatedBy("SYSTEM");
                expenseRepository.save(expense);
            } else if (req.getCostPaidBy() == com.sep490.slms2026.enums.CostPaidBy.TENANT && req.getRepairCost() != null) {
                com.sep490.slms2026.entity.TenantContract activeContract = tenantContractRepository
                        .findByRoomIdAndStatus(req.getRoom().getId(), com.sep490.slms2026.enums.ContractStatus.ACTIVE)
                        .orElse(null);
                
                if (activeContract != null) {
                    TenantPendingCharge charge = TenantPendingCharge.builder()
                            .tenantContract(activeContract)
                            .amount(req.getRepairCost())
                            .category("MAINTENANCE")
                            .note("Chi phí bảo trì ticket #" + req.getId() + ": " + req.getTitle())
                            .status("PENDING")
                            .build();
                    tenantPendingChargeRepository.save(charge);
                }
            }
            
            // Revert room status
            if (req.getRoom() != null) {
                boolean hasActiveContract = tenantContractRepository.existsByRoomIdAndStatus(req.getRoom().getId(), com.sep490.slms2026.enums.ContractStatus.ACTIVE);
                req.getRoom().setStatus(hasActiveContract ? com.sep490.slms2026.enums.RoomStatus.RENTED : com.sep490.slms2026.enums.RoomStatus.AVAILABLE);
                roomRepository.save(req.getRoom());
            }
            
            addTimeline(req, oldStatus, MaintenanceStatus.CONFIRMED, "Khách thuê xác nhận hoàn tất");
        } else {
            req.setStatus(MaintenanceStatus.REOPENED);
            req.setReopenCount(req.getReopenCount() + 1);
            addTimeline(req, oldStatus, MaintenanceStatus.REOPENED, "Khách thuê KHÔNG HÀI LÒNG, yêu cầu xử lý lại");
        }
        repository.save(req);
        return convertToResponse(req);
    }

    private MaintenanceRequestResponse convertToResponse(MaintenanceRequest req) {
        MaintenanceRequestResponse res = new MaintenanceRequestResponse();
        res.setId(req.getId());
        res.setRequestCode("M-" + req.getId());
        res.setTitle(req.getTitle());
        res.setStatus(req.getStatus());
        res.setCategory(req.getCategory());
        res.setPriority(req.getPriority());
        res.setDescription(req.getDescription());
        
        if (req.getTenant() != null && req.getTenant().getUser() != null) {
            res.setTenantId(req.getTenant().getUser().getId());
            res.setTenantName(req.getTenant().getUser().getFullName());
            res.setTenantPhone(req.getTenant().getUser().getPhoneNumber());
        }
        
        if (req.getRoom() != null) {
            res.setRoomId(req.getRoom().getId());
            res.setRoomName(req.getRoom().getRoomNumber());
        }
        
        if (req.getProperty() != null) {
            res.setPropertyId(req.getProperty().getId());
            res.setPropertyName(req.getProperty().getPropertyName());
        }
        
        if (req.getEquipmentId() != null) {
            equipmentRepository.findById(req.getEquipmentId()).ifPresent(eq -> {
                res.setEquipmentId(eq.getId());
                res.setEquipmentName(eq.getCatalog() != null ? eq.getCatalog().getName() : null);
            });
        }
        
        if (req.getProperty() != null && req.getProperty().getManagedBy() != null) {
            res.setAssignedManagerId(req.getProperty().getManagedBy());
            userRepository.findById(req.getProperty().getManagedBy()).ifPresent(manager -> {
                res.setAssignedManagerName(manager.getFullName());
            });
        }
        
        res.setScheduledDate(req.getConfirmedSlot() != null ? req.getConfirmedSlot() : req.getScheduledSlots());
        res.setResolvedAt(req.getDoneAt());
        res.setRepairCost(req.getRepairCost());
        res.setResolutionNote(req.getResolutionNote());
        
        List<String> images = new ArrayList<>();
        if (req.getBeforeImageUrls() != null && !req.getBeforeImageUrls().isBlank()) {
            images.addAll(List.of(req.getBeforeImageUrls().split(",")));
        }
        if (req.getAfterImageUrls() != null && !req.getAfterImageUrls().isBlank()) {
            images.addAll(List.of(req.getAfterImageUrls().split(",")));
        }
        res.setImages(images);
        
        List<MaintenanceTimeline> timelines = timelineRepository.findByMaintenanceRequestIdOrderByChangedAtAsc(req.getId());
        res.setTimeline(timelines.stream().map(t -> MaintenanceTimelineResponse.builder()
                .oldStatus(t.getOldStatus() != null ? t.getOldStatus().name() : null)
                .newStatus(t.getNewStatus() != null ? t.getNewStatus().name() : null)
                .note(t.getNote())
                .changedBy(t.getChangedBy() != null ? t.getChangedBy().toString() : null)
                .changedByName(t.getChangedByName())
                .changedAt(t.getChangedAt())
                .build()).collect(Collectors.toList()));
                
        res.setCreatedAt(req.getCreatedAt());
        res.setUpdatedAt(req.getUpdatedAt());
        
        return res;
    }
}
