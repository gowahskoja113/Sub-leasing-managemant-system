package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.MaintenanceDashboardResponse;
import com.sep490.slms2026.dto.response.MaintenancePhotoHistoryResponse;
import com.sep490.slms2026.dto.response.MaintenanceRequestResponse;
import com.sep490.slms2026.dto.response.MaintenanceTimelineResponse;
import com.sep490.slms2026.dto.response.ManagerAvailabilitySlotResponse;
import com.sep490.slms2026.dto.response.OutstandingDamageResponse;
import com.sep490.slms2026.dto.response.TenantInvoiceResponse;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.*;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ConflictException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.MaintenanceService;
import com.sep490.slms2026.service.PropertyImageStorage;
import com.sep490.slms2026.service.RealtimeEventService;
import com.sep490.slms2026.service.TenantPendingChargeService;
import com.sep490.slms2026.service.UserPushTokenService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaintenanceServiceImpl implements MaintenanceService {

    private static final List<String> PURCHASE_KEYWORDS = List.of(
            "mua mới", "mua moi", "thay mới", "thay moi", "lắp thêm", "lap them", "nâng cấp", "nang cap");

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalTime BUSINESS_START = LocalTime.of(7, 0);
    private static final LocalTime BUSINESS_END = LocalTime.of(18, 0);
    private static final int VISIT_SLOT_MINUTES = 30;
    private static final int REPAIR_SLOT_MINUTES = 60;

    private final MaintenanceRequestRepository repository;
    private final MaintenanceTimelineRepository timelineRepository;
    private final MaintenanceImageRepository maintenanceImageRepository;
    private final OutstandingDamageRecordRepository outstandingDamageRecordRepository;
    private final TenantPendingChargeRepository tenantPendingChargeRepository;
    private final PropertyImageStorage imageStorage;
    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;
    private final EquipmentRepository equipmentRepository;
    private final TenantContractRepository tenantContractRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final UserPushTokenService userPushTokenService;
    private final TenantPendingChargeService tenantPendingChargeService;
    private final RealtimeEventService realtimeEventService;

    @Override
    public Page<MaintenanceRequestResponse> getRequests(
            String status, String priority, String category, Long propertyId, Long roomId, Pageable pageable) {

        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String role = user.getAuthorities().iterator().next().getAuthority();

        Specification<MaintenanceRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));

            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), parseStatus(status)));
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
                predicates.add(cb.equal(root.join("property").get("operationManagerId"), user.getId()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return repository.findAll(spec, pageable).map(this::convertToResponse);
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse createRequest(MaintenanceCreateRequest request) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        Tenant tenant = tenantRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tenant"));

        Room room = null;
        Property property;
        TenantContract tenantContract;
        if (request.getRoomId() != null) {
            room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));
            property = room.getProperty();
            tenantContract = assertTenantOwnsActiveUnit(user.getId(), room, property.getId());
        } else if (request.getPropertyId() != null) {
            property = propertyRepository.findById(request.getPropertyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bất động sản không tồn tại"));
            tenantContract = assertTenantOwnsActiveWholeHouse(user.getId(), property.getId());
        } else {
            throw new BusinessException(
                    "Thiếu vị trí sự cố: gửi roomId (thuê theo phòng) hoặc propertyId (thuê nguyên căn)");
        }

        String title = request.getTitle() != null ? request.getTitle().trim() : "";
        if (title.isBlank()) {
            throw new BusinessException("Tiêu đề sự cố là bắt buộc");
        }
        if (title.length() > 200) {
            throw new BusinessException("Tiêu đề sự cố không được vượt quá 200 ký tự");
        }
        validateNoPurchaseIntent(title, request.getDescription());

        Long equipmentId = request.getEquipmentId();
        if (equipmentId != null) {
            Equipment equipment = equipmentRepository.findById(equipmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thiết bị"));
            boolean matches = equipment.getProperty() != null
                    && equipment.getProperty().getId().equals(property.getId())
                    && (equipment.getRoom() == null || room == null || equipment.getRoom().getId().equals(room.getId()));
            if (!matches) {
                throw new BusinessException("Thiết bị không thuộc phòng/nhà bạn đang báo sự cố");
            }
        }

        String category = resolveCreateCategory(equipmentId, request.getCategory());
        String description = trimToNull(request.getDescription());
        String beforeUrls = joinUrls(request.getImages());

        if (request.getPreviousRequestId() != null) {
            MaintenanceRequest prev = findActive(request.getPreviousRequestId());
            requireTenantOwner(prev, user.getId());
        }

        if (property.getOperationManagerId() == null) {
            throw new BusinessException(
                    "Nhà chưa được gán quản lý vận hành — chưa thể đặt lịch hẹn bảo trì. Vui lòng liên hệ admin.");
        }
        if (request.getVisitAppointmentAt() == null) {
            throw new BusinessException("visitAppointmentAt là bắt buộc khi tạo yêu cầu bảo trì");
        }
        validateAndAssertSlotAvailable(
                property.getOperationManagerId(),
                request.getVisitAppointmentAt(),
                VISIT_SLOT_MINUTES,
                null);

        MaintenanceRequest req = MaintenanceRequest.builder()
                .tenant(tenant)
                .property(property)
                .room(room)
                .tenantContract(tenantContract)
                .title(title)
                .description(description)
                .category(category)
                .equipmentId(equipmentId)
                .beforeImageUrls(beforeUrls)
                .previousRequestId(request.getPreviousRequestId())
                .visitAppointmentAt(request.getVisitAppointmentAt())
                .flowType(MaintenanceFlowType.NORMAL_WEAR)
                .status(MaintenanceStatus.OPEN)
                .build();

        req = repository.save(req);
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            appendPhotoHistory(req, MaintenancePhotoType.BEFORE, request.getImages());
        }

        String timelineNote = category != null
                ? "Khách thuê tạo yêu cầu [" + category + "], hẹn xem "
                        + request.getVisitAppointmentAt()
                : "Khách thuê tạo yêu cầu, hẹn xem " + request.getVisitAppointmentAt();
        addTimeline(req, null, MaintenanceStatus.OPEN, timelineNote);

        String locationLabel = room != null
                ? "phòng " + room.getRoomNumber()
                : "nguyên căn " + property.getPropertyName();
        notifyPropertyManager(req,
                "Yêu cầu bảo trì mới",
                "Khách thuê " + user.getFullName() + ": \"" + title + "\" — "
                        + locationLabel + " (#" + req.getId() + "), hẹn "
                        + request.getVisitAppointmentAt(),
                "MAINTENANCE_CREATED");
        realtimeEventService.publishMaintenanceEvent(req, RealtimeEventService.EVT_MAINTENANCE_CREATED);
        realtimeEventService.publishMaintenanceEvent(req, RealtimeEventService.EVT_MAINTENANCE_SCHEDULE_CHANGED);

        return convertToResponse(req);
    }

    @Override
    public Page<MaintenanceRequestResponse> getMyRequests(Pageable pageable) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return repository.findByTenantIdAndDeletedFalse(user.getId(), pageable).map(this::convertToResponse);
    }

    @Override
    public MaintenanceRequestResponse getRequestById(Long id) {
        MaintenanceRequest req = findActive(id);
        assertCanViewRequest(req);
        return convertToResponse(req);
    }

    @Override
    public MaintenanceDashboardResponse getDashboardStats() {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String role = user.getAuthorities().iterator().next().getAuthority();

        if ("ROLE_MANAGER".equals(role)) {
            UUID managerId = user.getId();
            return MaintenanceDashboardResponse.builder()
                    .total(repository.countAllByManager(managerId))
                    .pending(repository.countOpenByManager(managerId))
                    .inProgress(repository.countInProgressByManager(managerId))
                    .resolved(repository.countResolvedByManager(managerId))
                    .cancelled(repository.countCancelledByManager(managerId))
                    .totalRepairCost(nz(repository.sumInvoiceAmountByManager(managerId)))
                    .build();
        }

        return MaintenanceDashboardResponse.builder()
                .total(repository.countAll())
                .pending(repository.countOpen())
                .inProgress(repository.countInProgress())
                .resolved(repository.countResolved())
                .cancelled(repository.countCancelled())
                .totalRepairCost(nz(repository.sumInvoiceAmount()))
                .build();
    }

    @Override
    public List<MaintenanceRequestResponse> getEquipmentMaintenanceHistory(Long equipmentId) {
        return repository.findByEquipmentIdAndDeletedFalseOrderByCreatedAtDesc(equipmentId)
                .stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse approve(Long id, MaintenanceApproveRequest request) {
        MaintenanceRequest req = findActive(id);
        requireManagerAccess(req);
        requireStatus(req, MaintenanceStatus.OPEN);
        requireVisitArrivalConfirmed(req);

        String categoryFromBody = request != null ? request.getCategory() : null;
        String category;
        if (categoryFromBody != null && !categoryFromBody.isBlank()) {
            category = parseCategoryRequired(categoryFromBody);
        } else if (req.getCategory() != null && !req.getCategory().isBlank()) {
            category = parseCategoryRequired(req.getCategory());
        } else {
            throw new BusinessException("Danh mục sự cố (category) là bắt buộc khi duyệt yêu cầu");
        }

        String priority = parsePriorityOptional(request != null ? request.getPriority() : null);
        LocalDateTime repairAt = request != null ? request.getRepairAppointmentAt() : null;

        MaintenanceStatus old = req.getStatus();
        req.setCategory(category);
        if (priority != null) {
            req.setPriority(priority);
        }
        req.setFlowType(MaintenanceFlowType.NORMAL_WEAR);
        req.setDamageCause(DamageCause.WEAR);
        req.setAcknowledgedAt(LocalDateTime.now());

        if (repairAt != null) {
            UUID managerId = requireManagerId(req);
            validateAndAssertSlotAvailable(managerId, repairAt, REPAIR_SLOT_MINUTES, req.getId());
            req.setRepairAppointmentAt(repairAt);
            req.setStatus(MaintenanceStatus.REPAIR_SCHEDULED);
            repository.save(req);
            addTimeline(req, old, MaintenanceStatus.REPAIR_SCHEDULED,
                    "Manager duyệt [" + category + "], đặt lịch sửa " + repairAt);
            notifyTenant(req,
                    "Yêu cầu bảo trì đã được duyệt — đã đặt lịch sửa",
                    "Yêu cầu #" + req.getId() + " \"" + req.getTitle()
                            + "\" đã duyệt. Lịch sửa: " + repairAt,
                    "MAINTENANCE_APPROVED");
            realtimeEventService.publishMaintenanceEvent(req, RealtimeEventService.EVT_MAINTENANCE_APPROVED);
            realtimeEventService.publishMaintenanceEvent(req, RealtimeEventService.EVT_MAINTENANCE_SCHEDULE_CHANGED);
        } else {
            req.setStatus(MaintenanceStatus.IN_REPAIR);
            markRoomMaintenance(req);
            repository.save(req);
            addTimeline(req, old, MaintenanceStatus.IN_REPAIR,
                    "Manager duyệt yêu cầu [" + category + "], tiến hành sửa chữa ngay");
            notifyTenant(req,
                    "Yêu cầu bảo trì đã được duyệt",
                    "Yêu cầu #" + req.getId() + " \"" + req.getTitle() + "\" đã được tiếp nhận, đang sửa chữa.",
                    "MAINTENANCE_APPROVED");
            realtimeEventService.publishMaintenanceEvent(req, RealtimeEventService.EVT_MAINTENANCE_APPROVED);
        }
        return convertToResponse(req);
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse rejectFault(Long id, MaintenanceRejectFaultRequest request) {
        MaintenanceRequest req = findActive(id);
        requireManagerAccess(req);
        requireStatus(req, MaintenanceStatus.OPEN);
        requireVisitArrivalConfirmed(req);

        if (request == null || isBlank(request.getFaultReason())) {
            throw new BusinessException("Bắt buộc nhập lý do (faultReason)");
        }
        if (request.getResolutionPath() == null) {
            throw new BusinessException("resolutionPath là bắt buộc: MANAGER_REPAIR hoặc TENANT_SELF_REPAIR");
        }

        List<String> evidenceUrls = new ArrayList<>();
        if (request.getFaultEvidenceImages() != null) {
            evidenceUrls.addAll(request.getFaultEvidenceImages().stream()
                    .filter(u -> u != null && !u.isBlank()).toList());
        }
        if (evidenceUrls.isEmpty()) {
            throw new BusinessException("Bắt buộc đính kèm ảnh bằng chứng lỗi tenant (faultEvidenceImages)");
        }

        MaintenanceStatus old = req.getStatus();
        req.setFlowType(MaintenanceFlowType.TENANT_FAULT);
        req.setDamageCause(DamageCause.TENANT_MISUSE);
        req.setFaultReason(request.getFaultReason().trim());
        req.setFaultResolutionPath(request.getResolutionPath());
        appendPhotoHistory(req, MaintenancePhotoType.FAULT_EVIDENCE, evidenceUrls);

        if (request.getResolutionPath() == FaultResolutionPath.TENANT_SELF_REPAIR) {
            if (request.getSelfRepairDeadline() == null) {
                throw new BusinessException("selfRepairDeadline là bắt buộc khi giao tenant tự sửa");
            }
            if (request.getEstimatedDamageAmount() == null
                    || request.getEstimatedDamageAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("estimatedDamageAmount phải lớn hơn 0 khi giao tenant tự sửa");
            }
            req.setSelfRepairDeadline(request.getSelfRepairDeadline());
            req.setEstimatedDamageAmount(request.getEstimatedDamageAmount());
            req.setStatus(MaintenanceStatus.PENDING_TENANT_REPAIR);
            repository.save(req);
            addTimeline(req, old, MaintenanceStatus.PENDING_TENANT_REPAIR,
                    "Manager xác định lỗi tenant — giao tự sửa trước " + request.getSelfRepairDeadline());
            notifyTenant(req,
                    "Yêu cầu bảo trì — lỗi do khách thuê",
                    "Lý do: " + req.getFaultReason() + ". Bạn cần tự sửa chữa trước "
                            + request.getSelfRepairDeadline()
                            + ". Nếu không sửa, chi phí ước tính "
                            + request.getEstimatedDamageAmount() + "đ sẽ được trừ khi checkout.",
                    "MAINTENANCE_SELF_REPAIR_ASSIGNED");
        } else if (request.getRepairAppointmentAt() != null) {
            UUID managerId = requireManagerId(req);
            validateAndAssertSlotAvailable(
                    managerId, request.getRepairAppointmentAt(), REPAIR_SLOT_MINUTES, req.getId());
            if (request.getEstimatedDamageAmount() != null) {
                req.setEstimatedDamageAmount(request.getEstimatedDamageAmount());
            }
            req.setRepairAppointmentAt(request.getRepairAppointmentAt());
            req.setStatus(MaintenanceStatus.REPAIR_SCHEDULED);
            repository.save(req);
            addTimeline(req, old, MaintenanceStatus.REPAIR_SCHEDULED,
                    "Manager xác định lỗi tenant — đặt lịch sửa " + request.getRepairAppointmentAt());
            notifyTenant(req,
                    "Yêu cầu bảo trì — lỗi do khách thuê, đã đặt lịch sửa",
                    "Lý do: " + req.getFaultReason() + ". Lịch sửa: "
                            + request.getRepairAppointmentAt()
                            + ". Bạn sẽ nhận hóa đơn thanh toán sau khi hoàn tất.",
                    "MAINTENANCE_TENANT_FAULT");
            realtimeEventService.publishMaintenanceEvent(req, RealtimeEventService.EVT_MAINTENANCE_SCHEDULE_CHANGED);
        } else {
            req.setStatus(MaintenanceStatus.TENANT_FAULT);
            if (request.getEstimatedDamageAmount() != null) {
                req.setEstimatedDamageAmount(request.getEstimatedDamageAmount());
            }
            repository.save(req);
            addTimeline(req, old, MaintenanceStatus.TENANT_FAULT,
                    "Manager xác định lỗi tenant — sẽ sửa hộ và thu tiền ngay");
            notifyTenant(req,
                    "Yêu cầu bảo trì — lỗi do khách thuê",
                    "Lý do: " + req.getFaultReason() + ". Manager sẽ sửa hộ. "
                            + "Bạn sẽ nhận hóa đơn thanh toán sau khi hoàn tất.",
                    "MAINTENANCE_TENANT_FAULT");
        }

        realtimeEventService.publishMaintenanceEvent(req, RealtimeEventService.EVT_MAINTENANCE_REJECT_FAULT);
        return convertToResponse(req);
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse reportFault(Long id, MaintenanceReportFaultRequest request) {
        MaintenanceRequest req = findActive(id);
        requireManagerAccess(req);
        requireStatus(req, MaintenanceStatus.OPEN);

        if (request == null || isBlank(request.getFaultReason())) {
            throw new BusinessException("Bắt buộc nhập lý do (faultReason)");
        }

        List<String> evidenceUrls = new ArrayList<>();
        if (request.getFaultEvidenceImages() != null) {
            evidenceUrls.addAll(request.getFaultEvidenceImages().stream()
                    .filter(u -> u != null && !u.isBlank()).toList());
        }
        if (evidenceUrls.isEmpty()) {
            throw new BusinessException("Bắt buộc đính kèm ảnh bằng chứng lỗi tenant (faultEvidenceImages)");
        }

        MaintenanceStatus old = req.getStatus();
        req.setFlowType(MaintenanceFlowType.TENANT_FAULT);
        req.setDamageCause(DamageCause.TENANT_MISUSE);
        req.setFaultReason(request.getFaultReason().trim());
        req.setFaultResolutionPath(null);
        req.setSelfRepairDeadline(null);
        req.setEstimatedDamageAmount(null);
        req.setStatus(MaintenanceStatus.TENANT_FAULT);
        appendPhotoHistory(req, MaintenancePhotoType.FAULT_EVIDENCE, evidenceUrls);
        repository.save(req);
        addTimeline(req, old, MaintenanceStatus.TENANT_FAULT,
                "Manager báo lỗi do khách — chờ admin duyệt");
        notifyAdmins(req,
                "Báo cáo lỗi do khách — cần duyệt",
                "Phiếu #" + req.getId() + " \"" + req.getTitle() + "\": "
                        + req.getFaultReason(),
                "MAINTENANCE_FAULT_REPORTED");
        notifyTenant(req,
                "Manager báo lỗi do khách thuê",
                "Yêu cầu #" + req.getId() + " — đang chờ admin duyệt. Lý do: " + req.getFaultReason(),
                "MAINTENANCE_FAULT_REPORTED");
        realtimeEventService.publishMaintenanceEvent(req, RealtimeEventService.EVT_MAINTENANCE_FAULT_REPORTED);
        return convertToResponse(req);
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse adminReviewFault(Long id, MaintenanceAdminReviewRequest request) {
        MaintenanceRequest req = findActive(id);
        requireAdminAccess();

        if (request == null || request.getApproved() == null) {
            throw new BusinessException("Bắt buộc gửi approved (true/false)");
        }
        if (req.getStatus() != MaintenanceStatus.TENANT_FAULT) {
            throw new BusinessException(
                    "Chỉ duyệt phiếu ở trạng thái TENANT_FAULT. Hiện tại: " + req.getStatus());
        }
        if (req.getFlowType() != MaintenanceFlowType.TENANT_FAULT) {
            throw new BusinessException("Phiếu không thuộc luồng báo lỗi do khách");
        }
        if (isBlank(req.getFaultReason())) {
            throw new BusinessException("Phiếu chưa có báo cáo lỗi do khách");
        }
        if (req.getFaultResolutionPath() != null) {
            throw new BusinessException(
                    "Phiếu đã rẽ nhánh xử lý sửa chữa (luồng cũ), không dùng admin-review");
        }
        if (req.getAdminReviewedAt() != null) {
            throw new BusinessException("Phiếu đã được admin duyệt trước đó");
        }

        CustomUserDetails admin = SecurityUtils.requireCurrentUser();
        req.setAdminReviewedAt(LocalDateTime.now());
        req.setAdminReviewedBy(admin.getId());
        req.setAdminApproved(request.getApproved());
        req.setAdminReviewNote(trimToNull(request.getNote()));
        repository.save(req);

        String decision = Boolean.TRUE.equals(request.getApproved()) ? "Duyệt" : "Không duyệt";
        String note = decision + " báo lỗi do khách"
                + (req.getAdminReviewNote() != null ? ": " + req.getAdminReviewNote() : "");
        addTimeline(req, req.getStatus(), req.getStatus(), note);
        String reviewBody = Boolean.TRUE.equals(request.getApproved())
                ? "Admin đã duyệt báo lỗi phiếu #" + req.getId() + " \"" + req.getTitle() + "\"."
                : "Admin không duyệt báo lỗi phiếu #" + req.getId() + " \"" + req.getTitle() + "\".";
        if (req.getAdminReviewNote() != null) {
            reviewBody += " Ghi chú: " + req.getAdminReviewNote();
        }
        notifyPropertyManager(req,
                Boolean.TRUE.equals(request.getApproved()) ? "Admin đã duyệt báo lỗi" : "Admin không duyệt báo lỗi",
                reviewBody,
                "MAINTENANCE_ADMIN_REVIEWED");
        realtimeEventService.publishMaintenanceEvent(req, RealtimeEventService.EVT_MAINTENANCE_ADMIN_REVIEWED);
        return convertToResponse(req);
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse submitSelfRepair(Long id, MaintenanceSubmitSelfRepairRequest request,
                                                       List<MultipartFile> files) {
        MaintenanceRequest req = findActive(id);
        requireStatus(req, MaintenanceStatus.PENDING_TENANT_REPAIR);
        requireTenantOwner(req);

        List<String> uploaded = storeFiles(req.getId(), files);
        List<String> urls = new ArrayList<>();
        if (request != null && request.getSelfRepairImages() != null) {
            urls.addAll(request.getSelfRepairImages().stream().filter(u -> u != null && !u.isBlank()).toList());
        }
        urls.addAll(uploaded);
        if (urls.isEmpty()) {
            throw new BusinessException("Bắt buộc upload ảnh bằng chứng đã tự sửa (SELF_REPAIR)");
        }

        appendPhotoHistory(req, MaintenancePhotoType.SELF_REPAIR, urls);
        String note = request != null ? trimToNull(request.getNote()) : null;
        addTimeline(req, req.getStatus(), req.getStatus(),
                "Khách thuê gửi bằng chứng đã tự sửa" + (note != null ? ": " + note : ""));
        notifyPropertyManager(req,
                "Khách thuê đã gửi bằng chứng tự sửa",
                "Yêu cầu #" + req.getId() + " — vui lòng kiểm tra và xác nhận.",
                "MAINTENANCE_SELF_REPAIR_SUBMITTED");
        repository.save(req);
        realtimeEventService.publishMaintenanceEvent(req, RealtimeEventService.EVT_MAINTENANCE_SELF_REPAIR_SUBMITTED);
        return convertToResponse(req);
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse verifyRepair(Long id, MaintenanceVerifyRepairRequest request) {
        MaintenanceRequest req = findActive(id);
        requireManagerAccess(req);
        requireStatus(req, MaintenanceStatus.PENDING_TENANT_REPAIR);

        if (!hasPhotoType(req.getId(), MaintenancePhotoType.SELF_REPAIR)) {
            throw new BusinessException("Khách thuê chưa gửi ảnh bằng chứng tự sửa (SELF_REPAIR)");
        }

        if (request != null && request.getVerifyImages() != null && !request.getVerifyImages().isEmpty()) {
            appendPhotoHistory(req, MaintenancePhotoType.AFTER, request.getVerifyImages());
        }

        MaintenanceStatus old = req.getStatus();
        if (request != null && request.isAccepted()) {
            req.setStatus(MaintenanceStatus.CLOSED);
            req.setDoneAt(LocalDateTime.now());
            req.setResolvedAt(LocalDateTime.now());
            repository.save(req);
            restoreRoomStatus(req);
            String note = trimToNull(request.getNote());
            addTimeline(req, old, MaintenanceStatus.CLOSED,
                    "Manager xác nhận khách đã tự sửa xong" + (note != null ? ": " + note : ""));
            notifyTenant(req,
                    "Tự sửa chữa đã được xác nhận",
                    "Yêu cầu #" + req.getId() + " — manager đã xác nhận bạn đã sửa xong.",
                    "MAINTENANCE_COMPLETED");
            realtimeEventService.publishMaintenanceEvent(req, RealtimeEventService.EVT_MAINTENANCE_VERIFY_REPAIR);
            return convertToResponse(req);
        }

        req.setStatus(MaintenanceStatus.OUTSTANDING_DAMAGE);
        repository.save(req);
        createOutstandingDamageRecord(req);
        String rejectNote = request != null ? trimToNull(request.getNote()) : null;
        addTimeline(req, old, MaintenanceStatus.OUTSTANDING_DAMAGE,
                "Manager từ chối kết quả tự sửa / quá hạn" + (rejectNote != null ? ": " + rejectNote : ""));
        notifyTenant(req,
                "Tự sửa chữa chưa đạt",
                "Yêu cầu #" + req.getId() + " — chi phí ước tính "
                        + req.getEstimatedDamageAmount() + "đ sẽ được xử lý khi checkout.",
                "MAINTENANCE_SELF_REPAIR_OVERDUE");
        realtimeEventService.publishMaintenanceEvent(req, RealtimeEventService.EVT_MAINTENANCE_VERIFY_REPAIR);
        return convertToResponse(req);
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse complete(Long id, MaintenanceCompleteRequest request) {
        MaintenanceRequest req = findActive(id);
        requireManagerAccess(req);

        boolean normalFlow = req.getStatus() == MaintenanceStatus.IN_REPAIR;
        boolean managerRepairFault = req.getStatus() == MaintenanceStatus.TENANT_FAULT
                && req.getFaultResolutionPath() == FaultResolutionPath.MANAGER_REPAIR;
        if (!normalFlow && !managerRepairFault) {
            throw new BusinessException(
                    "Yêu cầu phải ở trạng thái IN_REPAIR hoặc TENANT_FAULT (manager sửa hộ). Hiện tại: "
                            + req.getStatus());
        }

        if (request.getAfterImages() != null && !request.getAfterImages().isEmpty()) {
            appendCsv(req, "after", request.getAfterImages());
            appendPhotoHistory(req, MaintenancePhotoType.AFTER, request.getAfterImages());
        }
        if (request.getInvoiceImages() != null && !request.getInvoiceImages().isEmpty()) {
            appendCsv(req, "invoice", request.getInvoiceImages());
            appendPhotoHistory(req, MaintenancePhotoType.INVOICE, request.getInvoiceImages());
        }

        if (isBlank(req.getAfterImageUrls())) {
            throw new BusinessException("Bắt buộc phải có ảnh sau sửa chữa (AFTER)");
        }
        if (isBlank(req.getInvoiceImageUrls())) {
            throw new BusinessException("Bắt buộc phải có ảnh hóa đơn (INVOICE)");
        }

        applyInvoiceOnComplete(req, request);

        MaintenanceStatus old = req.getStatus();
        req.setResolutionNote(trimToNull(request.getResolutionNote()));
        req.setRepairDescription(trimToNull(request.getRepairDescription()));
        req.setStatus(MaintenanceStatus.CLOSED);
        req.setDoneAt(LocalDateTime.now());
        req.setResolvedAt(LocalDateTime.now());
        repository.save(req);
        restoreRoomStatus(req);

        TenantInvoiceResponse issuedInvoice = null;
        if (managerRepairFault) {
            issuedInvoice = issueMaintenanceCharge(req, req.getInvoiceAmount());
        }

        String note = request.getRepairDescription() != null
                ? "Manager hoàn tất sửa chữa: " + request.getRepairDescription()
                : "Manager hoàn tất sửa chữa";
        addTimeline(req, old, MaintenanceStatus.CLOSED, note);
        notifyTenantOnComplete(req, managerRepairFault);
        realtimeEventService.publishMaintenanceEvent(req, RealtimeEventService.EVT_MAINTENANCE_COMPLETED);

        MaintenanceRequestResponse response = convertToResponse(req);
        response.setIssuedInvoice(issuedInvoice);
        return response;
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse cancel(Long id, String reason) {
        MaintenanceRequest req = findActive(id);
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String role = user.getAuthorities().iterator().next().getAuthority();
        boolean tenantSelfCancel = "ROLE_TENANT".equals(role);

        if (tenantSelfCancel) {
            requireTenantOwner(req);
            if (req.getStatus() != MaintenanceStatus.OPEN) {
                throw new BusinessException(
                        "Chỉ hủy được yêu cầu đang chờ (OPEN). Trạng thái hiện tại: " + req.getStatus());
            }
        } else {
            requireManagerAccess(req);
            if (req.getStatus() == MaintenanceStatus.CLOSED || req.getStatus() == MaintenanceStatus.CANCELLED) {
                throw new BusinessException("Không thể hủy yêu cầu ở trạng thái " + req.getStatus());
            }
        }

        MaintenanceStatus old = req.getStatus();
        req.setStatus(MaintenanceStatus.CANCELLED);
        repository.save(req);
        restoreRoomStatus(req);
        String timelineNote = tenantSelfCancel
                ? (isBlank(reason) ? "Khách thuê tự hủy yêu cầu" : reason.trim())
                : (isBlank(reason) ? "Manager hủy yêu cầu" : reason.trim());
        addTimeline(req, old, MaintenanceStatus.CANCELLED, timelineNote);
        if (!tenantSelfCancel) {
            notifyTenant(req,
                    "Yêu cầu bảo trì đã bị huỷ",
                    "Yêu cầu #" + req.getId() + " \"" + req.getTitle() + "\" đã bị huỷ."
                            + (isBlank(timelineNote) ? "" : " Lý do: " + timelineNote),
                    "MAINTENANCE_CANCELLED");
            realtimeEventService.publishMaintenanceEvent(req,
                    RealtimeEventService.EVT_MAINTENANCE_CANCELLED_BY_MANAGER);
        } else {
            notifyPropertyManager(req,
                    "Khách thuê đã huỷ yêu cầu bảo trì",
                    "Yêu cầu #" + req.getId() + " \"" + req.getTitle() + "\" đã bị khách huỷ."
                            + (isBlank(timelineNote) ? "" : " Lý do: " + timelineNote),
                    "MAINTENANCE_CANCELLED");
            realtimeEventService.publishMaintenanceEvent(req,
                    RealtimeEventService.EVT_MAINTENANCE_CANCELLED_BY_TENANT);
        }
        return convertToResponse(req);
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse uploadPhotos(Long id, List<MultipartFile> files, String type) {
        MaintenanceRequest req = findActive(id);
        assertCanUploadPhotos(req, type);
        List<String> newUrls = storeFiles(id, files);
        if (newUrls.isEmpty()) {
            return convertToResponse(req);
        }

        MaintenancePhotoType photoType = parsePhotoType(type);
        switch (photoType) {
            case BEFORE -> appendCsv(req, "before", newUrls);
            case AFTER -> appendCsv(req, "after", newUrls);
            case INVOICE -> appendCsv(req, "invoice", newUrls);
            case FAULT_EVIDENCE, SELF_REPAIR -> { /* chỉ lưu history */ }
        }
        appendPhotoHistory(req, photoType, newUrls);
        repository.save(req);
        return convertToResponse(req);
    }

    @Override
    public List<OutstandingDamageResponse> getOutstandingDamages(Long propertyId, Long tenantContractId) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String role = user.getAuthorities().iterator().next().getAuthority();

        List<OutstandingDamageRecord> records = outstandingDamageRecordRepository
                .findByResolvedAtCheckoutFalseOrderByCreatedAtDesc();

        return records.stream()
                .filter(r -> tenantContractId == null || tenantContractId.equals(r.getTenantContractId()))
                .filter(r -> {
                    if (propertyId == null) {
                        return true;
                    }
                    return repository.findById(r.getMaintenanceRequestId())
                            .map(m -> m.getProperty() != null && propertyId.equals(m.getProperty().getId()))
                            .orElse(false);
                })
                .filter(r -> {
                    if (!"ROLE_MANAGER".equals(role)) {
                        return true;
                    }
                    return repository.findById(r.getMaintenanceRequestId())
                            .map(m -> m.getProperty() != null
                                    && user.getId().equals(m.getProperty().getOperationManagerId()))
                            .orElse(false);
                })
                .map(this::toOutstandingDamageResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public int processOverdueSelfRepairs() {
        List<MaintenanceRequest> overdue = repository
                .findByStatusAndSelfRepairDeadlineBeforeAndDeletedFalse(
                        MaintenanceStatus.PENDING_TENANT_REPAIR, LocalDate.now());
        int count = 0;
        for (MaintenanceRequest req : overdue) {
            if (!hasPhotoType(req.getId(), MaintenancePhotoType.SELF_REPAIR)) {
                MaintenanceStatus old = req.getStatus();
                req.setStatus(MaintenanceStatus.OUTSTANDING_DAMAGE);
                repository.save(req);
                createOutstandingDamageRecord(req);
                addTimeline(req, old, MaintenanceStatus.OUTSTANDING_DAMAGE,
                        "Quá hạn tự sửa — ghi nhận thiệt hại chờ checkout");
                notifyTenant(req,
                        "Quá hạn tự sửa chữa",
                        "Yêu cầu #" + req.getId() + " — chi phí ước tính "
                                + req.getEstimatedDamageAmount() + "đ sẽ được xử lý khi checkout.",
                        "MAINTENANCE_SELF_REPAIR_OVERDUE");
                notifyPropertyManager(req,
                        "Khách thuê quá hạn tự sửa",
                        "Yêu cầu #" + req.getId() + " đã quá hạn tự sửa.",
                        "MAINTENANCE_SELF_REPAIR_OVERDUE");
                count++;
            }
        }
        return count;
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse rescheduleVisit(Long id, MaintenanceRescheduleVisitRequest request) {
        MaintenanceRequest req = findActive(id);
        assertCanRescheduleVisit(req);
        requireStatus(req, MaintenanceStatus.OPEN);
        if (req.getVisitArrivalConfirmedAt() != null) {
            throw new BusinessException("Không thể đổi lịch hẹn xem sau khi manager đã xác nhận có mặt");
        }
        if (req.getVisitAppointmentAt() == null) {
            throw new BusinessException("Phiếu này không có lịch hẹn xem để đổi");
        }
        assertStillBeforeAppointmentDay(req.getVisitAppointmentAt());
        if (request == null || request.getVisitAppointmentAt() == null) {
            throw new BusinessException("visitAppointmentAt là bắt buộc");
        }
        UUID managerId = requireManagerId(req);
        validateAndAssertSlotAvailable(
                managerId, request.getVisitAppointmentAt(), VISIT_SLOT_MINUTES, req.getId());

        MaintenanceStatus old = req.getStatus();
        LocalDateTime previous = req.getVisitAppointmentAt();
        req.setVisitAppointmentAt(request.getVisitAppointmentAt());
        repository.save(req);
        addTimeline(req, old, old,
                "Đổi lịch hẹn xem: " + previous + " → " + request.getVisitAppointmentAt());
        notifyPropertyManager(req,
                "Đổi lịch hẹn xem bảo trì",
                "Yêu cầu #" + req.getId() + " đổi lịch xem sang " + request.getVisitAppointmentAt(),
                "MAINTENANCE_SCHEDULE_CHANGED");
        notifyTenant(req,
                "Đổi lịch hẹn xem bảo trì",
                "Yêu cầu #" + req.getId() + " đổi lịch xem sang " + request.getVisitAppointmentAt(),
                "MAINTENANCE_SCHEDULE_CHANGED");
        realtimeEventService.publishMaintenanceEvent(req, RealtimeEventService.EVT_MAINTENANCE_SCHEDULE_CHANGED);
        return convertToResponse(req);
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse confirmArrival(Long id) {
        MaintenanceRequest req = findActive(id);
        requireManagerAccess(req);
        requireStatus(req, MaintenanceStatus.OPEN);
        if (req.getVisitArrivalConfirmedAt() != null) {
            return convertToResponse(req);
        }
        req.setVisitArrivalConfirmedAt(LocalDateTime.now(VN_ZONE));
        repository.save(req);
        addTimeline(req, MaintenanceStatus.OPEN, MaintenanceStatus.OPEN,
                "Manager xác nhận có mặt tại hiện trường");
        notifyTenant(req,
                "Manager đã tới xem sự cố",
                "Yêu cầu #" + req.getId() + " — quản lý đã xác nhận có mặt.",
                "MAINTENANCE_SCHEDULE_CHANGED");
        realtimeEventService.publishMaintenanceEvent(req, RealtimeEventService.EVT_MAINTENANCE_SCHEDULE_CHANGED);
        return convertToResponse(req);
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse rescheduleRepair(Long id, MaintenanceRescheduleRepairRequest request) {
        MaintenanceRequest req = findActive(id);
        requireManagerAccess(req);
        requireStatus(req, MaintenanceStatus.REPAIR_SCHEDULED);
        if (req.getRepairAppointmentAt() == null) {
            throw new BusinessException("Phiếu này không có lịch sửa để đổi");
        }
        assertStillBeforeAppointmentDay(req.getRepairAppointmentAt());
        if (request == null || request.getRepairAppointmentAt() == null) {
            throw new BusinessException("repairAppointmentAt là bắt buộc");
        }
        UUID managerId = requireManagerId(req);
        validateAndAssertSlotAvailable(
                managerId, request.getRepairAppointmentAt(), REPAIR_SLOT_MINUTES, req.getId());

        LocalDateTime previous = req.getRepairAppointmentAt();
        req.setRepairAppointmentAt(request.getRepairAppointmentAt());
        repository.save(req);
        addTimeline(req, MaintenanceStatus.REPAIR_SCHEDULED, MaintenanceStatus.REPAIR_SCHEDULED,
                "Đổi lịch sửa: " + previous + " → " + request.getRepairAppointmentAt());
        notifyTenant(req,
                "Đổi lịch sửa bảo trì",
                "Yêu cầu #" + req.getId() + " đổi lịch sửa sang " + request.getRepairAppointmentAt(),
                "MAINTENANCE_SCHEDULE_CHANGED");
        realtimeEventService.publishMaintenanceEvent(req, RealtimeEventService.EVT_MAINTENANCE_SCHEDULE_CHANGED);
        return convertToResponse(req);
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse startRepair(Long id) {
        MaintenanceRequest req = findActive(id);
        requireManagerAccess(req);
        requireStatus(req, MaintenanceStatus.REPAIR_SCHEDULED);

        MaintenanceStatus old = req.getStatus();
        MaintenanceStatus next = req.getFlowType() == MaintenanceFlowType.TENANT_FAULT
                ? MaintenanceStatus.TENANT_FAULT
                : MaintenanceStatus.IN_REPAIR;
        req.setStatus(next);
        req.setRepairStartedAt(LocalDateTime.now(VN_ZONE));
        if (req.getAcknowledgedAt() == null) {
            req.setAcknowledgedAt(LocalDateTime.now(VN_ZONE));
        }
        markRoomMaintenance(req);
        repository.save(req);
        addTimeline(req, old, next, "Manager bắt đầu sửa chữa");
        notifyTenant(req,
                "Bắt đầu sửa chữa",
                "Yêu cầu #" + req.getId() + " \"" + req.getTitle() + "\" đang được sửa chữa.",
                "MAINTENANCE_SCHEDULE_CHANGED");
        realtimeEventService.publishMaintenanceEvent(req, RealtimeEventService.EVT_MAINTENANCE_SCHEDULE_CHANGED);
        return convertToResponse(req);
    }

    @Override
    public List<ManagerAvailabilitySlotResponse> getManagerAvailability(
            Long propertyId, UUID managerId, LocalDateTime from, LocalDateTime to) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        UUID resolvedManagerId = managerId;

        if (propertyId != null) {
            Property property = propertyRepository.findById(propertyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bất động sản không tồn tại"));
            if (property.getOperationManagerId() == null) {
                throw new BusinessException(
                        "Nhà chưa được gán quản lý vận hành — chưa có lịch trống để xem");
            }
            resolvedManagerId = property.getOperationManagerId();
        }
        if (resolvedManagerId == null) {
            String role = user.getAuthorities().iterator().next().getAuthority();
            if ("ROLE_MANAGER".equals(role)) {
                resolvedManagerId = user.getId();
            } else {
                throw new BusinessException("Cần gửi propertyId hoặc managerId");
            }
        }

        LocalDateTime rangeFrom = from != null ? from : LocalDate.now(VN_ZONE).atStartOfDay();
        LocalDateTime rangeTo = to != null ? to : rangeFrom.plusDays(14);

        List<MaintenanceRequest> slots =
                repository.findActiveAppointmentSlotsByManager(resolvedManagerId, null);
        List<ManagerAvailabilitySlotResponse> result = new ArrayList<>();
        for (MaintenanceRequest r : slots) {
            if (r.getStatus() == MaintenanceStatus.OPEN && r.getVisitAppointmentAt() != null) {
                LocalDateTime start = r.getVisitAppointmentAt();
                LocalDateTime end = start.plusMinutes(VISIT_SLOT_MINUTES);
                if (overlapsRange(start, end, rangeFrom, rangeTo)) {
                    result.add(toAvailabilitySlot(r, "VISIT", start, end));
                }
            } else if (r.getStatus() == MaintenanceStatus.REPAIR_SCHEDULED
                    && r.getRepairAppointmentAt() != null) {
                LocalDateTime start = r.getRepairAppointmentAt();
                LocalDateTime end = start.plusMinutes(REPAIR_SLOT_MINUTES);
                if (overlapsRange(start, end, rangeFrom, rangeTo)) {
                    result.add(toAvailabilitySlot(r, "REPAIR", start, end));
                }
            }
        }
        result.sort(Comparator.comparing(ManagerAvailabilitySlotResponse::getStart));
        return result;
    }

    @Override
    @Transactional
    public int autoCancelNoShowVisits() {
        LocalDateTime deadline = LocalDateTime.now(VN_ZONE).minusHours(2);
        List<MaintenanceRequest> noShows = repository.findNoShowVisitsForAutoCancel(deadline);
        int count = 0;
        for (MaintenanceRequest req : noShows) {
            MaintenanceStatus old = req.getStatus();
            req.setStatus(MaintenanceStatus.CANCELLED);
            repository.save(req);
            restoreRoomStatus(req);
            addTimeline(req, old, MaintenanceStatus.CANCELLED,
                    "Tự động huỷ — quá 2 giờ chưa xác nhận có mặt");
            notifyTenant(req,
                    "Yêu cầu bảo trì đã tự huỷ",
                    "Yêu cầu #" + req.getId() + " \"" + req.getTitle()
                            + "\" đã tự huỷ vì quá 2 giờ sau lịch hẹn chưa xác nhận có mặt.",
                    "MAINTENANCE_CANCELLED");
            notifyPropertyManager(req,
                    "Yêu cầu bảo trì tự huỷ — không xác nhận có mặt",
                    "Yêu cầu #" + req.getId() + " đã tự huỷ (quá 2 giờ sau lịch hẹn xem).",
                    "MAINTENANCE_CANCELLED");
            realtimeEventService.publishMaintenanceEvent(req,
                    RealtimeEventService.EVT_MAINTENANCE_SCHEDULE_CHANGED);
            count++;
        }
        return count;
    }

    @Override
    @Transactional
    public void markOutstandingDamageResolved(Long maintenanceRequestId, Long checkoutDamageItemId,
                                              BigDecimal actualAmount) {
        List<OutstandingDamageRecord> records = outstandingDamageRecordRepository
                .findByMaintenanceRequestIdAndResolvedAtCheckoutFalse(maintenanceRequestId);
        for (OutstandingDamageRecord record : records) {
            record.setResolvedAtCheckout(true);
            record.setCheckoutDamageItemId(checkoutDamageItemId);
            if (actualAmount != null && actualAmount.compareTo(BigDecimal.ZERO) > 0) {
                record.setEstimatedAmount(actualAmount);
            }
            outstandingDamageRecordRepository.save(record);
        }
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void processOverdueSelfRepairsTask() {
        processOverdueSelfRepairs();
    }

    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void autoCancelNoShowVisitsTask() {
        autoCancelNoShowVisits();
    }

    // ---------- helpers ----------

    private void requireVisitArrivalConfirmed(MaintenanceRequest req) {
        // Phiếu cũ (trước khi có lịch hẹn) bỏ qua bước xác nhận có mặt.
        if (req.getVisitAppointmentAt() == null) {
            return;
        }
        if (req.getVisitArrivalConfirmedAt() == null) {
            throw new BusinessException(
                    "Cần xác nhận có mặt (confirm-arrival) trước khi duyệt / báo lỗi khách");
        }
    }

    private UUID requireManagerId(MaintenanceRequest req) {
        UUID managerId = req.getProperty() != null ? req.getProperty().getOperationManagerId() : null;
        if (managerId == null) {
            throw new BusinessException(
                    "Nhà chưa được gán quản lý vận hành — không thể đặt/đổi lịch hẹn");
        }
        return managerId;
    }

    private void assertCanRescheduleVisit(MaintenanceRequest req) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String role = user.getAuthorities().iterator().next().getAuthority();
        if ("ROLE_TENANT".equals(role)) {
            requireTenantOwner(req);
        } else {
            requireManagerAccess(req);
        }
    }

    private void assertStillBeforeAppointmentDay(LocalDateTime appointmentAt) {
        LocalDate today = LocalDate.now(VN_ZONE);
        if (!today.isBefore(appointmentAt.toLocalDate())) {
            throw new BusinessException(
                    "Không thể đổi lịch trong ngày hẹn hoặc sau ngày hẹn — chỉ được huỷ");
        }
    }

    private void validateAndAssertSlotAvailable(
            UUID managerId, LocalDateTime start, int durationMinutes, Long excludeRequestId) {
        if (start == null) {
            throw new BusinessException("Thời điểm hẹn là bắt buộc");
        }
        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        if (!start.isAfter(now)) {
            throw new BusinessException("Thời điểm hẹn phải ở tương lai");
        }
        LocalDateTime end = start.plusMinutes(durationMinutes);
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = end.toLocalTime();
        // Slot kết thúc đúng 18:00 vẫn hợp lệ; tràn qua ngày hoặc sau 18:00 thì không.
        if (startTime.isBefore(BUSINESS_START)
                || end.toLocalDate().isAfter(start.toLocalDate())
                || endTime.isAfter(BUSINESS_END)) {
            throw new BusinessException(
                    "Chỉ đặt lịch trong giờ hành chính 07:00–18:00 (kể cả giờ kết thúc khung)");
        }

        List<MaintenanceRequest> existing =
                repository.findActiveAppointmentSlotsByManager(managerId, excludeRequestId);
        for (MaintenanceRequest other : existing) {
            LocalDateTime otherStart;
            int otherDuration;
            String type;
            if (other.getStatus() == MaintenanceStatus.OPEN && other.getVisitAppointmentAt() != null) {
                otherStart = other.getVisitAppointmentAt();
                otherDuration = VISIT_SLOT_MINUTES;
                type = "VISIT";
            } else if (other.getStatus() == MaintenanceStatus.REPAIR_SCHEDULED
                    && other.getRepairAppointmentAt() != null) {
                otherStart = other.getRepairAppointmentAt();
                otherDuration = REPAIR_SLOT_MINUTES;
                type = "REPAIR";
            } else {
                continue;
            }
            LocalDateTime otherEnd = otherStart.plusMinutes(otherDuration);
            if (start.isBefore(otherEnd) && end.isAfter(otherStart)) {
                throw new ConflictException(
                        "Trùng lịch " + type + " với phiếu M-" + other.getId()
                                + " (" + otherStart + " – " + otherEnd + ")");
            }
        }
    }

    private static boolean overlapsRange(
            LocalDateTime start, LocalDateTime end, LocalDateTime rangeFrom, LocalDateTime rangeTo) {
        return start.isBefore(rangeTo) && end.isAfter(rangeFrom);
    }

    private ManagerAvailabilitySlotResponse toAvailabilitySlot(
            MaintenanceRequest r, String type, LocalDateTime start, LocalDateTime end) {
        return ManagerAvailabilitySlotResponse.builder()
                .requestId(r.getId())
                .requestCode("M-" + r.getId())
                .type(type)
                .start(start)
                .end(end)
                .propertyName(r.getProperty() != null ? r.getProperty().getPropertyName() : null)
                .roomNumber(r.getRoom() != null ? r.getRoom().getRoomNumber() : null)
                .build();
    }

    private void applyInvoiceOnComplete(MaintenanceRequest req, MaintenanceCompleteRequest request) {
        if (isBlank(request.getInvoiceVendor())) {
            throw new BusinessException("invoiceVendor là bắt buộc");
        }
        if (request.getInvoiceDate() == null) {
            throw new BusinessException("invoiceDate là bắt buộc");
        }
        if (request.getInvoiceAmount() == null || request.getInvoiceAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("invoiceAmount phải lớn hơn 0");
        }
        if (isBlank(request.getRepairDescription())) {
            throw new BusinessException("repairDescription là bắt buộc");
        }
        req.setInvoiceVendor(request.getInvoiceVendor().trim());
        req.setInvoiceNumber(trimToNull(request.getInvoiceNumber()));
        req.setInvoiceDate(request.getInvoiceDate());
        req.setInvoiceAmount(request.getInvoiceAmount());
        req.setRepairDescription(request.getRepairDescription().trim());
    }

    private void createOutstandingDamageRecord(MaintenanceRequest req) {
        if (outstandingDamageRecordRepository.existsByMaintenanceRequestId(req.getId())) {
            return;
        }
        TenantContract contract = resolveActiveContract(req);
        String label = req.getTitle() != null ? req.getTitle() : "Thiết bị hư #" + req.getId();
        List<String> photos = loadPhotoUrlsByType(req.getId(), MaintenancePhotoType.FAULT_EVIDENCE);
        photos.addAll(loadPhotoUrlsByType(req.getId(), MaintenancePhotoType.SELF_REPAIR));

        OutstandingDamageRecord record = OutstandingDamageRecord.builder()
                .maintenanceRequestId(req.getId())
                .tenantContractId(contract.getId())
                .equipmentId(req.getEquipmentId())
                .label(label)
                .estimatedAmount(req.getEstimatedDamageAmount() != null
                        ? req.getEstimatedDamageAmount()
                        : BigDecimal.ZERO)
                .note(req.getFaultReason())
                .photos(new ArrayList<>(photos))
                .build();
        outstandingDamageRecordRepository.save(record);
    }

    private TenantInvoiceResponse issueMaintenanceCharge(MaintenanceRequest req, BigDecimal amount) {
        TenantContract contract = resolveActiveContract(req);
        String note = "Bồi thường bảo trì #" + req.getId()
                + (req.getTitle() != null ? " — " + req.getTitle() : "");
        return tenantPendingChargeService.createAndIssueMaintenanceCharge(
                contract, amount, req.getId(), note);
    }

    private TenantContract resolveActiveContract(MaintenanceRequest req) {
        UUID tenantUserId = req.getTenant() != null && req.getTenant().getUser() != null
                ? req.getTenant().getUser().getId()
                : null;
        if (tenantUserId == null) {
            throw new BusinessException("Ticket không có thông tin khách thuê");
        }

        if (req.getTenantContract() != null) {
            TenantContract contract = req.getTenantContract();
            if (contract.getTenant() == null
                    || contract.getTenant().getUser() == null
                    || !tenantUserId.equals(contract.getTenant().getUser().getId())) {
                throw new BusinessException("Hợp đồng gắn với ticket không thuộc về khách thuê hiện tại");
            }
            return contract;
        }

        TenantContract contract = null;
        if (req.getRoom() != null) {
            contract = tenantContractRepository
                    .findByRoomIdAndStatus(req.getRoom().getId(), ContractStatus.ACTIVE)
                    .orElse(null);
            if (contract == null && req.getProperty() != null) {
                contract = tenantContractRepository
                        .findByPropertyIdAndRoomIsNullAndStatus(req.getProperty().getId(), ContractStatus.ACTIVE)
                        .orElse(null);
            }
        } else if (req.getProperty() != null) {
            contract = tenantContractRepository
                    .findByPropertyIdAndRoomIsNullAndStatus(req.getProperty().getId(), ContractStatus.ACTIVE)
                    .orElse(null);
        }

        if (contract == null
                || contract.getTenant() == null
                || contract.getTenant().getUser() == null
                || !tenantUserId.equals(contract.getTenant().getUser().getId())) {
            throw new BusinessException(
                    "Không tìm thấy hợp đồng ACTIVE của khách thuê để tạo hoá đơn bồi thường");
        }
        return contract;
    }

    private MaintenanceRequest findActive(Long id) {
        MaintenanceRequest req = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy request"));
        if (req.isDeleted()) {
            throw new ResourceNotFoundException("Không tìm thấy request");
        }
        return req;
    }

    private TenantContract assertTenantOwnsActiveUnit(UUID tenantUserId, Room room, Long propertyId) {
        Long roomId = room.getId();
        TenantContract contract = tenantContractRepository.findByTenantId(tenantUserId).stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                .filter(c -> {
                    if (c.getRoom() != null) {
                        return roomId.equals(c.getRoom().getId());
                    }
                    return propertyId != null && c.getProperty() != null
                            && propertyId.equals(c.getProperty().getId());
                })
                .findFirst()
                .orElse(null);
        if (contract == null) {
            throw new BusinessException(
                    "Bạn chỉ có thể báo sự cố cho phòng thuộc hợp đồng đang hiệu lực của mình");
        }
        return contract;
    }

    private TenantContract assertTenantOwnsActiveWholeHouse(UUID tenantUserId, Long propertyId) {
        TenantContract contract = tenantContractRepository.findByTenantId(tenantUserId).stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                .filter(c -> c.getRoom() == null
                        && c.getProperty() != null
                        && propertyId.equals(c.getProperty().getId()))
                .findFirst()
                .orElse(null);
        if (contract == null) {
            throw new BusinessException(
                    "Bạn không có hợp đồng nguyên căn đang hiệu lực cho bất động sản này");
        }
        return contract;
    }

    private void requireStatus(MaintenanceRequest req, MaintenanceStatus expected) {
        if (req.getStatus() != expected) {
            throw new BusinessException("Yêu cầu phải ở trạng thái " + expected + " (hiện tại: " + req.getStatus() + ")");
        }
    }

    private void requireTenantOwner(MaintenanceRequest req) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        requireTenantOwner(req, user.getId());
    }

    private void requireTenantOwner(MaintenanceRequest req, UUID userId) {
        if (req.getTenant() == null || req.getTenant().getUser() == null
                || !req.getTenant().getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Yêu cầu sửa chữa này không thuộc về bạn");
        }
    }

    private void requireManagerAccess(MaintenanceRequest req) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String role = user.getAuthorities().iterator().next().getAuthority();
        if ("ROLE_ADMIN".equals(role)) {
            return;
        }
        if (!"ROLE_MANAGER".equals(role)) {
            throw new AccessDeniedException("Chỉ quản lý vận hành mới được thao tác trên yêu cầu sửa chữa này");
        }
        UUID opManager = req.getProperty() != null ? req.getProperty().getOperationManagerId() : null;
        if (!user.getId().equals(opManager)) {
            throw new AccessDeniedException("Bạn không quản lý tòa nhà của yêu cầu sửa chữa này");
        }
    }

    private void requireAdminAccess() {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String role = user.getAuthorities().iterator().next().getAuthority();
        if (!"ROLE_ADMIN".equals(role)) {
            throw new AccessDeniedException("Chỉ admin mới được thao tác này");
        }
    }

    private void assertCanViewRequest(MaintenanceRequest req) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String role = user.getAuthorities().iterator().next().getAuthority();
        if ("ROLE_TENANT".equals(role)) {
            requireTenantOwner(req);
        } else if ("ROLE_MANAGER".equals(role)) {
            requireManagerAccess(req);
        }
    }

    private void assertCanUploadPhotos(MaintenanceRequest req, String type) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String role = user.getAuthorities().iterator().next().getAuthority();
        MaintenancePhotoType photoType = parsePhotoType(type);
        if ("ROLE_TENANT".equals(role)) {
            requireTenantOwner(req);
            if (photoType != MaintenancePhotoType.BEFORE && photoType != MaintenancePhotoType.SELF_REPAIR) {
                throw new BusinessException("Khách thuê chỉ được upload ảnh BEFORE hoặc SELF_REPAIR");
            }
        } else {
            requireManagerAccess(req);
        }
    }

    private void markRoomMaintenance(MaintenanceRequest req) {
        if (req.getRoom() != null) {
            req.getRoom().setStatus(RoomStatus.MAINTENANCE);
            roomRepository.save(req.getRoom());
        }
    }

    private void restoreRoomStatus(MaintenanceRequest req) {
        if (req.getRoom() != null) {
            boolean stillHasOpenTicket = repository.existsByRoomIdAndStatusNotInAndIdNotAndDeletedFalse(
                    req.getRoom().getId(),
                    List.of(MaintenanceStatus.CLOSED, MaintenanceStatus.CANCELLED, MaintenanceStatus.OUTSTANDING_DAMAGE),
                    req.getId());
            if (stillHasOpenTicket) {
                return;
            }
            boolean hasActiveContract = tenantContractRepository.existsByRoomIdAndStatus(
                    req.getRoom().getId(), ContractStatus.ACTIVE);
            req.getRoom().setStatus(hasActiveContract ? RoomStatus.RENTED : RoomStatus.AVAILABLE);
            roomRepository.save(req.getRoom());
        }
    }

    private void addTimeline(MaintenanceRequest req, MaintenanceStatus oldStatus, MaintenanceStatus newStatus, String note) {
        CustomUserDetails user = null;
        try {
            user = SecurityUtils.requireCurrentUser();
        } catch (Exception ignored) {
            // system / cron
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
    }

    private void notifyTenantOnComplete(MaintenanceRequest req, boolean tenantCharge) {
        StringBuilder body = new StringBuilder();
        body.append("Yêu cầu #").append(req.getId());
        if (req.getTitle() != null && !req.getTitle().isBlank()) {
            body.append(" \"").append(req.getTitle()).append('"');
        }
        body.append(" đã hoàn tất.");
        if (req.getRepairDescription() != null) {
            body.append(" Mô tả: ").append(req.getRepairDescription()).append('.');
        }
        if (tenantCharge) {
            body.append(" Có khoản thanh toán ").append(req.getInvoiceAmount()).append("đ cần thanh toán.");
        } else if (req.getInvoiceAmount() != null) {
            body.append(" Chi phí tham khảo: ").append(req.getInvoiceAmount()).append("đ (chủ nhà chi trả).");
        }
        body.append(" Nếu chưa hài lòng, tạo yêu cầu mới trong app.");
        notifyTenant(req, "Bảo trì đã hoàn tất — " + req.getTitle(), body.toString(), "MAINTENANCE_COMPLETED");
        if (tenantCharge) {
            notifyTenant(req,
                    "Đã phát hành hóa đơn sửa chữa",
                    "Yêu cầu #" + req.getId() + " — khoản " + req.getInvoiceAmount() + "đ cần thanh toán.",
                    "MAINTENANCE_CHARGE_ISSUED");
        }
    }

    private void notifyTenant(MaintenanceRequest req, String title, String body, String type) {
        if (req.getTenant() == null || req.getTenant().getUser() == null) {
            return;
        }
        saveAndPush(req.getTenant().getUser().getId(), title, body, type,
                "MaintenanceDetail", "requestId", req.getId());
    }

    private void notifyPropertyManager(MaintenanceRequest req, String title, String body, String type) {
        UUID managerId = req.getProperty() != null ? req.getProperty().getOperationManagerId() : null;
        if (managerId != null) {
            saveAndPush(managerId, title, body, type,
                    "MaintenanceTicketDetail", "ticketId", req.getId());
            return;
        }
        // Chưa gán operationManager → fan-out mọi manager ACTIVE (khớp routing WS)
        userRepository.findByRoleAndStatus(Role.ROLE_MANAGER, UserStatus.ACTIVE)
                .forEach(manager -> saveAndPush(manager.getId(), title, body, type,
                        "MaintenanceTicketDetail", "ticketId", req.getId()));
    }

    /** Admin: ghi khay chuông (web) + push nếu có token. */
    private void notifyAdmins(MaintenanceRequest req, String title, String body, String type) {
        userRepository.findByRoleAndStatus(Role.ROLE_ADMIN, UserStatus.ACTIVE)
                .forEach(admin -> saveAndPush(admin.getId(), title, body, type,
                        "MaintenanceFaultReview", "requestId", req.getId()));
    }

    private void saveAndPush(UUID userId, String title, String body, String type,
                             String screen, String idKey, Long requestId) {
        String paramsJson = "{\"" + idKey + "\":" + requestId + "}";
        notificationRepository.save(Notification.builder()
                .userId(userId)
                .title(title)
                .content(body)
                .type(type)
                .screen(screen)
                .paramsJson(paramsJson)
                .read(false)
                .build());
        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        data.put("screen", screen);
        data.put(idKey, requestId);
        data.put("params", Map.of(idKey, requestId));
        userPushTokenService.sendToUser(userId, title, body, data);
    }

    private List<String> storeFiles(Long requestId, List<MultipartFile> files) {
        List<String> urls = new ArrayList<>();
        if (files == null) {
            return urls;
        }
        String prefix = "MAINT-" + requestId;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            try {
                urls.add(imageStorage.store(prefix, file.getOriginalFilename(), file.getBytes()));
            } catch (Exception e) {
                throw new BusinessException("Upload ảnh thất bại: " + e.getMessage());
            }
        }
        return urls;
    }

    private void appendCsv(MaintenanceRequest req, String field, List<String> urls) {
        String joined = joinUrls(urls);
        if (joined == null) {
            return;
        }
        switch (field) {
            case "after" -> {
                String existing = req.getAfterImageUrls();
                req.setAfterImageUrls(isBlank(existing) ? joined : existing + "," + joined);
            }
            case "before" -> {
                String existing = req.getBeforeImageUrls();
                req.setBeforeImageUrls(isBlank(existing) ? joined : existing + "," + joined);
            }
            case "invoice" -> {
                String existing = req.getInvoiceImageUrls();
                req.setInvoiceImageUrls(isBlank(existing) ? joined : existing + "," + joined);
            }
        }
    }

    private void appendPhotoHistory(MaintenanceRequest req, MaintenancePhotoType type, List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (String url : urls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            String trimmed = url.trim();
            if (maintenanceImageRepository.existsByMaintenanceRequestIdAndImageUrlAndType(
                    req.getId(), trimmed, type)) {
                continue;
            }
            maintenanceImageRepository.save(MaintenanceImage.builder()
                    .maintenanceRequest(req)
                    .imageUrl(trimmed)
                    .type(type)
                    .createdAt(now)
                    .build());
        }
    }

    private boolean hasPhotoType(Long requestId, MaintenancePhotoType type) {
        return !maintenanceImageRepository.findByMaintenanceRequestIdOrderByCreatedAtAsc(requestId)
                .stream()
                .filter(img -> img.getType() == type)
                .toList()
                .isEmpty();
    }

    private List<String> loadPhotoUrlsByType(Long requestId, MaintenancePhotoType type) {
        return maintenanceImageRepository.findByMaintenanceRequestIdOrderByCreatedAtAsc(requestId)
                .stream()
                .filter(img -> img.getType() == type)
                .map(MaintenanceImage::getImageUrl)
                .collect(Collectors.toList());
    }

    private List<MaintenancePhotoHistoryResponse> loadPhotoHistory(Long requestId) {
        return maintenanceImageRepository.findByMaintenanceRequestIdOrderByCreatedAtAsc(requestId)
                .stream()
                .map(img -> MaintenancePhotoHistoryResponse.builder()
                        .type(img.getType())
                        .url(img.getImageUrl())
                        .createdAt(img.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private MaintenanceBillingHint resolveBillingHint(MaintenanceRequest req) {
        if (req.getStatus() == MaintenanceStatus.OUTSTANDING_DAMAGE) {
            return MaintenanceBillingHint.DEPOSIT_DEDUCTION_PENDING;
        }
        if (req.getFlowType() == MaintenanceFlowType.TENANT_FAULT
                && req.getFaultResolutionPath() == FaultResolutionPath.MANAGER_REPAIR
                && req.getStatus() == MaintenanceStatus.CLOSED) {
            return MaintenanceBillingHint.TENANT_CHARGE_PENDING;
        }
        if (req.getStatus() == MaintenanceStatus.CLOSED && req.getFlowType() == MaintenanceFlowType.NORMAL_WEAR) {
            return MaintenanceBillingHint.HOST_PAID;
        }
        if (req.getFlowType() == MaintenanceFlowType.TENANT_FAULT
                && req.getFaultResolutionPath() == FaultResolutionPath.TENANT_SELF_REPAIR
                && req.getStatus() == MaintenanceStatus.PENDING_TENANT_REPAIR) {
            return MaintenanceBillingHint.DEPOSIT_DEDUCTION_PENDING;
        }
        return MaintenanceBillingHint.NONE;
    }

    private OutstandingDamageResponse toOutstandingDamageResponse(OutstandingDamageRecord r) {
        return OutstandingDamageResponse.builder()
                .id(r.getId())
                .maintenanceRequestId(r.getMaintenanceRequestId())
                .tenantContractId(r.getTenantContractId())
                .equipmentId(r.getEquipmentId())
                .label(r.getLabel())
                .estimatedAmount(r.getEstimatedAmount())
                .note(r.getNote())
                .photos(r.getPhotos())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private void validateNoPurchaseIntent(String title, String description) {
        String combined = (title + " " + (description != null ? description : "")).toLowerCase(Locale.ROOT);
        for (String keyword : PURCHASE_KEYWORDS) {
            if (combined.contains(keyword)) {
                throw new BusinessException(
                        "Yêu cầu mua mới/thay thế không thuộc phạm vi bảo trì. Vui lòng liên hệ quản lý.");
            }
        }
    }

    private static String resolveCreateCategory(Long equipmentId, String rawCategory) {
        if (equipmentId != null) {
            if (rawCategory == null || rawCategory.isBlank()) {
                return null;
            }
            return parseCategoryRequired(rawCategory);
        }
        if (rawCategory == null || rawCategory.isBlank()) {
            throw new BusinessException(
                    "Danh mục hư hỏng (category) là bắt buộc. Chọn: APPLIANCE, FURNITURE, PLUMBING, ELECTRICAL");
        }
        return parseCategoryRequired(rawCategory);
    }

    private static String parseCategoryRequired(String category) {
        if (category == null || category.isBlank()) {
            throw new BusinessException("Danh mục sự cố (category) là bắt buộc");
        }
        try {
            return MaintenanceCategory.valueOf(category.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "Danh mục không hợp lệ. Chọn một trong: APPLIANCE, FURNITURE, PLUMBING, ELECTRICAL");
        }
    }

    private static MaintenanceStatus parseStatus(String status) {
        try {
            return MaintenanceStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Trạng thái không hợp lệ: " + status);
        }
    }

    private static MaintenancePhotoType parsePhotoType(String type) {
        try {
            return MaintenancePhotoType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Type phải là BEFORE, AFTER, INVOICE, FAULT_EVIDENCE hoặc SELF_REPAIR");
        }
    }

    private static String parsePriorityOptional(String priority) {
        if (priority == null || priority.isBlank()) {
            return null;
        }
        try {
            return MaintenancePriority.valueOf(priority.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "Mức ưu tiên không hợp lệ. Chọn một trong: LOW, MEDIUM, HIGH, URGENT");
        }
    }

    private static String joinUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        List<String> clean = urls.stream().filter(u -> u != null && !u.isBlank()).toList();
        return clean.isEmpty() ? null : String.join(",", clean);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private MaintenanceRequestResponse convertToResponse(MaintenanceRequest req) {
        List<MaintenancePhotoHistoryResponse> photoHistory = loadPhotoHistory(req.getId());
        List<String> before = splitCsv(req.getBeforeImageUrls());
        List<String> after = splitCsv(req.getAfterImageUrls());
        List<String> invoice = splitCsv(req.getInvoiceImageUrls());
        List<String> faultEvidence = loadPhotoUrlsByType(req.getId(), MaintenancePhotoType.FAULT_EVIDENCE);
        List<String> selfRepair = loadPhotoUrlsByType(req.getId(), MaintenancePhotoType.SELF_REPAIR);

        List<String> all = new ArrayList<>();
        all.addAll(before);
        all.addAll(after);
        all.addAll(invoice);
        all.addAll(faultEvidence);
        all.addAll(selfRepair);

        MaintenanceRequestResponse res = MaintenanceRequestResponse.builder()
                .id(req.getId())
                .requestCode("M-" + req.getId())
                .title(req.getTitle())
                .description(req.getDescription())
                .status(req.getStatus())
                .flowType(req.getFlowType())
                .billingHint(resolveBillingHint(req))
                .category(req.getCategory())
                .priority(req.getPriority())
                .resolvedAt(req.getResolvedAt() != null ? req.getResolvedAt() : req.getDoneAt())
                .resolutionNote(req.getResolutionNote())
                .repairDescription(req.getRepairDescription())
                .invoiceVendor(req.getInvoiceVendor())
                .invoiceNumber(req.getInvoiceNumber())
                .invoiceDate(req.getInvoiceDate())
                .invoiceAmount(req.getInvoiceAmount())
                .previousRequestId(req.getPreviousRequestId())
                .damageCause(req.getDamageCause())
                .faultReason(req.getFaultReason())
                .faultResolutionPath(req.getFaultResolutionPath())
                .selfRepairDeadline(req.getSelfRepairDeadline())
                .estimatedDamageAmount(req.getEstimatedDamageAmount())
                .adminReviewedAt(req.getAdminReviewedAt())
                .adminReviewedBy(req.getAdminReviewedBy())
                .adminApproved(req.getAdminApproved())
                .adminReviewNote(req.getAdminReviewNote())
                .beforeImages(before)
                .afterImages(after)
                .invoiceImages(invoice)
                .faultEvidenceImages(faultEvidence)
                .selfRepairImages(selfRepair)
                .images(all)
                .photoHistory(photoHistory)
                .acknowledgedAt(req.getAcknowledgedAt())
                .visitAppointmentAt(req.getVisitAppointmentAt())
                .visitArrivalConfirmedAt(req.getVisitArrivalConfirmedAt())
                .repairAppointmentAt(req.getRepairAppointmentAt())
                .repairStartedAt(req.getRepairStartedAt())
                .createdAt(req.getCreatedAt())
                .updatedAt(req.getUpdatedAt())
                .build();

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
            if (req.getProperty().getOperationManagerId() != null) {
                res.setAssignedManagerId(req.getProperty().getOperationManagerId());
                userRepository.findById(req.getProperty().getOperationManagerId()).ifPresent(manager ->
                        res.setAssignedManagerName(manager.getFullName()));
            }
        }
        if (req.getEquipmentId() != null) {
            equipmentRepository.findById(req.getEquipmentId()).ifPresent(eq -> {
                res.setEquipmentId(eq.getId());
                res.setEquipmentName(eq.getCatalog() != null ? eq.getCatalog().getName() : null);
            });
        }
        if (req.getAdminReviewedBy() != null) {
            userRepository.findById(req.getAdminReviewedBy()).ifPresent(admin ->
                    res.setAdminReviewedByName(admin.getFullName()));
        }

        attachIssuedInvoiceIfPending(req, res);

        List<MaintenanceTimeline> timelines = timelineRepository.findByMaintenanceRequestIdOrderByChangedAtAsc(req.getId());
        res.setTimeline(timelines.stream().map(t -> MaintenanceTimelineResponse.builder()
                .oldStatus(t.getOldStatus() != null ? t.getOldStatus().name() : null)
                .newStatus(t.getNewStatus() != null ? t.getNewStatus().name() : null)
                .note(t.getNote())
                .changedBy(t.getChangedBy() != null ? t.getChangedBy().toString() : null)
                .changedByName(t.getChangedByName())
                .changedAt(t.getChangedAt())
                .build()).collect(Collectors.toList()));

        return res;
    }

    /**
     * Khi billingHint = TENANT_CHARGE_PENDING và hoá đơn chưa thanh toán,
     * luôn trả issuedInvoice trên GET (không chỉ ngay sau complete()).
     */
    private void attachIssuedInvoiceIfPending(MaintenanceRequest req, MaintenanceRequestResponse res) {
        if (res.getBillingHint() != MaintenanceBillingHint.TENANT_CHARGE_PENDING) {
            return;
        }
        List<TenantPendingCharge> charges =
                tenantPendingChargeRepository.findByMaintenanceRequestIdWithInvoice(req.getId());
        for (TenantPendingCharge charge : charges) {
            TenantInvoice invoice = charge.getInvoice();
            if (invoice == null) {
                continue;
            }
            if (invoice.getStatus() == TenantInvoiceStatus.PAID
                    || invoice.getStatus() == TenantInvoiceStatus.CANCELLED) {
                continue;
            }
            res.setIssuedInvoice(TenantInvoiceResponse.builder()
                    .id(invoice.getId())
                    .code(invoice.getCode())
                    .type(invoice.getInvoiceType() != null ? invoice.getInvoiceType().name() : null)
                    .propertyName(invoice.getPropertyName())
                    .roomNumber(invoice.getRoomNumber())
                    .month(invoice.getBillingMonth())
                    .year(invoice.getBillingYear())
                    .billingPeriod(invoice.getBillingPeriod())
                    .totalAmount(invoice.getTotalAmount())
                    .lateFee(invoice.getLateFee())
                    .grandTotal(invoice.getGrandTotal())
                    .status(invoice.getStatus() != null ? invoice.getStatus().name() : null)
                    .dueDate(invoice.getDueDate())
                    .createdAt(invoice.getCreatedAt())
                    .paidAt(invoice.getPaidAt())
                    .payosCheckoutUrl(invoice.getPayosCheckoutUrl())
                    .payosQrCode(invoice.getPayosQrCode())
                    .payosOrderCode(invoice.getPayosOrderCode())
                    .build());
            return;
        }
    }

    private static List<String> splitCsv(String csv) {
        if (isBlank(csv)) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
