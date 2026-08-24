package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CreateInvoiceDisputeRequest;
import com.sep490.slms2026.dto.request.ResolveInvoiceDisputeRequest;
import com.sep490.slms2026.dto.response.AdminInvoiceDisputeResponse;
import com.sep490.slms2026.dto.response.InvoiceDisputeResponse;
import com.sep490.slms2026.dto.response.TenantInvoiceResponse;
import com.sep490.slms2026.entity.InvoiceDispute;
import com.sep490.slms2026.entity.MeterReading;
import com.sep490.slms2026.entity.Notification;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.TenantInvoice;
import com.sep490.slms2026.entity.TenantPendingCharge;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.entity.UtilityBill;
import com.sep490.slms2026.entity.UtilityInvoice;
import com.sep490.slms2026.enums.InvoiceDisputeReason;
import com.sep490.slms2026.enums.InvoiceDisputeStatus;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.TenantInvoiceStatus;
import com.sep490.slms2026.enums.TenantInvoiceType;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.enums.UtilityBillStatus;
import com.sep490.slms2026.enums.UtilityInvoiceStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.InvoiceDisputeRepository;
import com.sep490.slms2026.repository.MeterReadingRepository;
import com.sep490.slms2026.repository.NotificationRepository;
import com.sep490.slms2026.repository.TenantInvoiceRepository;
import com.sep490.slms2026.repository.TenantPendingChargeRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.repository.UtilityBillRepository;
import com.sep490.slms2026.repository.UtilityInvoiceRepository;
import com.sep490.slms2026.service.InvoiceDisputeService;
import com.sep490.slms2026.service.UserPushTokenService;
import com.sep490.slms2026.util.ContractBillingCalendar;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceDisputeServiceImpl implements InvoiceDisputeService {

    public static final int REJECT_GRACE_DAYS = 3;
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<InvoiceDisputeStatus> BLOCKING = List.of(
            InvoiceDisputeStatus.OPEN,
            InvoiceDisputeStatus.ACCEPTED,
            InvoiceDisputeStatus.REJECTED);

    private final InvoiceDisputeRepository invoiceDisputeRepository;
    private final TenantInvoiceRepository tenantInvoiceRepository;
    private final UtilityInvoiceRepository utilityInvoiceRepository;
    private final UtilityBillRepository utilityBillRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final UserPushTokenService userPushTokenService;
    private final TenantPendingChargeRepository tenantPendingChargeRepository;

    @Override
    @Transactional
    public InvoiceDisputeResponse create(UUID tenantUserId, Long tenantInvoiceId, CreateInvoiceDisputeRequest request) {
        TenantInvoice invoice = loadOwnedUtilityInvoice(tenantUserId, tenantInvoiceId);
        if (invoice.getStatus() == TenantInvoiceStatus.CANCELLED) {
            throw new BusinessException("INVOICE_CANCELLED", "Không thể khiếu nại hoá đơn đã huỷ.");
        }
        Long utilityInvoiceId = invoice.getUtilityInvoiceId();
        if (utilityInvoiceId == null) {
            throw new BusinessException("NOT_UTILITY_INVOICE", "Chỉ khiếu nại được hoá đơn điện/nước.");
        }
        if (invoiceDisputeRepository.existsByTenantInvoiceIdAndStatusIn(invoice.getId(), BLOCKING)
                || invoiceDisputeRepository.existsByUtilityInvoiceIdAndStatusIn(utilityInvoiceId, BLOCKING)) {
            throw new BusinessException("DISPUTE_ALREADY_EXISTS",
                    "Hoá đơn này đã có khiếu nại. Chỉ gửi lại được sau khi tự rút.");
        }

        UtilityInvoice utilityInvoice = utilityInvoiceRepository.findById(utilityInvoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hoá đơn tiện ích"));

        InvoiceDispute dispute = InvoiceDispute.builder()
                .utilityInvoice(utilityInvoice)
                .tenantInvoice(invoice)
                .tenantContract(invoice.getTenantContract())
                .status(InvoiceDisputeStatus.OPEN)
                .reason(request.getReason() != null ? request.getReason() : InvoiceDisputeReason.OTHER)
                .note(request.getNote().trim())
                .photos(sanitizePhotos(request.getPhotos()))
                .createdAt(LocalDateTime.now())
                .build();
        dispute = invoiceDisputeRepository.save(dispute);

        if (invoice.getStatus() == TenantInvoiceStatus.OVERDUE) {
            invoice.setStatus(TenantInvoiceStatus.PENDING);
            tenantInvoiceRepository.save(invoice);
        }
        notifyAdminsAndManagerOnCreate(invoice, dispute);
        return toTenantDto(dispute);
    }

    @Override
    @Transactional
    public InvoiceDisputeResponse withdraw(UUID tenantUserId, Long tenantInvoiceId) {
        TenantInvoice invoice = loadOwnedUtilityInvoice(tenantUserId, tenantInvoiceId);
        InvoiceDispute dispute = invoiceDisputeRepository
                .findFirstByTenantInvoiceIdOrderByCreatedAtDesc(invoice.getId())
                .orElseThrow(() -> new BusinessException("DISPUTE_NOT_FOUND", "Không có khiếu nại để rút."));
        if (dispute.getStatus() != InvoiceDisputeStatus.OPEN) {
            throw new BusinessException("DISPUTE_NOT_OPEN", "Chỉ rút được khiếu nại đang chờ xử lý.");
        }
        dispute.setStatus(InvoiceDisputeStatus.WITHDRAWN);
        dispute.setResolvedAt(LocalDateTime.now());
        dispute.setResolutionNote("Khách tự rút khiếu nại.");
        invoiceDisputeRepository.save(dispute);
        applyGraceDueDate(invoice);
        return toTenantDto(dispute);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminInvoiceDisputeResponse> listForAdmin() {
        return invoiceDisputeRepository.findAllForAdmin().stream()
                .map(this::toAdminDto)
                .toList();
    }

    @Override
    @Transactional
    public AdminInvoiceDisputeResponse resolve(UUID adminUserId, Long disputeId, ResolveInvoiceDisputeRequest request) {
        InvoiceDispute dispute = invoiceDisputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khiếu nại"));
        if (dispute.getStatus() != InvoiceDisputeStatus.OPEN) {
            throw new BusinessException("DISPUTE_ALREADY_RESOLVED", "Khiếu nại này đã được kết luận.");
        }

        TenantInvoice tenantInvoice = dispute.getTenantInvoice();
        UtilityInvoice utilityInvoice = dispute.getUtilityInvoice();
        boolean wasPaid = tenantInvoice.getStatus() == TenantInvoiceStatus.PAID;
        String outcome = request.getOutcome().trim().toUpperCase();

        dispute.setResolvedBy(adminUserId);
        dispute.setResolvedAt(LocalDateTime.now());
        dispute.setResolutionNote(request.getNote().trim());

        if ("ACCEPTED".equals(outcome)) {
            dispute.setStatus(InvoiceDisputeStatus.ACCEPTED);
            tenantInvoice.setStatus(TenantInvoiceStatus.CANCELLED);
            tenantInvoiceRepository.save(tenantInvoice);
            if (utilityInvoice != null) {
                utilityInvoice.setStatus(UtilityInvoiceStatus.CANCELLED);
                utilityInvoiceRepository.save(utilityInvoice);
            }
            if (wasPaid) {
                createRefundCredit(tenantInvoice);
            }
            notifyOnResolve(dispute, true);
        } else if ("REJECTED".equals(outcome)) {
            dispute.setStatus(InvoiceDisputeStatus.REJECTED);
            applyGraceDueDate(tenantInvoice);
            notifyOnResolve(dispute, false);
        } else {
            throw new BusinessException("INVALID_OUTCOME", "Kết luận phải là ACCEPTED hoặc REJECTED.");
        }
        invoiceDisputeRepository.save(dispute);
        return toAdminDto(dispute);
    }

    @Override
    public void enrichTenantInvoice(TenantInvoice invoice, TenantInvoiceResponse response) {
        if (invoice.getInvoiceType() != TenantInvoiceType.ELECTRICITY
                && invoice.getInvoiceType() != TenantInvoiceType.WATER) {
            return;
        }
        if (invoice.getUtilityInvoiceId() == null) {
            return;
        }
        utilityInvoiceRepository.findById(invoice.getUtilityInvoiceId()).ifPresent(ui -> {
            boolean wholeHouse = ui.getProperty() != null && Boolean.TRUE.equals(ui.getProperty().getWholeHouse());
            response.setPrevReading(ui.getPrevReading());
            response.setNewReading(ui.getNewReading());
            response.setMeterImageUrl(resolveMeterImageUrl(ui));
            response.setMeterCapturedAt(resolveMeterCapturedAt(ui));
            response.setUtilityBillImageUrl(wholeHouse ? null : resolveUtilityBillImageUrl(ui, invoice));
            response.setBillingAddress(null);
            response.setCustomerCode(null);
            response.setPropertyType(wholeHouse ? "WHOLE_HOUSE" : "MULTI_ROOM");
        });
        invoiceDisputeRepository.findFirstByTenantInvoiceIdOrderByCreatedAtDesc(invoice.getId())
                .ifPresent(d -> response.setDispute(toTenantDto(d)));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> openDisputeTenantInvoiceIds() {
        return invoiceDisputeRepository.findTenantInvoiceIdsByStatus(InvoiceDisputeStatus.OPEN);
    }

    @Override
    @Transactional
    public void attachReplacementIfPresent(UtilityInvoice newUtilityInvoice) {
        if (newUtilityInvoice == null || newUtilityInvoice.getTenantContract() == null) {
            return;
        }
        List<InvoiceDispute> accepted = invoiceDisputeRepository.findByTenantContractIdAndStatus(
                newUtilityInvoice.getTenantContract().getId(), InvoiceDisputeStatus.ACCEPTED);
        for (InvoiceDispute dispute : accepted) {
            if (dispute.getReplacementInvoice() != null) {
                continue;
            }
            UtilityInvoice old = dispute.getUtilityInvoice();
            if (old == null || old.getUtilityType() != newUtilityInvoice.getUtilityType()) {
                continue;
            }
            if (!samePeriod(old.getBillingPeriod(), newUtilityInvoice.getBillingPeriod())) {
                continue;
            }
            dispute.setReplacementInvoice(newUtilityInvoice);
            invoiceDisputeRepository.save(dispute);
        }
    }

    private TenantInvoice loadOwnedUtilityInvoice(UUID tenantUserId, Long tenantInvoiceId) {
        TenantInvoice invoice = tenantInvoiceRepository.findByIdAndTenantUserId(tenantInvoiceId, tenantUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn ID=" + tenantInvoiceId));
        if (invoice.getInvoiceType() != TenantInvoiceType.ELECTRICITY
                && invoice.getInvoiceType() != TenantInvoiceType.WATER) {
            throw new BusinessException("NOT_UTILITY_INVOICE", "Chỉ khiếu nại được hoá đơn điện/nước.");
        }
        return invoice;
    }

    private void applyGraceDueDate(TenantInvoice invoice) {
        LocalDate today = LocalDate.now(VN);
        LocalDate base = invoice.getDueDate();
        if (base == null || !base.isAfter(today)) {
            invoice.setDueDate(today.plusDays(REJECT_GRACE_DAYS));
        } else {
            invoice.setDueDate(base.plusDays(REJECT_GRACE_DAYS));
        }
        if (invoice.getStatus() == TenantInvoiceStatus.OVERDUE
                && invoice.getDueDate() != null
                && !invoice.getDueDate().isBefore(today)) {
            invoice.setStatus(TenantInvoiceStatus.PENDING);
        }
        tenantInvoiceRepository.save(invoice);
    }

    private void createRefundCredit(TenantInvoice invoice) {
        if (invoice.getTenantContract() == null || invoice.getGrandTotal() == null
                || invoice.getGrandTotal().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        tenantPendingChargeRepository.save(TenantPendingCharge.builder()
                .tenantContract(invoice.getTenantContract())
                .amount(invoice.getGrandTotal().negate())
                .category("UTILITY_DISPUTE_CREDIT")
                .note("Hoàn/trừ kỳ sau do khiếu nại hoá đơn " + invoice.getCode() + " được chấp nhận.")
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private List<String> sanitizePhotos(List<String> photos) {
        if (photos == null) {
            return new ArrayList<>();
        }
        return photos.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .limit(3)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String resolveMeterImageUrl(UtilityInvoice ui) {
        if (ui.getMeterImageUrl() != null && !ui.getMeterImageUrl().isBlank()) {
            return ui.getMeterImageUrl();
        }
        return findMeterReading(ui).map(MeterReading::getImageUrl).orElse(null);
    }

    private LocalDateTime resolveMeterCapturedAt(UtilityInvoice ui) {
        return findMeterReading(ui).map(MeterReading::getRecordedAt).orElse(ui.getCreatedAt());
    }

    private Optional<MeterReading> findMeterReading(UtilityInvoice ui) {
        if (ui.getProperty() == null) {
            return Optional.empty();
        }
        Long propertyId = ui.getProperty().getId();
        Long roomId = ui.getRoom() != null ? ui.getRoom().getId() : null;
        if (roomId != null) {
            return meterReadingRepository.findTopByPropertyIdAndRoomIdAndUtilityTypeAndPeriodOrderByRecordedAtDesc(
                    propertyId, roomId, ui.getUtilityType(), ui.getBillingPeriod());
        }
        return meterReadingRepository.findTopByPropertyIdAndRoomIsNullAndUtilityTypeAndPeriodOrderByRecordedAtDesc(
                propertyId, ui.getUtilityType(), ui.getBillingPeriod());
    }

    private String resolveUtilityBillImageUrl(UtilityInvoice ui, TenantInvoice invoice) {
        if (ui.getProperty() == null) {
            return null;
        }
        YearMonth ym = ContractBillingCalendar.parsePeriod(ui.getBillingPeriod())
                .orElseGet(() -> invoice.getBillingYear() != null && invoice.getBillingMonth() != null
                        ? YearMonth.of(invoice.getBillingYear(), invoice.getBillingMonth())
                        : null);
        if (ym == null) {
            return null;
        }
        Optional<UtilityBill> exact = utilityBillRepository.findByPropertyIdAndMonthAndYearAndTypeAndStatus(
                ui.getProperty().getId(), ym.getMonthValue(), ym.getYear(),
                ui.getUtilityType(), UtilityBillStatus.PUBLISHED);
        if (exact.isPresent() && exact.get().getImageUrl() != null && !exact.get().getImageUrl().isBlank()) {
            return exact.get().getImageUrl();
        }
        return utilityBillRepository.findByFilters(
                        ui.getProperty().getId(), ym.getMonthValue(), ym.getYear(),
                        ui.getUtilityType(), UtilityBillStatus.PUBLISHED)
                .stream()
                .map(UtilityBill::getImageUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);
    }

    private boolean samePeriod(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.equalsIgnoreCase(b)) {
            return true;
        }
        var pa = ContractBillingCalendar.parsePeriod(a);
        var pb = ContractBillingCalendar.parsePeriod(b);
        return pa.isPresent() && pb.isPresent() && pa.get().equals(pb.get());
    }

    private InvoiceDisputeResponse toTenantDto(InvoiceDispute dispute) {
        Long replacementTenantInvoiceId = null;
        String replacementCode = null;
        if (dispute.getReplacementInvoice() != null) {
            Optional<TenantInvoice> replacement = tenantInvoiceRepository
                    .findByUtilityInvoiceId(dispute.getReplacementInvoice().getId());
            if (replacement.isPresent()) {
                replacementTenantInvoiceId = replacement.get().getId();
                replacementCode = replacement.get().getCode();
            }
        }
        return InvoiceDisputeResponse.builder()
                .id(dispute.getId())
                .invoiceId(dispute.getTenantInvoice() != null ? dispute.getTenantInvoice().getId() : null)
                .status(dispute.getStatus() != null ? dispute.getStatus().name() : null)
                .reason(dispute.getReason() != null ? dispute.getReason().name() : null)
                .note(dispute.getNote())
                .photos(dispute.getPhotos())
                .createdAt(dispute.getCreatedAt())
                .resolvedAt(dispute.getResolvedAt())
                .resolutionNote(dispute.getResolutionNote())
                .replacementInvoiceId(replacementTenantInvoiceId)
                .replacementInvoiceCode(replacementCode)
                .build();
    }

    private AdminInvoiceDisputeResponse toAdminDto(InvoiceDispute dispute) {
        TenantInvoice ti = dispute.getTenantInvoice();
        UtilityInvoice ui = dispute.getUtilityInvoice();
        TenantContract contract = dispute.getTenantContract();
        Property property = ui != null ? ui.getProperty() : (contract != null ? contract.getProperty() : null);
        boolean wholeHouse = property != null && Boolean.TRUE.equals(property.getWholeHouse());

        String tenantName = null;
        String tenantPhone = null;
        if (contract != null && contract.getTenant() != null && contract.getTenant().getUser() != null) {
            tenantName = contract.getTenant().getUser().getFullName();
            tenantPhone = contract.getTenant().getUser().getPhoneNumber();
        }

        String managerName = null;
        UUID readerId = ui != null ? ui.getCreatedBy() : null;
        if (readerId != null) {
            managerName = userRepository.findById(readerId).map(User::getFullName).orElse(null);
        }
        if (managerName == null && property != null && property.getOperationManagerId() != null) {
            managerName = userRepository.findById(property.getOperationManagerId()).map(User::getFullName).orElse(null);
        }

        InvoiceDisputeResponse nested = toTenantDto(dispute);
        boolean refundRequired = dispute.getStatus() == InvoiceDisputeStatus.ACCEPTED
                && ti != null && ti.getPaidAt() != null;

        return AdminInvoiceDisputeResponse.builder()
                .id(dispute.getId())
                .status(dispute.getStatus() != null ? dispute.getStatus().name() : null)
                .reason(dispute.getReason() != null ? dispute.getReason().name() : null)
                .note(dispute.getNote())
                .photos(dispute.getPhotos())
                .createdAt(dispute.getCreatedAt())
                .resolvedAt(dispute.getResolvedAt())
                .resolutionNote(dispute.getResolutionNote())
                .replacementInvoiceId(nested.getReplacementInvoiceId())
                .replacementInvoiceCode(nested.getReplacementInvoiceCode())
                .invoiceId(ti != null ? ti.getId() : null)
                .invoiceCode(ti != null ? ti.getCode() : null)
                .invoiceType(ti != null && ti.getInvoiceType() != null ? ti.getInvoiceType().name() : null)
                .invoiceStatus(ti != null && ti.getStatus() != null ? ti.getStatus().name() : null)
                .billingPeriod(ui != null ? ui.getBillingPeriod() : (ti != null ? ti.getBillingPeriod() : null))
                .amount(ti != null ? ti.getGrandTotal() : (ui != null ? ui.getAmount() : null))
                .dueDate(ti != null ? ti.getDueDate() : null)
                .paidAt(ti != null ? ti.getPaidAt() : null)
                .propertyName(property != null ? property.getPropertyName() : (ti != null ? ti.getPropertyName() : null))
                .propertyAddress(property != null ? property.getAddress() : null)
                .wholeHouse(wholeHouse)
                .propertyType(wholeHouse ? "WHOLE_HOUSE" : "MULTI_ROOM")
                .roomNumber(ti != null ? ti.getRoomNumber()
                        : (ui != null && ui.getRoom() != null ? ui.getRoom().getRoomNumber() : null))
                .tenantName(tenantName)
                .tenantPhone(tenantPhone)
                .managerName(managerName)
                .prevReading(ui != null ? ui.getPrevReading() : null)
                .newReading(ui != null ? ui.getNewReading() : null)
                .consumption(ui != null ? ui.getConsumption() : null)
                .unitPrice(ui != null ? ui.getUnitPrice() : null)
                .meterImageUrl(ui != null ? resolveMeterImageUrl(ui) : null)
                .meterCapturedAt(ui != null ? resolveMeterCapturedAt(ui) : null)
                .utilityBillImageUrl(ui != null && !wholeHouse ? resolveUtilityBillImageUrl(ui, ti) : null)
                .billingAddress(null)
                .customerCode(null)
                .refundRequired(refundRequired)
                .build();
    }

    private void notifyAdminsAndManagerOnCreate(TenantInvoice invoice, InvoiceDispute dispute) {
        String typeLabel = invoice.getInvoiceType() == TenantInvoiceType.WATER ? "nước" : "điện";
        String title = "Khiếu nại hoá đơn " + typeLabel;
        String content = "Khách khiếu nại hoá đơn " + invoice.getCode()
                + " (" + dispute.getReason() + "). " + dispute.getNote();
        for (User admin : userRepository.findByRoleAndStatus(Role.ROLE_ADMIN, UserStatus.ACTIVE)) {
            sendNotification(admin.getId(), "INVOICE_DISPUTE_OPEN", title, content, invoice.getId());
        }
        UUID managerId = resolveManagerId(invoice);
        if (managerId != null) {
            sendNotification(managerId, "INVOICE_DISPUTE_OPEN", title,
                    "Khách khiếu nại chỉ số/hoá đơn " + invoice.getCode() + ". Chuẩn bị giải trình hoặc chụp lại đồng hồ.",
                    invoice.getId());
        }
    }

    private void notifyOnResolve(InvoiceDispute dispute, boolean accepted) {
        TenantInvoice invoice = dispute.getTenantInvoice();
        String outcome = accepted ? "được chấp nhận" : "bị từ chối";
        String content = "Khiếu nại hoá đơn " + invoice.getCode() + " " + outcome + ". "
                + (dispute.getResolutionNote() != null ? dispute.getResolutionNote() : "");
        sendNotification(invoice.getTenantUserId(),
                accepted ? "INVOICE_DISPUTE_ACCEPTED" : "INVOICE_DISPUTE_REJECTED",
                "Khiếu nại hoá đơn " + outcome, content, invoice.getId());
        if (accepted) {
            UUID managerId = resolveManagerId(invoice);
            if (managerId != null) {
                sendNotification(managerId, "INVOICE_DISPUTE_ACCEPTED",
                        "Khiếu nại được chấp nhận — cần đọc lại số",
                        "Hoá đơn " + invoice.getCode() + " đã huỷ vì sai. Cần phát hành lại bản đúng.",
                        invoice.getId());
            }
        }
    }

    private UUID resolveManagerId(TenantInvoice invoice) {
        if (invoice.getTenantContract() != null && invoice.getTenantContract().getAssignedManager() != null) {
            return invoice.getTenantContract().getAssignedManager().getId();
        }
        if (invoice.getTenantContract() != null
                && invoice.getTenantContract().getProperty() != null
                && invoice.getTenantContract().getProperty().getOperationManagerId() != null) {
            return invoice.getTenantContract().getProperty().getOperationManagerId();
        }
        return null;
    }

    private void sendNotification(UUID userId, String type, String title, String content, Long invoiceId) {
        if (userId == null) {
            return;
        }
        notificationRepository.save(Notification.builder()
                .userId(userId)
                .title(title)
                .content(content)
                .type(type)
                .screen("InvoiceList")
                .paramsJson("{\"invoiceId\":" + invoiceId + "}")
                .read(false)
                .build());
        userPushTokenService.sendToUser(userId, title, content, java.util.Map.of(
                "type", type,
                "screen", "InvoiceList",
                "invoiceId", invoiceId));
    }
}
