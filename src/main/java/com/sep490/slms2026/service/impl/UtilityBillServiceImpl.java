package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CreateUtilityBillRequest;
import com.sep490.slms2026.dto.response.UtilityBillResponse;
import com.sep490.slms2026.entity.Notification;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.entity.UtilityBill;
import com.sep490.slms2026.entity.UtilityInvoice;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.enums.UtilityBillStatus;
import com.sep490.slms2026.enums.UtilityType;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.HostNotificationRepository;
import com.sep490.slms2026.repository.NotificationRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.repository.UtilityBillRepository;
import com.sep490.slms2026.repository.UtilityInvoiceRepository;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.UserPushTokenService;
import com.sep490.slms2026.service.UtilityBillService;
import com.sep490.slms2026.service.UtilityInvoiceService;
import com.sep490.slms2026.util.UtilityTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UtilityBillServiceImpl implements UtilityBillService {

    /** Đủ chữ số để consumption × unitPrice khớp totalAmount, không làm tròn về VND nguyên. */
    static final int UNIT_PRICE_SCALE = 8;
    static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final UtilityBillRepository utilityBillRepository;
    private final PropertyRepository propertyRepository;
    private final UtilityInvoiceRepository utilityInvoiceRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final UserPushTokenService userPushTokenService;
    private final UtilityInvoiceService utilityInvoiceService;
    private final TenantContractRepository tenantContractRepository;
    private final HostNotificationRepository hostNotificationRepository;

    @Override
    @Transactional
    public UtilityBillResponse createUtilityBill(CreateUtilityBillRequest request) {
        return createPublishedBill(request, false);
    }

    private UtilityBillResponse createPublishedBill(CreateUtilityBillRequest request, boolean evnLegacyCodes) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        Long propertyId = request.getPropertyId();
        UtilityType type = UtilityTypeMapper.fromApi(request.getType());
        Integer month = request.getMonth();
        Integer year = request.getYear();
        Integer totalQuantity = request.getTotalQuantity();

        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy toà nhà ID: " + propertyId));

        Optional<UtilityBill> existing = utilityBillRepository.findByPropertyIdAndMonthAndYearAndTypeAndStatus(
                propertyId, month, year, type, UtilityBillStatus.PUBLISHED);
        if (existing.isPresent()) {
            if (evnLegacyCodes) {
                throw new BusinessException("EVN_BILL_ALREADY_EXISTS",
                        "Đã tồn tại hoá đơn EVN cho kỳ này.");
            }
            String label = type == UtilityType.WATER ? "nước" : "điện";
            throw new BusinessException("UTILITY_BILL_ALREADY_EXISTS",
                    "Đã tồn tại hoá đơn " + label + " cho kỳ này.");
        }

        boolean wholeHouse = Boolean.TRUE.equals(property.getWholeHouse());
        if (wholeHouse) {
            validateWholeHouseReadings(request);
        }

        BigDecimal unitPrice = request.getTotalAmount().divide(
                new BigDecimal(totalQuantity), UNIT_PRICE_SCALE, RoundingMode.HALF_UP);

        LocalDate today = LocalDate.now(VN);
        UtilityBill bill = UtilityBill.builder()
                .property(property)
                .type(type)
                .billingPeriod(request.getBillingPeriod())
                .month(month)
                .year(year)
                .totalQuantity(totalQuantity)
                .totalAmount(request.getTotalAmount())
                .unitPrice(unitPrice)
                .imageUrl(request.getImageUrl())
                .status(UtilityBillStatus.PUBLISHED)
                .createdBy(user.getId())
                .createdAt(LocalDateTime.now())
                .readingDeadline(wholeHouse ? null : today)
                .build();

        utilityBillRepository.save(bill);
        if (wholeHouse) {
            utilityInvoiceService.createFromWholeHouseBill(bill, request.getPrevReading(), request.getNewReading());
        }
        notifyManagerBillPublished(property, bill);

        return toResponse(bill, user.getUsername());
    }

    private void validateWholeHouseReadings(CreateUtilityBillRequest request) {
        if (request.getPrevReading() == null || request.getNewReading() == null) {
            throw new BusinessException("READINGS_REQUIRED",
                    "Nhà nguyên căn cần chỉ số cũ và chỉ số mới in trên giấy EVN/nước.");
        }
        BigDecimal expected = request.getNewReading().subtract(request.getPrevReading());
        if (expected.compareTo(BigDecimal.valueOf(request.getTotalQuantity())) != 0) {
            throw new BusinessException("CONSUMPTION_MISMATCH",
                    "Chỉ số mới − chỉ số cũ phải bằng tổng tiêu thụ trên hoá đơn.");
        }
        if (request.getNewReading().compareTo(request.getPrevReading()) < 0) {
            throw new BusinessException("READING_ORDER_INVALID",
                    "Chỉ số mới phải lớn hơn hoặc bằng chỉ số cũ");
        }
    }

    @Override
    @Transactional
    public void revokeUtilityBill(Long id) {
        revoke(id, false);
    }

    private void revoke(Long id, boolean evnLegacyCodes) {
        UtilityBill bill = utilityBillRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        evnLegacyCodes ? "Không tìm thấy hoá đơn EVN." : "Không tìm thấy hoá đơn tiện ích."));

        if (bill.getStatus() == UtilityBillStatus.REVOKED) {
            return;
        }

        UtilityType invoiceType = bill.getType() != null ? bill.getType() : UtilityType.ELECTRIC;
        List<UtilityInvoice> utilityInvoices = utilityInvoiceRepository.findByFilters(
                bill.getProperty().getId(), bill.getBillingPeriod(), invoiceType);

        if (!utilityInvoices.isEmpty()) {
            if (evnLegacyCodes) {
                throw new BusinessException("EVN_BILL_IN_USE",
                        "Không thể thu hồi vì đã có hoá đơn điện được gửi cho khách trong kỳ này.");
            }
            String label = invoiceType == UtilityType.WATER ? "nước" : "điện";
            throw new BusinessException("UTILITY_BILL_IN_USE",
                    "Không thể thu hồi vì đã có hoá đơn " + label + " được gửi cho khách trong kỳ này.");
        }

        bill.setStatus(UtilityBillStatus.REVOKED);
        utilityBillRepository.save(bill);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UtilityBillResponse> getUtilityBills(
            Long propertyId, Integer month, Integer year, UtilityType type, boolean isManager) {
        UtilityBillStatus statusFilter = isManager ? UtilityBillStatus.PUBLISHED : null;
        List<UtilityBill> bills;

        if (isManager) {
            CustomUserDetails user = SecurityUtils.requireCurrentUser();
            if (propertyId != null) {
                if (!propertyRepository.findIdsByOperationManagerId(user.getId()).contains(propertyId)) {
                    throw new AccessDeniedException("Bạn không có quyền quản lý nhà này");
                }
                bills = utilityBillRepository.findByFilters(propertyId, month, year, type, statusFilter);
            } else {
                List<Long> managerPropIds = propertyRepository.findIdsByOperationManagerId(user.getId());
                if (managerPropIds.isEmpty()) {
                    return List.of();
                }
                bills = managerPropIds.stream()
                        .flatMap(pid -> utilityBillRepository.findByFilters(pid, month, year, type, statusFilter).stream())
                        .collect(Collectors.toList());
            }
        } else {
            bills = utilityBillRepository.findByFilters(propertyId, month, year, type, statusFilter);
        }

        return bills.stream().map(bill -> {
            String username = null;
            if (bill.getCreatedBy() != null) {
                username = userRepository.findById(bill.getCreatedBy())
                    .map(User::getUsername).orElse("unknown");
            }
            return toResponse(bill, username);
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public int remindUtilityMeterReading() {
        LocalDate today = LocalDate.now(VN);
        LocalTime now = LocalTime.now(VN);
        List<UtilityBill> bills = utilityBillRepository
                .findPublishedSharedHouseBillsWithDeadline(UtilityBillStatus.PUBLISHED);
        int sent = 0;
        for (UtilityBill bill : bills) {
            RoomProgress progress = roomProgress(bill);
            if (progress.roomsTotal() <= 0 || progress.roomsDone() >= progress.roomsTotal()) {
                continue;
            }
            LocalDate deadline = bill.getReadingDeadline();
            if (deadline == null) {
                continue;
            }
            int remaining = progress.roomsTotal() - progress.roomsDone();
            if (today.isAfter(deadline)) {
                int daysLate = (int) ChronoUnit.DAYS.between(deadline, today);
                if (notifyReadingMilestone(bill, "UTILITY_READING_OVERDUE",
                        "utility-reading:" + bill.getId() + ":overdue-" + daysLate,
                        overdueTitle(bill, daysLate, remaining, progress.roomsTotal()),
                        overdueContent(bill, daysLate, remaining, progress.roomsTotal()),
                        true)) {
                    sent++;
                }
            } else if (today.equals(deadline) && !now.isBefore(LocalTime.of(20, 0))) {
                if (notifyReadingMilestone(bill, "UTILITY_READING_LATE_RISK",
                        "utility-reading:" + bill.getId() + ":late-risk",
                        lateRiskTitle(bill),
                        lateRiskContent(bill, remaining, progress.roomsTotal()),
                        false)) {
                    sent++;
                }
            } else if (today.equals(deadline) && !now.isBefore(LocalTime.of(15, 0))) {
                if (notifyReadingMilestone(bill, "UTILITY_READING_DUE_TODAY",
                        "utility-reading:" + bill.getId() + ":due-today",
                        dueTodayTitle(bill),
                        dueTodayContent(bill, remaining, progress.roomsTotal()),
                        false)) {
                    sent++;
                }
            }
        }
        return sent;
    }

    private void notifyManagerBillPublished(Property property, UtilityBill bill) {
        UUID managerId = resolveManagerId(property);
        if (managerId == null) {
            log.warn("Utility bill {} published but property {} has no operation manager — skip notify",
                    bill.getId(), property.getId());
            return;
        }

        boolean wholeHouse = Boolean.TRUE.equals(property.getWholeHouse());
        boolean water = bill.getType() == UtilityType.WATER;
        String kind = water ? "nước" : "điện";
        String unit = water ? "m³" : "kWh";
        String label = propertyLabel(property);
        String paramsJson = "{\"propertyId\":" + property.getId() + "}";

        String notifType;
        String title;
        String content;
        String dedupeKey = null;
        if (wholeHouse) {
            notifType = water ? "WATER_BILL_PUBLISHED" : "EVN_BILL_PUBLISHED";
            title = "Đã có hoá đơn " + kind + " kỳ " + bill.getMonth() + "/" + bill.getYear();
            content = "Đã phát hành hoá đơn " + kind + " kỳ " + bill.getMonth() + "/" + bill.getYear()
                    + " cho khách thuê · " + label
                    + ". Bạn không cần làm gì, vào xem nếu khách thắc mắc.";
        } else {
            RoomProgress progress = roomProgress(bill);
            int total = progress.roomsTotal();
            int remaining = Math.max(total - progress.roomsDone(), 0);
            notifType = "UTILITY_READING_ASSIGNED";
            title = "⚡ Việc hôm nay: chụp đồng hồ " + kind;
            content = "⚡ Việc hôm nay: chụp đồng hồ + ghi chỉ số " + total + " phòng · " + label
                    + " · đơn giá " + formatVnPrice(bill.getUnitPrice()) + "đ/" + unit
                    + ". Phải xong trong hôm nay. Còn " + remaining + "/" + total + " phòng.";
            dedupeKey = "utility-reading:" + bill.getId() + ":assigned";
        }

        notificationRepository.save(Notification.builder()
                .userId(managerId)
                .title(title)
                .content(content)
                .type(notifType)
                .screen("UtilityBilling")
                .paramsJson(paramsJson)
                .dedupeKey(dedupeKey)
                .read(false)
                .build());

        Map<String, Object> data = new HashMap<>();
        data.put("type", notifType);
        data.put("screen", "UtilityBilling");
        data.put("propertyId", property.getId());
        data.put("params", Map.of("propertyId", property.getId()));
        userPushTokenService.sendToUser(managerId, title, content, data);
    }

    private boolean notifyReadingMilestone(UtilityBill bill,
                                           String type, String dedupeKey,
                                           String title, String content, boolean notifyAdminAndHost) {
        Property property = bill.getProperty();
        UUID managerId = resolveManagerId(property);
        String paramsJson = "{\"propertyId\":" + property.getId() + ",\"billId\":" + bill.getId() + "}";
        boolean sent = notifyAppUser(managerId, type, title, content, paramsJson, dedupeKey);

        if (!notifyAdminAndHost) {
            return sent;
        }
        for (User admin : userRepository.findByRoleAndStatus(Role.ROLE_ADMIN, UserStatus.ACTIVE)) {
            if (notifyAppUser(admin.getId(), type, title, content, paramsJson, dedupeKey)) {
                sent = true;
            }
        }
        for (User host : userRepository.findByRoleAndStatus(Role.ROLE_OWNER, UserStatus.ACTIVE)) {
            try {
                if (hostNotificationRepository.existsByUserIdAndDedupeKey(host.getId(), dedupeKey)) {
                    continue;
                }
                hostNotificationRepository.insertIfAbsent(
                        host.getId(),
                        dedupeKey,
                        type,
                        title,
                        content,
                        "HIGH");
                sent = true;
            } catch (Exception e) {
                log.error("Failed to insert host utility-reading reminder billId={}", bill.getId(), e);
            }
        }
        return sent;
    }

    private boolean notifyAppUser(UUID userId, String type, String title, String content,
                                  String paramsJson, String dedupeKey) {
        if (userId == null) {
            return false;
        }
        if (notificationRepository.existsByUserIdAndDedupeKey(userId, dedupeKey)) {
            return false;
        }
        try {
            notificationRepository.save(Notification.builder()
                    .userId(userId)
                    .title(title)
                    .content(content)
                    .type(type)
                    .screen("UtilityBilling")
                    .paramsJson(paramsJson)
                    .dedupeKey(dedupeKey)
                    .read(false)
                    .build());
        } catch (DataIntegrityViolationException e) {
            return false;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        data.put("screen", "UtilityBilling");
        data.put("params", paramsJson);
        userPushTokenService.sendToUser(userId, title, content, data);
        return true;
    }

    private String dueTodayTitle(UtilityBill bill) {
        return "⏰ Còn phòng chưa ghi chỉ số — hết hôm nay là quá hạn";
    }

    private String dueTodayContent(UtilityBill bill, int remaining, int total) {
        return "⏰ Còn " + remaining + "/" + total + " phòng chưa ghi chỉ số — hết hôm nay là quá hạn"
                + " · " + kindPeriodLabel(bill) + " · " + propertyLabel(bill.getProperty());
    }

    private String lateRiskTitle(UtilityBill bill) {
        return "🚨 Quá hôm nay thì số đọc lệch kỳ hoá đơn";
    }

    private String lateRiskContent(UtilityBill bill, int remaining, int total) {
        return "🚨 Còn " + remaining + "/" + total + " phòng — quá hôm nay thì số đọc lệch kỳ hoá đơn"
                + " · " + kindPeriodLabel(bill) + " · " + propertyLabel(bill.getProperty());
    }

    private String overdueTitle(UtilityBill bill, int daysLate, int remaining, int total) {
        return "❗ Quá hạn " + daysLate + " ngày · còn " + remaining + "/" + total + " phòng chưa ghi chỉ số";
    }

    private String overdueContent(UtilityBill bill, int daysLate, int remaining, int total) {
        return overdueTitle(bill, daysLate, remaining, total)
                + " · " + kindPeriodLabel(bill) + " · " + propertyLabel(bill.getProperty());
    }

    private String kindPeriodLabel(UtilityBill bill) {
        String kind = bill.getType() == UtilityType.WATER ? "nước" : "điện";
        return "Hoá đơn " + kind + " kỳ " + bill.getMonth() + "/" + bill.getYear();
    }

    private static UUID resolveManagerId(Property property) {
        if (property.getOperationManagerId() != null) {
            return property.getOperationManagerId();
        }
        return property.getOperationManagerId();
    }

    private static String propertyLabel(Property property) {
        String name = property.getPropertyName() != null ? property.getPropertyName() : "Nhà";
        return name + "#" + property.getId();
    }

    private static String formatVnPrice(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);
        if (rounded.stripTrailingZeros().scale() <= 0) {
            return String.format("%,d", rounded.longValue()).replace(',', '.');
        }
        return String.format("%,.2f", rounded.doubleValue()).replace(',', '@').replace('.', ',').replace('@', '.');
    }

    private UtilityBillResponse toResponse(UtilityBill bill, String username) {
        UtilityType type = bill.getType() != null ? bill.getType() : UtilityType.ELECTRIC;
        Integer qty = bill.getTotalQuantity();
        RoomProgress progress = roomProgress(bill);
        LocalDate today = LocalDate.now(VN);
        boolean wholeHouse = Boolean.TRUE.equals(bill.getProperty().getWholeHouse());
        boolean overdue = !wholeHouse
                && bill.getReadingDeadline() != null
                && today.isAfter(bill.getReadingDeadline())
                && progress.roomsDone() < progress.roomsTotal();
        return UtilityBillResponse.builder()
                .id(bill.getId())
                .propertyId(bill.getProperty().getId())
                .propertyName(bill.getProperty().getPropertyName())
                .type(UtilityTypeMapper.toApi(type))
                .billingPeriod(bill.getBillingPeriod())
                .month(bill.getMonth())
                .year(bill.getYear())
                .totalQuantity(qty)
                .totalAmount(bill.getTotalAmount())
                .unitPrice(bill.getUnitPrice())
                .unitPriceExact(bill.getUnitPrice())
                .imageUrl(bill.getImageUrl())
                .status(bill.getStatus().name())
                .createdBy(username)
                .createdAt(bill.getCreatedAt())
                .roomsTotal(progress.roomsTotal())
                .roomsDone(progress.roomsDone())
                .readingDeadline(bill.getReadingDeadline())
                .overdue(overdue)
                .build();
    }

    private RoomProgress roomProgress(UtilityBill bill) {
        Property property = bill.getProperty();
        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            return new RoomProgress(0, 0);
        }
        UtilityType type = bill.getType() != null ? bill.getType() : UtilityType.ELECTRIC;
        int total = (int) tenantContractRepository.countActiveRoomContracts(property.getId());
        int done = (int) utilityInvoiceRepository.countDistinctRoomsInvoiced(
                property.getId(), bill.getBillingPeriod(), type);
        return new RoomProgress(total, done);
    }

    private record RoomProgress(int roomsTotal, int roomsDone) {}
}

