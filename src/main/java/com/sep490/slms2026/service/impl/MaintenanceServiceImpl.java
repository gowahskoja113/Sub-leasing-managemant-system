package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.MaintenanceDashboardResponse;
import com.sep490.slms2026.dto.response.MaintenancePhotoHistoryResponse;
import com.sep490.slms2026.dto.response.MaintenanceRequestResponse;
import com.sep490.slms2026.dto.response.MaintenanceTimelineResponse;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.MaintenanceCategory;
import com.sep490.slms2026.enums.MaintenancePhotoType;
import com.sep490.slms2026.enums.MaintenancePriority;
import com.sep490.slms2026.enums.MaintenanceStatus;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.MaintenanceService;
import com.sep490.slms2026.service.PropertyImageStorage;
import com.sep490.slms2026.service.PushNotificationService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaintenanceServiceImpl implements MaintenanceService {

    private final MaintenanceRequestRepository repository;
    private final MaintenanceTimelineRepository timelineRepository;
    private final MaintenanceImageRepository maintenanceImageRepository;
    private final PropertyImageStorage imageStorage;
    private final RoomRepository roomRepository;
    private final EquipmentRepository equipmentRepository;
    private final TenantContractRepository tenantContractRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final PushNotificationService pushNotificationService;

    /** Số ngày chờ tenant confirm trước khi auto-confirm. */
    @Value("${maintenance.auto-confirm-days:3}")
    private int autoConfirmDays;

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
    @Transactional
    public MaintenanceRequestResponse createRequest(MaintenanceCreateRequest request) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        Tenant tenant = tenantRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tenant"));

        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("Phòng không tồn tại"));

        String title = request.getTitle() != null ? request.getTitle().trim() : "";
        if (title.isBlank()) {
            throw new BusinessException("Tiêu đề sự cố là bắt buộc");
        }
        if (title.length() > 200) {
            throw new BusinessException("Tiêu đề sự cố không được vượt quá 200 ký tự");
        }
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new BusinessException("Mô tả hiện trạng là bắt buộc");
        }
        String beforeUrls = joinUrls(request.getImages());
        if (beforeUrls == null) {
            throw new BusinessException("Bắt buộc đính kèm ảnh hiện trạng thiết bị (BEFORE)");
        }

        MaintenanceRequest req = MaintenanceRequest.builder()
                .tenant(tenant)
                .property(room.getProperty())
                .room(room)
                .title(title)
                .description(request.getDescription().trim())
                .equipmentId(request.getEquipmentId())
                .beforeImageUrls(beforeUrls)
                .status(MaintenanceStatus.PENDING)
                .build();

        req = repository.save(req);
        appendPhotoHistory(req, MaintenancePhotoType.BEFORE, request.getImages());
        addTimeline(req, null, MaintenanceStatus.PENDING, "Khách thuê tạo yêu cầu");
        notifyPropertyManager(req,
                "Yêu cầu bảo trì mới",
                "Khách thuê " + user.getFullName() + ": \"" + title + "\" — phòng "
                        + req.getRoom().getRoomNumber() + " (#" + req.getId() + ")");

        return convertToResponse(req);
    }

    @Override
    public Page<MaintenanceRequestResponse> getMyRequests(Pageable pageable) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return repository.findByTenantIdAndDeletedFalse(user.getId(), pageable).map(this::convertToResponse);
    }

    @Override
    public MaintenanceRequestResponse getRequestById(Long id) {
        return convertToResponse(findActive(id));
    }

    @Override
    public MaintenanceDashboardResponse getDashboardStats() {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String role = user.getAuthorities().iterator().next().getAuthority();

        if ("ROLE_MANAGER".equals(role)) {
            UUID managerId = user.getId();
            return MaintenanceDashboardResponse.builder()
                    .total(repository.countAllByManager(managerId))
                    .pending(repository.countPendingByManager(managerId))
                    .inProgress(repository.countInProgressByManager(managerId))
                    .resolved(repository.countResolvedByManager(managerId))
                    .cancelled(repository.countCancelledByManager(managerId))
                    .totalRepairCost(nz(repository.sumRepairCostByManager(managerId)))
                    .build();
        }

        return MaintenanceDashboardResponse.builder()
                .total(repository.countAll())
                .pending(repository.countPending())
                .inProgress(repository.countInProgress())
                .resolved(repository.countResolved())
                .cancelled(repository.countCancelled())
                .totalRepairCost(nz(repository.sumRepairCost()))
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
        requireStatus(req, MaintenanceStatus.PENDING);

        String category = parseCategoryRequired(
                request != null ? request.getCategory() : null);
        String priority = parsePriorityOptional(
                request != null ? request.getPriority() : null);

        MaintenanceStatus old = req.getStatus();
        req.setCategory(category);
        req.setPriority(priority);
        req.setStatus(MaintenanceStatus.APPROVED);
        req.setAcknowledgedAt(LocalDateTime.now());
        markRoomMaintenance(req);
        repository.save(req);
        addTimeline(req, old, MaintenanceStatus.APPROVED,
                "Manager duyệt yêu cầu [" + category + "], chờ sửa chữa bên ngoài");
        return convertToResponse(req);
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse complete(Long id, MaintenanceCompleteRequest request) {
        MaintenanceRequest req = findActive(id);
        requireStatus(req, MaintenanceStatus.APPROVED);

        if (request.getAfterImages() != null && !request.getAfterImages().isEmpty()) {
            appendCsv(req, true, request.getAfterImages());
            appendPhotoHistory(req, MaintenancePhotoType.AFTER, request.getAfterImages());
        }
        if (isBlank(req.getAfterImageUrls())) {
            throw new BusinessException("Bắt buộc phải có ảnh sau sửa chữa (AFTER) trước khi hoàn tất");
        }

        MaintenanceStatus old = req.getStatus();
        req.setResolutionNote(request.getResolutionNote());
        req.setStatus(MaintenanceStatus.WAITING_TENANT_CONFIRM);
        req.setDoneAt(LocalDateTime.now());
        // Giữ nguyên rejectReason / rejectImageUrls vòng trước — lịch sử đầy đủ nằm ở photoHistory
        repository.save(req);

        String note = request.getResolutionNote() != null && !request.getResolutionNote().isBlank()
                ? "Manager báo sửa xong: " + request.getResolutionNote()
                : "Manager báo sửa xong, chờ khách thuê xác nhận";
        addTimeline(req, old, MaintenanceStatus.WAITING_TENANT_CONFIRM, note);
        return convertToResponse(req);
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse confirm(Long id, MaintenanceConfirmRequest request) {
        MaintenanceRequest req = findActive(id);
        requireStatus(req, MaintenanceStatus.WAITING_TENANT_CONFIRM);

        if (request != null && !request.isAccept()) {
            throw new BusinessException("Để từ chối kết quả sửa chữa, dùng API /reject kèm lý do và ảnh");
        }

        return closeRequest(req, "Khách thuê xác nhận đã sửa xong", false);
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse reject(Long id, MaintenanceRejectRequest request, List<MultipartFile> files) {
        MaintenanceRequest req = findActive(id);
        requireStatus(req, MaintenanceStatus.WAITING_TENANT_CONFIRM);
        requireTenantOwner(req);

        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException("Bắt buộc nhập lý do từ chối");
        }

        List<String> uploaded = storeFiles(req.getId(), files);
        List<String> urls = new ArrayList<>();
        if (request.getImages() != null) {
            urls.addAll(request.getImages().stream().filter(u -> u != null && !u.isBlank()).toList());
        }
        urls.addAll(uploaded);

        if (urls.isEmpty() && isBlank(req.getRejectImageUrls())) {
            throw new BusinessException("Bắt buộc đính kèm ảnh minh chứng khi từ chối");
        }

        MaintenanceStatus old = req.getStatus();
        req.setStatus(MaintenanceStatus.REJECTED);
        req.setRejectReason(request.getReason().trim());
        if (!urls.isEmpty()) {
            req.setRejectImageUrls(joinUrls(urls));
            appendPhotoHistory(req, MaintenancePhotoType.REJECT, urls);
        }
        req.setReopenCount(req.getReopenCount() == null ? 1 : req.getReopenCount() + 1);
        repository.save(req);

        addTimeline(req, old, MaintenanceStatus.REJECTED,
                "Khách thuê từ chối kết quả sửa: " + req.getRejectReason());
        notifyPropertyManager(req,
                "Khách thuê từ chối kết quả bảo trì",
                "Yêu cầu #" + req.getId() + " bị từ chối. Lý do: " + req.getRejectReason());

        return convertToResponse(req);
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse reviewReject(Long id, MaintenanceApproveRequest request) {
        MaintenanceRequest req = findActive(id);
        requireStatus(req, MaintenanceStatus.REJECTED);

        MaintenanceStatus old = req.getStatus();
        if (request != null && request.isApprove()) {
            // Quay lại APPROVED. Không xoá ảnh AFTER khỏi lịch sử (maintenance_images).
            // Chỉ reset snapshot vòng hiện tại để bắt buộc chụp AFTER mới khi complete lại.
            ensurePhotoHistoryFromCsv(req, MaintenancePhotoType.AFTER, req.getAfterImageUrls());
            req.setStatus(MaintenanceStatus.APPROVED);
            req.setAfterImageUrls(null);
            req.setDoneAt(null);
            req.setResolutionNote(null);
            repository.save(req);
            addTimeline(req, old, MaintenanceStatus.APPROVED,
                    "Manager chấp nhận từ chối của khách, quay lại bước chờ sửa chữa");
            return convertToResponse(req);
        }

        // Manager không đồng ý reopen → yêu cầu tenant xác nhận lại
        req.setStatus(MaintenanceStatus.WAITING_TENANT_CONFIRM);
        repository.save(req);
        addTimeline(req, old, MaintenanceStatus.WAITING_TENANT_CONFIRM,
                "Manager không đồng ý reopen; yêu cầu khách thuê xác nhận lại kết quả sửa");
        return convertToResponse(req);
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse cancel(Long id, String reason) {
        MaintenanceRequest req = findActive(id);
        if (req.getStatus() == MaintenanceStatus.CLOSED || req.getStatus() == MaintenanceStatus.CANCELLED) {
            throw new BusinessException("Không thể hủy yêu cầu ở trạng thái " + req.getStatus());
        }

        MaintenanceStatus old = req.getStatus();
        req.setStatus(MaintenanceStatus.CANCELLED);
        repository.save(req);
        restoreRoomStatus(req);
        addTimeline(req, old, MaintenanceStatus.CANCELLED,
                reason != null && !reason.isBlank() ? reason : "Manager hủy yêu cầu");
        return convertToResponse(req);
    }

    @Override
    @Transactional
    public MaintenanceRequestResponse uploadPhotos(Long id, List<MultipartFile> files, String type) {
        MaintenanceRequest req = findActive(id);
        List<String> newUrls = storeFiles(id, files);
        if (newUrls.isEmpty()) {
            return convertToResponse(req);
        }

        if ("BEFORE".equalsIgnoreCase(type)) {
            appendCsv(req, false, newUrls);
            appendPhotoHistory(req, MaintenancePhotoType.BEFORE, newUrls);
        } else if ("AFTER".equalsIgnoreCase(type)) {
            appendCsv(req, true, newUrls);
            appendPhotoHistory(req, MaintenancePhotoType.AFTER, newUrls);
        } else if ("REJECT".equalsIgnoreCase(type)) {
            String existing = req.getRejectImageUrls();
            req.setRejectImageUrls(isBlank(existing) ? joinUrls(newUrls) : existing + "," + joinUrls(newUrls));
            appendPhotoHistory(req, MaintenancePhotoType.REJECT, newUrls);
        } else {
            throw new BusinessException("Type must be BEFORE, AFTER hoặc REJECT");
        }
        repository.save(req);
        return convertToResponse(req);
    }

    @Override
    @Transactional
    public int autoConfirmOverdue() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(Math.max(autoConfirmDays, 1));
        List<MaintenanceRequest> overdue = repository
                .findByStatusAndDoneAtBeforeAndDeletedFalse(MaintenanceStatus.WAITING_TENANT_CONFIRM, deadline);
        int count = 0;
        for (MaintenanceRequest req : overdue) {
            closeRequest(req,
                    "Hệ thống tự xác nhận sau " + autoConfirmDays + " ngày không phản hồi từ khách thuê",
                    true);
            count++;
        }
        return count;
    }

    @Scheduled(cron = "0 30 8 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void autoConfirmOverdueTask() {
        autoConfirmOverdue();
    }

    // ---------- helpers ----------

    private MaintenanceRequestResponse closeRequest(MaintenanceRequest req, String timelineNote, boolean auto) {
        MaintenanceStatus old = req.getStatus();
        req.setStatus(MaintenanceStatus.CLOSED);
        req.setTenantConfirmedAt(LocalDateTime.now());
        repository.save(req);
        restoreRoomStatus(req);
        addTimeline(req, old, MaintenanceStatus.CLOSED, timelineNote);
        if (auto) {
            notifyPropertyManager(req,
                    "Bảo trì tự xác nhận",
                    "Yêu cầu #" + req.getId() + " đã được hệ thống tự xác nhận do khách thuê không phản hồi.");
        }
        return convertToResponse(req);
    }

    private MaintenanceRequest findActive(Long id) {
        MaintenanceRequest req = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy request"));
        if (req.isDeleted()) {
            throw new ResourceNotFoundException("Không tìm thấy request");
        }
        return req;
    }

    private void requireStatus(MaintenanceRequest req, MaintenanceStatus expected) {
        if (req.getStatus() != expected) {
            throw new BusinessException("Yêu cầu phải ở trạng thái " + expected + " (hiện tại: " + req.getStatus() + ")");
        }
    }

    private void requireTenantOwner(MaintenanceRequest req) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        if (req.getTenant() == null || req.getTenant().getUser() == null
                || !req.getTenant().getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền thao tác trên yêu cầu này");
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

        if (req.getTenant() != null && req.getTenant().getUser() != null) {
            if (user != null && user.getId().equals(req.getTenant().getUser().getId())) {
                return;
            }
            String title = "Cập nhật yêu cầu bảo trì";
            String body = "Yêu cầu #" + req.getId() + " của bạn đã đổi trạng thái thành: " + newStatus;
            saveAndPush(req.getTenant().getUser().getId(), req.getTenant().getUser().getPushToken(), title, body, req.getId());
        }
    }

    private void notifyPropertyManager(MaintenanceRequest req, String title, String body) {
        if (req.getProperty() == null || req.getProperty().getManagedBy() == null) {
            return;
        }
        UUID managerId = req.getProperty().getManagedBy();
        userRepository.findById(managerId).ifPresent(manager ->
                saveAndPush(managerId, manager.getPushToken(), title, body, req.getId()));
    }

    private void saveAndPush(UUID userId, String pushToken, String title, String body, Long requestId) {
        notificationRepository.save(Notification.builder()
                .userId(userId)
                .title(title)
                .content(body)
                .type("MAINTENANCE")
                .build());
        if (pushToken != null && !pushToken.isBlank()) {
            pushNotificationService.sendPushNotification(
                    pushToken, title, body, java.util.Map.of("requestId", requestId));
        }
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

    private void appendCsv(MaintenanceRequest req, boolean after, List<String> urls) {
        String joined = joinUrls(urls);
        if (joined == null) {
            return;
        }
        if (after) {
            String existing = req.getAfterImageUrls();
            req.setAfterImageUrls(isBlank(existing) ? joined : existing + "," + joined);
        } else {
            String existing = req.getBeforeImageUrls();
            req.setBeforeImageUrls(isBlank(existing) ? joined : existing + "," + joined);
        }
    }

    /** Ghi log ảnh append-only vào maintenance_images (không bao giờ xoá/ghi đè). */
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

    private void ensurePhotoHistoryFromCsv(MaintenanceRequest req, MaintenancePhotoType type, String csv) {
        appendPhotoHistory(req, type, splitCsv(csv));
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

    private static String joinUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        List<String> clean = urls.stream().filter(u -> u != null && !u.isBlank()).toList();
        return clean.isEmpty() ? null : String.join(",", clean);
    }

    private static String parseCategoryRequired(String category) {
        if (category == null || category.isBlank()) {
            throw new BusinessException("Danh mục sự cố (category) là bắt buộc khi duyệt yêu cầu");
        }
        try {
            return MaintenanceCategory.valueOf(category.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "Danh mục không hợp lệ. Chọn một trong: APPLIANCE, FURNITURE, STRUCTURAL, ELECTRICAL, PLUMBING, OTHER");
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

    private static List<String> splitCsv(String csv) {
        if (isBlank(csv)) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
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
            if (req.getProperty().getManagedBy() != null) {
                res.setAssignedManagerId(req.getProperty().getManagedBy());
                userRepository.findById(req.getProperty().getManagedBy()).ifPresent(manager ->
                        res.setAssignedManagerName(manager.getFullName()));
            }
        }

        if (req.getEquipmentId() != null) {
            equipmentRepository.findById(req.getEquipmentId()).ifPresent(eq -> {
                res.setEquipmentId(eq.getId());
                res.setEquipmentName(eq.getCatalog() != null ? eq.getCatalog().getName() : null);
            });
        }

        res.setResolvedAt(req.getDoneAt());
        res.setRepairCost(req.getRepairCost());
        res.setResolutionNote(req.getResolutionNote());
        res.setCostPaidBy(req.getCostPaidBy());
        res.setCause(req.getCause());
        res.setReopenCount(req.getReopenCount());
        res.setRejectReason(req.getRejectReason());
        res.setAcknowledgedAt(req.getAcknowledgedAt());
        res.setTenantConfirmedAt(req.getTenantConfirmedAt());

        List<String> before = splitCsv(req.getBeforeImageUrls());
        List<String> after = splitCsv(req.getAfterImageUrls());
        List<String> reject = splitCsv(req.getRejectImageUrls());
        res.setBeforeImages(before);
        res.setAfterImages(after);
        res.setRejectImages(reject);
        res.setPhotoHistory(loadPhotoHistory(req.getId()));

        List<String> all = new ArrayList<>();
        all.addAll(before);
        all.addAll(after);
        all.addAll(reject);
        res.setImages(all);

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
