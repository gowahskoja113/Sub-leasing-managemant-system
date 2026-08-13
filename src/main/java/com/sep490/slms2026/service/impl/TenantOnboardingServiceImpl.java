package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.constant.OtpDeliveryOverride;
import com.sep490.slms2026.dto.request.ContractAddedEquipmentRequest;
import com.sep490.slms2026.dto.request.ContractEvidencePhotoRequest;
import com.sep490.slms2026.dto.request.HouseholdMemberRequest;
import com.sep490.slms2026.dto.request.OnboardTenantRequest;
import com.sep490.slms2026.dto.request.TerminateContractRequest;
import com.sep490.slms2026.dto.response.ContractEvidencePhotoResponse;
import com.sep490.slms2026.dto.response.TenantContractResponse;
import com.sep490.slms2026.entity.ContractEvidencePhoto;
import com.sep490.slms2026.entity.HouseholdMember;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.Tenant;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.TenantInvoice;
import com.sep490.slms2026.entity.TenantPayment;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.OtpPurpose;
import com.sep490.slms2026.enums.PaymentStatus;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.enums.TenantInvoiceStatus;
import com.sep490.slms2026.enums.TenantInvoiceType;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.TenantInvoiceRepository;
import com.sep490.slms2026.repository.TenantPaymentRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.repository.TenantInvoiceRepository;
import com.sep490.slms2026.repository.TenantPaymentClaimRepository;
import com.sep490.slms2026.enums.TenantInvoiceStatus;
import com.sep490.slms2026.enums.TenantInvoiceType;
import com.sep490.slms2026.repository.NotificationRepository;
import com.sep490.slms2026.service.ContractEquipmentService;
import com.sep490.slms2026.service.MeterOverrideService;
import com.sep490.slms2026.service.OtpService;
import com.sep490.slms2026.service.PayosService;
import com.sep490.slms2026.service.RealtimeEventService;
import com.sep490.slms2026.service.TenantOnboardingService;
import com.sep490.slms2026.util.RentFirstCycleCalculator;
import com.sep490.slms2026.util.PhoneUtils;
import com.sep490.slms2026.util.PaymentBreakdownBuilder;
import com.sep490.slms2026.util.TenantContractPaymentAmounts;
import com.sep490.slms2026.util.TenantContractStatusHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantOnboardingServiceImpl implements TenantOnboardingService {

    /** Số ngày tối đa cho phép nhận nhà sớm so với ngày vào ở dự kiến. */
    @org.springframework.beans.factory.annotation.Value("${contract.max-early-move-in-days:3}")
    private int maxEarlyMoveInDays;

    /** Quá số ngày này kể từ ngày vào ở dự kiến mà HĐ chưa ACTIVE → tự động hủy (no-show). */
    @org.springframework.beans.factory.annotation.Value("${contract.no-show-grace-days:10}")
    private int noShowGraceDays;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final TenantContractRepository tenantContractRepository;
    private final TenantInvoiceRepository tenantInvoiceRepository;
    private final TenantPaymentRepository tenantPaymentRepository;
    private final PayosService payosService;
    private final OtpService otpService;
    private final ContractEquipmentService contractEquipmentService;
    private final NotificationRepository notificationRepository;
    private final com.sep490.slms2026.service.UserPushTokenService userPushTokenService;
    private final MeterOverrideService meterOverrideService;
    private final TenantPaymentClaimRepository tenantPaymentClaimRepository;
    private final com.sep490.slms2026.service.TenantBillingService tenantBillingService;
    private final RealtimeEventService realtimeEventService;

    @Override
    @Transactional
    public TenantContractResponse onboardTenant(Long propertyId, Long roomId, OnboardTenantRequest request) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tòa nhà với ID: " + propertyId));

        // ── Validation §3.2: 4 quy tắc bắt buộc phía server ──
        LocalDate today = LocalDate.now();

        // Rule 1: Ngày hợp đồng hiệu lực (moveInDate) phải là hôm nay
        if (!request.isDraft() && request.getMoveInDate() != null && !today.equals(request.getMoveInDate())) {
            throw new BusinessException("Ngày hợp đồng hiệu lực phải là hôm nay");
        }

        // Rule 2: endDate bắt buộc (belt-and-suspenders — @NotNull đã chặn ở DTO)
        if (request.getEndDate() == null) {
            throw new BusinessException("Thiếu ngày kết thúc hợp đồng");
        }

        // Rule 3: endDate phải sau moveInDate (ngày hiệu lực)
        if (!request.getEndDate().isAfter(request.getMoveInDate())) {
            throw new BusinessException("Ngày kết thúc phải sau ngày hiệu lực");
        }

        // Rule 4: Thời hạn thuê tối đa 5 năm
        if (request.getEndDate().isAfter(today.plusYears(5))) {
            throw new BusinessException("Thời hạn thuê tối đa 5 năm");
        }

        Room room = null;
        if (roomId != null) {
            room = roomRepository.findByIdAndPropertyId(roomId, propertyId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy phòng ID " + roomId + " thuộc tòa nhà ID " + propertyId));

            // Quy tắc 1-HĐ-active theo phòng + kiểm tra trùng khoảng thời gian
            if (tenantContractRepository.existsByRoomIdAndStatus(roomId, ContractStatus.ACTIVE)) {
                throw new BusinessException("Phòng này đã có hợp đồng đang hiệu lực");
            }
            if (tenantContractRepository.existsOverlappingContractByRoom(
                    roomId, request.getMoveInDate(), request.getEndDate())) {
                throw new BusinessException("Phòng này đã có hợp đồng chồng lấn trong khoảng thời gian này");
            }
            if (room.getStatus() == RoomStatus.RENTED) {
                throw new BusinessException("Phòng này đang được cho thuê");
            }
        } else {
            // Thuê nguyên căn: chặn nếu đã có HĐ active cấp tòa
            if (tenantContractRepository.existsByPropertyIdAndRoomIsNullAndStatus(propertyId, ContractStatus.ACTIVE)) {
                throw new BusinessException("Căn nhà này đã có hợp đồng nguyên căn đang hiệu lực");
            }
            if (tenantContractRepository.existsOverlappingContractByProperty(
                    propertyId, request.getMoveInDate(), request.getEndDate())) {
                throw new BusinessException("Căn nhà này đã có hợp đồng chồng lấn trong khoảng thời gian này");
            }
        }

        // Quản lý phụ trách nhà (Operation Manager) là người duy nhất đón khách & phụ trách
        // toàn bộ vòng đời hợp đồng. Nhà chưa có quản lý → không cho tạo hợp đồng.
        if (property.getOperationManagerId() == null) {
            throw new BusinessException(
                    "Nhà chưa có quản lý phụ trách, vui lòng gán quản lý cho nhà trước khi tạo hợp đồng");
        }

        Tenant tenant = request.isDraft() ? null : getOrCreateTenant(request);

        // BE tự gán quản lý = Operation Manager của nhà, không nhận từ client.
        User assignedManager = userRepository.findById(property.getOperationManagerId()).orElse(null);

        ContractStatus initStatus;
        if (request.isDraft()) {
            initStatus = ContractStatus.DRAFT;
        } else {
            initStatus = request.isRequireDepositPayment() || request.isRequireHostPriceApproval() ? ContractStatus.PENDING : ContractStatus.ACTIVE;
        }

        TenantContract contract = TenantContract.builder()
                .tenant(tenant)
                .property(property)
                .room(room)
                .contractCode(generateContractCode())
                .rentAmount(request.getRentAmount())
                .deposit(request.getDeposit())
                .depositMonths(request.getDepositMonths())
                .moveInDate(request.getMoveInDate())
                .startDate(request.getMoveInDate())
                .endDate(request.getEndDate())
                .equipmentSnapshot(request.getEquipmentSnapshot())
                .initialElectricReading(request.getInitialElectricReading())
                .initialWaterReading(request.getInitialWaterReading())
                .electricMeterImageUrl(request.getElectricMeterImageUrl())
                .electricMeterCapturedAt(resolveCapturedAt(
                        request.getElectricMeterImageUrl(), request.getElectricMeterCapturedAt()))
                .waterMeterImageUrl(request.getWaterMeterImageUrl())
                .waterMeterCapturedAt(resolveCapturedAt(
                        request.getWaterMeterImageUrl(), request.getWaterMeterCapturedAt()))
                .roomConditionPhotos(resolveRoomConditionPhotos(
                        request.getRoomConditionPhotos(), request.getRoomConditionUrls()))
                .roomConditionNote(request.getRoomConditionNote())
                .status(initStatus)
                .priceApprovalStatus(request.isRequireHostPriceApproval() ? com.sep490.slms2026.enums.PriceApprovalStatus.PENDING_PRICE_APPROVAL : null)
                .assignedManager(assignedManager)
                .draftContractFileUrl(request.getDraftContractFileUrl())
                .expectedReceptionDate(request.getExpectedReceptionDate())
                .draftTenantName(request.isDraft() ? request.getFullName() : null)
                .draftTenantPhone(request.isDraft() ? request.getPhoneNumber() : null)
                .draftTenantCccd(request.isDraft() ? request.getCccd() : null)
                .draftTenantDob(request.isDraft() ? request.getDateOfBirth() : null)
                .draftTenantCccdIssueDate(request.isDraft() ? request.getCccdIssueDate() : null)
                .draftTenantCccdIssuePlace(request.isDraft() ? request.getCccdIssuePlace() : null)
                .draftTenantAddress(request.isDraft() ? request.getPermanentAddress() : null)
                .build();

        // Thành viên ở cùng (bỏ qua dòng trống)
        if (request.getHouseholdMembers() != null) {
            for (HouseholdMemberRequest m : request.getHouseholdMembers()) {
                if (m.getFullName() == null || m.getFullName().isBlank()) continue;
                contract.getHouseholdMembers().add(HouseholdMember.builder()
                        .tenantContract(contract)
                        .fullName(m.getFullName())
                        .relation(m.getRelation())
                        .phone(m.getPhone())
                        .dateOfBirth(m.getDateOfBirth())
                        .cccd(m.getCccd())
                        .build());
            }
        }

        contractEquipmentService.resolveAndApplyHandover(
                contract,
                request.getSelectedEquipmentIds(),
                request.getDeclinedEquipmentIds(),
                request.getAddedEquipments(),
                request.getAddedEquipmentIds());

        TenantContract saved = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                if (attempt > 0) {
                    contract.setContractCode(generateContractCode());
                }
                saved = tenantContractRepository.saveAndFlush(contract);
                break;
            } catch (DataIntegrityViolationException e) {
                if (attempt == 2) {
                    throw new BusinessException("Hệ thống đang bận, vui lòng thử lại để sinh mã hợp đồng");
                }
                log.warn("Trùng mã hợp đồng {}, thử lại lần {}", contract.getContractCode(), attempt + 1);
            }
        }

        // Bắt buộc bằng chứng khi ghi chỉ số: ảnh HOẶC override token (sau passcode admin)
        requireMeterEvidence(request.getInitialElectricReading(),
                request.getElectricMeterImageUrl(), request.getElectricMeterOverrideToken(), "điện");
        requireMeterEvidence(request.getInitialWaterReading(),
                request.getWaterMeterImageUrl(), request.getWaterMeterOverrideToken(), "nước");

        // Override nhập tay chỉ số (không có ảnh) — sau khi có contractId
        applyMeterOverridesIfAny(saved, request.getElectricMeterOverrideToken(),
                request.getElectricMeterOverrideReason(), request.getInitialElectricReading(),
                request.getWaterMeterOverrideToken(), request.getWaterMeterOverrideReason(),
                request.getInitialWaterReading());
        if (request.getElectricMeterOverrideToken() != null || request.getWaterMeterOverrideToken() != null) {
            saved = tenantContractRepository.save(saved);
        }

        if (request.isDraft() && saved.getAssignedManager() != null) {
            notifyAssignedManager(saved);
        }

        // Chỉ set phòng RENTED khi HĐ kích hoạt ngay (không yêu cầu thanh toán cọc trước và không chờ duyệt giá, và không phải DRAFT).
        // Với luồng có thanh toán hoặc chờ duyệt: phòng sẽ được set RENTED ở bước confirm.
        if (room != null && !request.isRequireDepositPayment() && !request.isRequireHostPriceApproval() && !request.isDraft()) {
            room.setStatus(RoomStatus.RENTED);
            roomRepository.save(room);
        }

        if (saved.getStatus() == ContractStatus.ACTIVE) {
            contractEquipmentService.disableDeclinedForActiveContract(saved);
            tenantBillingService.generateProratedRentForNewContract(saved);
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public TenantContractResponse getContract(Long contractId) {
        TenantContract contract = findContract(contractId);
        syncExpiredIfNeeded(contract);
        return toResponse(contract);
    }

    @Override
    @Transactional
    public TenantContractResponse createDepositPayment(Long contractId) {
        TenantContract contract = findContract(contractId);
        if (contract.getStatus() == ContractStatus.DRAFT) {
            ensureRoomAvailableForDeposit(contract);
            if (contract.getMoveInDate() == null || contract.getMoveInDate().isBefore(LocalDate.now())) {
                throw new BusinessException("Ngày vào ở không hợp lệ để thu cọc");
            }
            contract.setStatus(ContractStatus.PENDING);
        }

        ensureDepositPaymentAllowed(contract);

        // Onboard: một QR gồm cọc + tiền nhà pro-rata chu kỳ đầu.
        BigDecimal resolvedDeposit = TenantContractPaymentAmounts.resolveDepositAmount(contract);
        if ((contract.getDeposit() == null || contract.getDeposit().compareTo(BigDecimal.ZERO) <= 0)
                && resolvedDeposit.compareTo(BigDecimal.ZERO) > 0) {
            contract.setDeposit(resolvedDeposit);
        }
        BigDecimal deposit = TenantContractPaymentAmounts.resolveDepositAmount(contract);
        BigDecimal firstRent = TenantContractPaymentAmounts.resolveFirstRentAmount(contract);
        BigDecimal total = deposit.add(firstRent);
        long amount = total.longValue();
        if (amount <= 0) {
            throw new BusinessException("Số tiền thanh toán onboard không hợp lệ");
        }
        long orderCode = System.currentTimeMillis(); // duy nhất, < giới hạn PayOS
        PayosService.PaymentLink link = payosService.createPaymentLink(
                orderCode, amount, "Onboard HD " + contract.getId());

        contract.setPayosOrderCode(link.orderCode);
        contract.setPaymentStatus(PaymentStatus.PENDING);
        // Snapshot để webhook ghi hoá đơn đúng số đã quét (không tính lại nếu HĐ bị sửa sau).
        contract.setOnboardQrAmount(total);
        contract.setOnboardQrDepositAmount(deposit);
        contract.setOnboardQrFirstRentAmount(firstRent);
        tenantContractRepository.save(contract);

        TenantContractResponse res = toResponse(contract);
        res.setPayosCheckoutUrl(link.checkoutUrl);
        res.setPayosQrCode(link.qrCode);
        return res;
    }

    @Override
    @Transactional
    public TenantContractResponse confirmContract(Long contractId, String otp) {
        TenantContract contract = findContract(contractId);

        if (contract.getStatus() == ContractStatus.ACTIVE) {
            return toResponse(contract); // idempotent
        }
        if (contract.getPaymentStatus() != PaymentStatus.PAID) {
            throw new BusinessException("Chưa thanh toán onboard, không thể hoàn tất hợp đồng");
        }

        // Hardcode SĐT nhận OTP (budget) — không lấy từ hợp đồng
        otpService.verifyOrThrow(OtpDeliveryOverride.PHONE, otp, OtpPurpose.CONTRACT_CONFIRM, contractId);

        // Nhận nhà SỚM: khách đến trước ngày vào ở dự kiến.
        // Cho phép tối đa maxEarlyMoveInDays ngày — ghi nhận ngày vào ở thực tế = hôm nay,
        // GIỮ NGUYÊN endDate (khách được ở free mấy ngày sớm, không dời hạn/không tính thêm tiền).
        LocalDate today = LocalDate.now();
        LocalDate plannedMoveIn = contract.getMoveInDate();
        if (plannedMoveIn != null && today.isBefore(plannedMoveIn)) {
            long daysEarly = java.time.temporal.ChronoUnit.DAYS.between(today, plannedMoveIn);
            if (daysEarly > maxEarlyMoveInDays) {
                throw new BusinessException("Chỉ được nhận nhà sớm tối đa " + maxEarlyMoveInDays
                        + " ngày so với ngày vào ở dự kiến (" + plannedMoveIn
                        + "). Vui lòng cập nhật lại ngày vào ở hoặc nhận đúng lịch.");
            }
            contract.setMoveInDate(today);
            contract.setStartDate(today);
        }

        boolean accountCreated = false;
        boolean rolePromoted = false;
        if (contract.getTenant() == null) {
            TenantCreationResult result = getOrCreateTenant(
                    contract.getDraftTenantPhone(),
                    contract.getDraftTenantName(),
                    contract.getDraftTenantCccd(),
                    contract.getDraftTenantDob(),
                    contract.getDraftTenantCccdIssueDate(),
                    contract.getDraftTenantCccdIssuePlace(),
                    contract.getDraftTenantAddress());
            contract.setTenant(result.tenant);
            accountCreated = result.created;
            rolePromoted = result.promoted;
        }

        Room room = contract.getRoom();
        if (room != null) {
            if (tenantContractRepository.existsByRoomIdAndStatus(room.getId(), ContractStatus.ACTIVE)) {
                throw new BusinessException("Phòng này đã có hợp đồng đang hiệu lực");
            }
            room.setStatus(RoomStatus.RENTED);
            roomRepository.save(room);
        }
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setActivatedAt(LocalDateTime.now());
        TenantContract saved = tenantContractRepository.save(contract);
        contractEquipmentService.disableDeclinedForActiveContract(saved);
        backfillOnboardingInvoiceTenant(saved);
        tenantBillingService.generateProratedRentForNewContract(saved);
        notifyContractActivated(saved);

        return toResponse(saved, contract.getTenant().getUser().getUsername(), accountCreated, rolePromoted);
    }

    @Override
    @Transactional
    public void sendContractConfirmOtp(Long contractId) {
        TenantContract contract = findContract(contractId);
        if (contract.getStatus() == ContractStatus.ACTIVE) {
            throw new BusinessException("Hợp đồng đã được kích hoạt");
        }
        if (contract.getPaymentStatus() != PaymentStatus.PAID) {
            throw new BusinessException("Chưa thanh toán onboard, không thể gửi OTP xác nhận");
        }
        // Hardcode SĐT nhận OTP (budget) — không lấy từ hợp đồng
        log.info("Gửi OTP xác nhận HĐ {} tới số override {}", contractId, OtpDeliveryOverride.PHONE);
        otpService.sendOtp(OtpDeliveryOverride.PHONE, OtpPurpose.CONTRACT_CONFIRM, contractId);
    }

    @Override
    @Transactional
    public TenantContractResponse syncPaymentStatus(Long contractId) {
        TenantContract contract = findContract(contractId);
        if (contract.getPaymentStatus() != PaymentStatus.PAID && contract.getPayosOrderCode() != null) {
            String status = payosService.getPaymentStatus(contract.getPayosOrderCode());
            if ("PAID".equalsIgnoreCase(status)) {
                completeDepositPayment(contract, contract.getPayosOrderCode(), "PAYOS");
            }
        }
        return toResponse(findContract(contractId));
    }

    @Override
    @Transactional
    public void markDepositPaid(Long payosOrderCode) {
        tenantContractRepository.findByPayosOrderCode(payosOrderCode).ifPresent(contract ->
                completeDepositPayment(contract, payosOrderCode, "PAYOS"));
    }

    /**
     * Ghi nhận thanh toán cọc onboard: set PAID, sinh hoá đơn + giao dịch, notify.
     * Idempotent — PayOS có thể retry webhook; cũng backfill hoá đơn nếu HĐ đã PAID trước đó nhưng thiếu sổ.
     */
    private void completeDepositPayment(TenantContract contract, Long payosOrderCode, String method) {
        LocalDateTime now = LocalDateTime.now();
        boolean wasAlreadyPaid = contract.getPaymentStatus() == PaymentStatus.PAID
                && contract.getDepositPaidAt() != null;

        contract.setPaymentStatus(PaymentStatus.PAID);
        if (contract.getPaidAt() == null) {
            contract.setPaidAt(now);
        }
        if (contract.getDepositPaidAt() == null) {
            contract.setDepositPaidAt(contract.getPaidAt() != null ? contract.getPaidAt() : now);
        }
        if (contract.getDepositMethod() == null || contract.getDepositMethod().isBlank()) {
            contract.setDepositMethod(method);
        }
        tenantContractRepository.save(contract);

        boolean invoiceCreated = createOnboardingInvoiceAndPayment(contract, payosOrderCode, method,
                contract.getPaidAt() != null ? contract.getPaidAt() : now);
        if (!wasAlreadyPaid || invoiceCreated) {
            notifyDepositPaid(contract);
        }
    }

    /** @return true nếu vừa tạo hoá đơn mới */
    private boolean createOnboardingInvoiceAndPayment(TenantContract contract, Long payosOrderCode,
                                                   String method, LocalDateTime paidAt) {
        String code = onboardingInvoiceCode(contract.getId());
        if (tenantInvoiceRepository.findByCode(code).isPresent()) {
            return false;
        }

        BigDecimal deposit = contract.getOnboardQrDepositAmount() != null
                ? contract.getOnboardQrDepositAmount()
                : TenantContractPaymentAmounts.resolveDepositAmount(contract);
        BigDecimal firstRentAmount = contract.getOnboardQrFirstRentAmount() != null
                ? contract.getOnboardQrFirstRentAmount()
                : TenantContractPaymentAmounts.resolveFirstRentAmount(contract);
        RentFirstCycleCalculator.Result firstRent = TenantContractPaymentAmounts.resolveFirstRentCycle(contract);
        BigDecimal grandTotal = contract.getOnboardQrAmount() != null
                ? contract.getOnboardQrAmount()
                : deposit.add(firstRentAmount);
        UUID tenantUserId = resolveTenantUserId(contract);
        String propertyName = contract.getProperty() != null ? contract.getProperty().getPropertyName() : "";
        String roomNumber = contract.getRoom() != null ? contract.getRoom().getRoomNumber() : null;
        int depositMonths = contract.getDepositMonths() != null ? contract.getDepositMonths() : 1;
        String moveIn = contract.getMoveInDate() != null ? contract.getMoveInDate().toString() : "";
        BigDecimal rentMonthly = contract.getRentAmount() != null ? contract.getRentAmount() : BigDecimal.ZERO;

        StringBuilder note = new StringBuilder("ONBOARD|depositAmount=").append(deposit.toPlainString())
                .append("|depositMonths=").append(depositMonths);
        if (rentMonthly.compareTo(BigDecimal.ZERO) > 0) {
            note.append("|rentAmount=").append(rentMonthly.toPlainString());
        }
        if (firstRentAmount.compareTo(BigDecimal.ZERO) > 0) {
            note.append("|firstRentAmount=").append(firstRentAmount.toPlainString())
                    .append("|billedDays=").append(firstRent.billedDays())
                    .append("|daysInMonth=").append(firstRent.daysInMonth())
                    .append("|periodStart=").append(firstRent.periodStart())
                    .append("|periodEnd=").append(firstRent.periodEnd());
        }

        String billingPeriod = firstRentAmount.compareTo(BigDecimal.ZERO) > 0
                ? "Cọc + tiền nhà lúc nhận phòng " + moveIn
                : "Tiền cọc lúc nhận phòng " + moveIn;

        TenantInvoice invoice = tenantInvoiceRepository.save(TenantInvoice.builder()
                .code(code)
                .tenantUserId(tenantUserId)
                .tenantContract(contract)
                .invoiceType(TenantInvoiceType.OTHER)
                .propertyName(propertyName != null ? propertyName : "")
                .roomNumber(roomNumber)
                .billingPeriod(billingPeriod)
                .note(note.toString())
                .totalAmount(grandTotal)
                .lateFee(BigDecimal.ZERO)
                .grandTotal(grandTotal)
                .status(TenantInvoiceStatus.PAID)
                .createdAt(paidAt)
                .paidAt(paidAt)
                .paymentMethod(method)
                .transactionId(payosOrderCode != null ? String.valueOf(payosOrderCode) : null)
                .payosOrderCode(payosOrderCode)
                .build());

        tenantPaymentRepository.save(TenantPayment.builder()
                .tenantInvoice(invoice)
                .tenantUserId(tenantUserId)
                .invoiceCode(invoice.getCode())
                .invoiceType(invoice.getInvoiceType())
                .amount(grandTotal)
                .method(method)
                .paidAt(paidAt)
                .transactionId(payosOrderCode != null ? String.valueOf(payosOrderCode) : null)
                .propertyName(propertyName)
                .roomNumber(roomNumber)
                .build());

        realtimeEventService.publishInvoicePaid(invoice);
        tenantBillingService.recordPaidFirstRentFromOnboard(contract, payosOrderCode, method, paidAt);
        return true;
    }

    private void backfillOnboardingInvoiceTenant(TenantContract contract) {
        UUID tenantUserId = resolveTenantUserId(contract);
        if (tenantUserId == null) {
            return;
        }
        for (TenantInvoice invoice : tenantInvoiceRepository.findByTenantContractId(contract.getId())) {
            if (invoice.getTenantUserId() == null) {
                invoice.setTenantUserId(tenantUserId);
                tenantInvoiceRepository.save(invoice);
            }
            for (TenantPayment payment : tenantPaymentRepository.findByTenantInvoiceId(invoice.getId())) {
                if (payment.getTenantUserId() == null) {
                    payment.setTenantUserId(tenantUserId);
                    tenantPaymentRepository.save(payment);
                }
            }
        }
    }

    /**
     * Ghi chỉ số thì phải có bằng chứng: ảnh mặt đồng hồ, HOẶC override token (sau passcode admin).
     * Xét riêng từng đồng hồ. {@code reading == null} = chưa tới bước ghi chỉ số → không chặn.
     */
    private static void requireMeterEvidence(
            BigDecimal reading, String imageUrl, UUID overrideToken, String label) {
        if (reading == null) {
            return;
        }
        boolean hasPhoto = imageUrl != null && !imageUrl.isBlank();
        if (!hasPhoto && overrideToken == null) {
            throw new BusinessException(
                    "Thiếu bằng chứng chỉ số " + label + ": cần ảnh mặt đồng hồ, "
                            + "hoặc mã do admin cấp kèm lý do.");
        }
    }

    private void applyMeterOverridesIfAny(
            TenantContract contract,
            UUID electricToken, String electricReason, BigDecimal electricValue,
            UUID waterToken, String waterReason, BigDecimal waterValue) {
        // Chỉ apply override khi không có ảnh (nhập tay)
        boolean needElec = electricToken != null
                && (contract.getElectricMeterImageUrl() == null || contract.getElectricMeterImageUrl().isBlank());
        boolean needWater = waterToken != null
                && (contract.getWaterMeterImageUrl() == null || contract.getWaterMeterImageUrl().isBlank());
        if (!needElec && !needWater) {
            return;
        }
        UUID managerId = com.sep490.slms2026.security.SecurityUtils.requireCurrentUser().getId();
        if (needElec) {
            meterOverrideService.consumeOverrideIfPresent(
                    managerId, contract.getId(), "ELEC", electricToken, electricValue, electricReason);
            if (contract.getElectricMeterCapturedAt() == null) {
                contract.setElectricMeterCapturedAt(LocalDateTime.now());
            }
        }
        if (needWater) {
            meterOverrideService.consumeOverrideIfPresent(
                    managerId, contract.getId(), "WATER", waterToken, waterValue, waterReason);
            if (contract.getWaterMeterCapturedAt() == null) {
                contract.setWaterMeterCapturedAt(LocalDateTime.now());
            }
        }
    }

    private static String onboardingInvoiceCode(Long contractId) {
        return "HD-ONBOARD-" + contractId;
    }

    private UUID resolveTenantUserId(TenantContract contract) {
        if (contract.getTenant() != null) {
            if (contract.getTenant().getUser() != null) {
                return contract.getTenant().getUser().getId();
            }
            return contract.getTenant().getId();
        }
        String phone = contract.getDraftTenantPhone();
        if (phone != null && !phone.isBlank()) {
            try {
                String local = PhoneUtils.normalizeLocal(phone);
                String intl = PhoneUtils.toInternational(phone);
                User existing = findExistingUserByPhone(local, intl);
                if (existing != null) {
                    return existing.getId();
                }
            } catch (Exception ignored) {
                // keep null
            }
        }
        return null;
    }

    private void notifyDepositPaid(TenantContract contract) {
        BigDecimal total = TenantContractPaymentAmounts.resolveInitialPaymentAmount(contract);
        String amountText = total != null ? total.stripTrailingZeros().toPlainString() : "0";
        String tenantName = contract.getDraftTenantName();
        if ((tenantName == null || tenantName.isBlank()) && contract.getTenant() != null
                && contract.getTenant().getUser() != null) {
            tenantName = contract.getTenant().getUser().getFullName();
        }
        if (tenantName == null || tenantName.isBlank()) {
            tenantName = "khách";
        }
        String roomLabel = contract.getRoom() != null && contract.getRoom().getRoomNumber() != null
                ? contract.getRoom().getRoomNumber() : "nguyên căn";

        // Tenant — kèm số tiền
        UUID tenantUserId = resolveTenantUserId(contract);
        if (tenantUserId != null) {
            String title = "✅ Đã nhận thanh toán onboard";
            String body = firstRentIncluded(contract)
                    ? "Hệ thống đã ghi nhận tiền cọc và tiền nhà chu kỳ đầu (" + amountText
                    + "đ). Tiếp tục xác thực OTP để hoàn tất nhận nhà."
                    : "Hệ thống đã ghi nhận thanh toán onboard (" + amountText
                    + "đ). Tiếp tục xác thực OTP để hoàn tất nhận nhà.";
            notificationRepository.save(com.sep490.slms2026.entity.Notification.builder()
                    .userId(tenantUserId)
                    .title(title)
                    .content(body)
                    .type("DEPOSIT_PAID_TENANT")
                    .screen("InvoiceList")
                    .build());
            userPushTokenService.sendToUser(tenantUserId, title, body, Map.of(
                    "screen", "InvoiceList",
                    "type", "DEPOSIT_PAID_TENANT"));
        }

        // Manager — KHÔNG kèm số tiền (chính sách money visibility)
        User manager = resolveContractManager(contract);
        if (manager != null) {
            String title = "💰 Khách đã thanh toán xong";
            String body = "Khách " + tenantName + " · Phòng " + roomLabel
                    + " đã thanh toán xong. Tiếp tục bước xác thực OTP để hoàn tất hợp đồng.";
            notificationRepository.save(com.sep490.slms2026.entity.Notification.builder()
                    .userId(manager.getId())
                    .title(title)
                    .content(body)
                    .type("DEPOSIT_PAID_MANAGER")
                    .screen("ResumeContract")
                    .paramsJson("{\"contractId\":" + contract.getId() + "}")
                    .build());
            userPushTokenService.sendToUser(manager.getId(), title, body, Map.of(
                    "screen", "ResumeContract",
                    "params", Map.of("contractId", contract.getId()),
                    "type", "DEPOSIT_PAID_MANAGER"));
        }
    }

    /** Tenant: OTP xong, HĐ ACTIVE — có deep-link ContractDetail. */
    private void notifyContractActivated(TenantContract contract) {
        UUID tenantUserId = resolveTenantUserId(contract);
        if (tenantUserId == null) {
            return;
        }
        String roomLabel = contract.getRoom() != null && contract.getRoom().getRoomNumber() != null
                ? contract.getRoom().getRoomNumber() : "nguyên căn";
        String code = contract.getContractCode() != null ? contract.getContractCode() : ("#" + contract.getId());
        String title = "🎉 Hợp đồng đã kích hoạt";
        String body = "Hợp đồng " + code + " · Phòng " + roomLabel
                + " đã có hiệu lực. Xem chi tiết trong app.";
        notificationRepository.save(com.sep490.slms2026.entity.Notification.builder()
                .userId(tenantUserId)
                .title(title)
                .content(body)
                .type("CONTRACT_ACTIVATED")
                .build());
        userPushTokenService.sendToUser(tenantUserId, title, body, Map.of(
                "screen", "ContractDetail",
                "params", Map.of("contractId", contract.getId()),
                "type", "CONTRACT_ACTIVATED"));
    }

    private User resolveContractManager(TenantContract contract) {
        if (contract.getAssignedManager() != null) {
            return contract.getAssignedManager();
        }
        if (contract.getProperty() != null && contract.getProperty().getOperationManagerId() != null) {
            return userRepository.findById(contract.getProperty().getOperationManagerId()).orElse(null);
        }
        return null;
    }

    @Override
    @Transactional
    public List<TenantContractResponse> getManagedContracts(String status) {
        java.util.UUID managerUserId = com.sep490.slms2026.security.SecurityUtils.requireCurrentUser().getId();
        List<TenantContract> contracts;
        if (status != null && !status.isBlank()) {
            try {
                ContractStatus contractStatus = ContractStatus.valueOf(status.toUpperCase());
                contracts = tenantContractRepository.findManagedContractsByStatus(managerUserId, contractStatus);
            } catch (IllegalArgumentException notContractStatus) {
                try {
                    com.sep490.slms2026.enums.PriceApprovalStatus enumStatus =
                            com.sep490.slms2026.enums.PriceApprovalStatus.valueOf(status.toUpperCase());
                    contracts = tenantContractRepository.findManagedContractsByApprovalStatus(managerUserId, enumStatus);
                } catch (IllegalArgumentException e) {
                    contracts = new ArrayList<>();
                }
            }
        } else {
            contracts = tenantContractRepository.findManagedContractsByApprovalStatuses(managerUserId, 
                List.of(com.sep490.slms2026.enums.PriceApprovalStatus.PENDING_PRICE_APPROVAL, 
                        com.sep490.slms2026.enums.PriceApprovalStatus.APPROVED_AWAITING_DEPOSIT, 
                        com.sep490.slms2026.enums.PriceApprovalStatus.PRICE_REJECTED));
        }
        return contracts.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public TenantContractResponse resubmitApproval(Long contractId, com.sep490.slms2026.dto.request.ResubmitApprovalRequest request) {
        TenantContract contract = findContract(contractId);
        if (contract.getPriceApprovalStatus() != com.sep490.slms2026.enums.PriceApprovalStatus.PRICE_REJECTED &&
            contract.getPriceApprovalStatus() != com.sep490.slms2026.enums.PriceApprovalStatus.PENDING_PRICE_APPROVAL) {
            throw new BusinessException("Hợp đồng không ở trạng thái có thể gửi duyệt lại");
        }
        if (contract.getPayosOrderCode() != null && contract.getPaymentStatus() == PaymentStatus.PENDING) {
            throw new BusinessException(
                    "Đang có QR thanh toán onboard PENDING — hủy/tạo lại QR trước khi sửa giá, hoặc đợi thanh toán xong");
        }
        if (contract.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessException("Hợp đồng đã thanh toán onboard, không thể gửi duyệt lại giá");
        }
        contract.setRentAmount(request.getRentAmount());
        contract.setDeposit(request.getDeposit());
        contract.setPriceApprovalStatus(com.sep490.slms2026.enums.PriceApprovalStatus.PENDING_PRICE_APPROVAL);
        contract.setPriceRejectReason(null);
        return toResponse(tenantContractRepository.save(contract));
    }

    @Override
    @Transactional
    public void cancelContract(Long contractId) {
        TenantContract contract = findContract(contractId);
        if (contract.getStatus() == ContractStatus.ACTIVE) {
            throw new BusinessException("Không thể hủy hợp đồng đã kích hoạt — dùng API thanh lý (/terminate)");
        }
        if (contract.getStatus() == ContractStatus.TERMINATED) {
            return;
        }
        releaseContractOccupancy(contract);
        contract.setStatus(ContractStatus.TERMINATED);
        contract.setTerminatedAt(LocalDateTime.now());
        tenantContractRepository.save(contract);

        if (contract.getTenant() != null && contract.getTenant().getUser() != null) {
            disableTenantAccountIfNoActiveContracts(contract.getTenant().getUser());
        }
    }

    @Override
    @Transactional
    public void deleteContract(Long contractId) {
        TenantContract contract = findContract(contractId);
        if (contract.getStatus() == ContractStatus.ACTIVE || contract.getStatus() == ContractStatus.PENDING) {
            releaseContractOccupancy(contract);
        }
        
        // Disable account if no other active contracts
        if (contract.getTenant() != null && contract.getTenant().getUser() != null) {
            disableTenantAccountIfNoActiveContracts(contract.getTenant().getUser());
        }

        tenantPaymentClaimRepository.deleteByTenantContractId(contractId);
        tenantInvoiceRepository.deleteByTenantContractId(contractId);
        tenantContractRepository.delete(contract);
    }

    @Override
    @Transactional
    public TenantContractResponse terminateActiveContract(Long contractId, TerminateContractRequest request) {
        TenantContract contract = findContract(contractId);
        if (contract.getStatus() == ContractStatus.TERMINATED) {
            throw new BusinessException("Hợp đồng đã được thanh lý trước đó");
        }
        if (contract.getStatus() != ContractStatus.ACTIVE && contract.getStatus() != ContractStatus.EXPIRED) {
            throw new BusinessException("Chỉ thanh lý được hợp đồng đang ACTIVE hoặc EXPIRED");
        }

        if (request.getType() == com.sep490.slms2026.enums.ContractTerminationType.VIOLATION) {
            boolean hasOverdueInvoice = tenantInvoiceRepository.findForTenant(contract.getTenant().getUser().getId(), TenantInvoiceStatus.OVERDUE, TenantInvoiceType.RENT)
                    .stream()
                    .anyMatch(inv -> inv.getTenantContract().getId().equals(contractId) 
                            && inv.getDueDate() != null
                            && inv.getDueDate().isBefore(LocalDate.now().minusDays(2)));
            if (!hasOverdueInvoice) {
                throw new BusinessException("Không thể đơn phương chấm dứt hợp đồng (lỗi vi phạm) nếu không có hoá đơn tiền phòng quá hạn trên 3 ngày.");
            }
        }

        LocalDate effectiveDate = request.getEffectiveDate() != null
                ? request.getEffectiveDate()
                : LocalDate.now();
        if (effectiveDate.isBefore(contract.getStartDate())) {
            throw new BusinessException("Ngày chấm dứt không được trước ngày bắt đầu hợp đồng");
        }

        releaseContractOccupancy(contract);

        LocalDateTime now = LocalDateTime.now();
        contract.setStatus(ContractStatus.TERMINATED);
        contract.setTerminatedAt(now);
        contract.setTerminationType(request.getType());
        contract.setTerminationReason(request.getReason().trim());
        contract.setTerminationNote(request.getNote() != null ? request.getNote().trim() : null);
        contract.setEndDate(effectiveDate);

        TenantContract saved = tenantContractRepository.save(contract);

        if (saved.getTenant() != null && saved.getTenant().getUser() != null) {
            disableTenantAccountIfNoActiveContracts(saved.getTenant().getUser());
        }

        return toResponse(saved);
    }

    private void disableTenantAccountIfNoActiveContracts(User user) {
        if (user == null || user.getRole() != Role.ROLE_TENANT) return;
        boolean conThue = tenantContractRepository.findByTenantId(user.getId()).stream()
                .anyMatch(c -> c.getStatus() == ContractStatus.ACTIVE
                            || c.getStatus() == ContractStatus.PENDING
                            || c.getStatus() == ContractStatus.DRAFT
                            || c.getStatus() == ContractStatus.EXPIRED);
        if (!conThue) {
            user.setStatus(UserStatus.DISABLE);
            userRepository.save(user);
        }
    }

    private void releaseContractOccupancy(TenantContract contract) {
        Room room = contract.getRoom();
        if (room != null && room.getStatus() == RoomStatus.RENTED) {
            room.setStatus(RoomStatus.AVAILABLE);
            roomRepository.save(room);
        }

        Property property = contract.getProperty();
        if (room == null
                && Boolean.TRUE.equals(property.getWholeHouse())
                && property.getStatus() == PropertyStatus.RENTED) {
            property.setStatus(PropertyStatus.ACTIVE);
            propertyRepository.save(property);
        }

        contractEquipmentService.restoreDisabledByContract(contract.getId());
    }

    @Override
    @Transactional
    public List<TenantContractResponse> getContractsByStatus(String status) {
        if (status == null || status.isBlank()) {
            return tenantContractRepository.findAll().stream().map(this::toResponse).toList();
        }
        try {
            ContractStatus cs = ContractStatus.valueOf(status.toUpperCase());
            return tenantContractRepository.findByStatus(cs).stream().map(this::toResponse).toList();
        } catch (IllegalArgumentException e) {
            return new ArrayList<>();
        }
    }

    @Override
    @Transactional
    public TenantContractResponse updateDraftContract(Long contractId, com.sep490.slms2026.dto.request.UpdateDraftContractRequest request) {
        TenantContract contract = findContract(contractId);
        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new BusinessException("Chỉ có thể cập nhật hợp đồng ở trạng thái nháp");
        }
        
        if (request.getRentAmount() != null) contract.setRentAmount(request.getRentAmount());
        if (request.getDeposit() != null) contract.setDeposit(request.getDeposit());
        if (request.getDepositMonths() != null) contract.setDepositMonths(request.getDepositMonths());
        if (request.getMoveInDate() != null) {
            contract.setMoveInDate(request.getMoveInDate());
            contract.setStartDate(request.getMoveInDate());
        }
        if (request.getEndDate() != null) contract.setEndDate(request.getEndDate());
        // Luôn đồng bộ nội thất có sẵn từ nhà/phòng (không cần checkbox).
        // addedEquipments / addedEquipmentIds null = giữ phần lắp thêm hiện có.
        contractEquipmentService.resolveAndApplyHandover(
                contract,
                request.getSelectedEquipmentIds(),
                request.getDeclinedEquipmentIds(),
                request.getAddedEquipments(),
                request.getAddedEquipmentIds());
        if (request.getInitialElectricReading() != null) contract.setInitialElectricReading(request.getInitialElectricReading());
        if (request.getInitialWaterReading() != null) contract.setInitialWaterReading(request.getInitialWaterReading());
        if (request.getElectricMeterImageUrl() != null) {
            contract.setElectricMeterImageUrl(request.getElectricMeterImageUrl());
            contract.setElectricMeterCapturedAt(resolveCapturedAt(
                    request.getElectricMeterImageUrl(), request.getElectricMeterCapturedAt()));
        } else if (request.getElectricMeterCapturedAt() != null && contract.getElectricMeterImageUrl() != null) {
            contract.setElectricMeterCapturedAt(request.getElectricMeterCapturedAt());
        }
        if (request.getWaterMeterImageUrl() != null) {
            contract.setWaterMeterImageUrl(request.getWaterMeterImageUrl());
            contract.setWaterMeterCapturedAt(resolveCapturedAt(
                    request.getWaterMeterImageUrl(), request.getWaterMeterCapturedAt()));
        } else if (request.getWaterMeterCapturedAt() != null && contract.getWaterMeterImageUrl() != null) {
            contract.setWaterMeterCapturedAt(request.getWaterMeterCapturedAt());
        }

        // Ảnh đã merge vào contract — dùng URL sau merge (không lấy request, tránh miss ảnh đã có sẵn)
        requireMeterEvidence(request.getInitialElectricReading(),
                contract.getElectricMeterImageUrl(), request.getElectricMeterOverrideToken(), "điện");
        requireMeterEvidence(request.getInitialWaterReading(),
                contract.getWaterMeterImageUrl(), request.getWaterMeterOverrideToken(), "nước");

        applyMeterOverridesIfAny(contract,
                request.getElectricMeterOverrideToken(), request.getElectricMeterOverrideReason(),
                request.getInitialElectricReading(),
                request.getWaterMeterOverrideToken(), request.getWaterMeterOverrideReason(),
                request.getInitialWaterReading());
        if (request.getRoomConditionNote() != null) contract.setRoomConditionNote(request.getRoomConditionNote());
        if (request.getExpectedReceptionDate() != null) contract.setExpectedReceptionDate(request.getExpectedReceptionDate());

        if (request.getRoomConditionPhotos() != null || request.getRoomConditionUrls() != null) {
            contract.setRoomConditionPhotos(resolveRoomConditionPhotos(
                    request.getRoomConditionPhotos(), request.getRoomConditionUrls()));
        }

        if (request.getFullName() != null) contract.setDraftTenantName(request.getFullName());
        if (request.getPhoneNumber() != null) contract.setDraftTenantPhone(request.getPhoneNumber());
        if (request.getCccd() != null) contract.setDraftTenantCccd(request.getCccd());
        if (request.getDateOfBirth() != null) contract.setDraftTenantDob(request.getDateOfBirth());
        if (request.getCccdIssueDate() != null) contract.setDraftTenantCccdIssueDate(request.getCccdIssueDate());
        if (request.getCccdIssuePlace() != null) contract.setDraftTenantCccdIssuePlace(request.getCccdIssuePlace());
        if (request.getPermanentAddress() != null) contract.setDraftTenantAddress(request.getPermanentAddress());
        if (request.getDraftContractFileUrl() != null) contract.setDraftContractFileUrl(request.getDraftContractFileUrl());

        // Quản lý luôn = Operation Manager của nhà — không cho sửa tay qua API update.

        if (request.getHouseholdMembers() != null) {
            contract.getHouseholdMembers().clear();
            for (com.sep490.slms2026.dto.request.HouseholdMemberRequest m : request.getHouseholdMembers()) {
                if (m.getFullName() == null || m.getFullName().isBlank()) continue;
                contract.getHouseholdMembers().add(HouseholdMember.builder()
                        .tenantContract(contract)
                        .fullName(m.getFullName())
                        .relation(m.getRelation())
                        .phone(m.getPhone())
                        .dateOfBirth(m.getDateOfBirth())
                        .cccd(m.getCccd())
                        .build());
            }
        }

        TenantContract saved = tenantContractRepository.save(contract);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public int reassignManagerForProperty(Long propertyId, UUID newManagerId) {
        if (newManagerId == null) {
            return 0;
        }
        User manager = userRepository.findById(newManagerId).orElse(null);
        if (manager == null) {
            return 0;
        }
        List<TenantContract> contracts = tenantContractRepository.findByPropertyIdAndStatusIn(
                propertyId,
                List.of(ContractStatus.DRAFT, ContractStatus.PENDING, ContractStatus.ACTIVE));
        int count = 0;
        for (TenantContract contract : contracts) {
            UUID previousManagerId = contract.getAssignedManager() != null
                    ? contract.getAssignedManager().getId() : null;
            if (manager.getId().equals(previousManagerId)) {
                continue;
            }
            contract.setAssignedManager(manager);
            tenantContractRepository.save(contract);
            notifyAssignedManager(contract);
            count++;
        }
        return count;
    }

    @Override
    @Transactional
    public int backfillMissingAssignedManagers() {
        List<TenantContract> contracts = tenantContractRepository.findMissingAssignedManager(
                List.of(ContractStatus.DRAFT, ContractStatus.PENDING, ContractStatus.ACTIVE));
        int count = 0;
        for (TenantContract contract : contracts) {
            UUID managerId = contract.getProperty().getOperationManagerId();
            if (managerId == null) {
                continue;
            }
            User manager = userRepository.findById(managerId).orElse(null);
            if (manager == null) {
                log.warn("Backfill skip HĐ #{} — không tìm thấy user operationManagerId={}",
                        contract.getId(), managerId);
                continue;
            }
            contract.setAssignedManager(manager);
            tenantContractRepository.save(contract);
            notifyAssignedManager(contract);
            count++;
        }
        return count;
    }

    @Override
    @Transactional
    public int autoCancelNoShowContracts() {
        LocalDate cutoff = LocalDate.now().minusDays(noShowGraceDays);
        List<TenantContract> stale = tenantContractRepository.findByStatusInAndMoveInDateBefore(
                List.of(ContractStatus.DRAFT, ContractStatus.PENDING), cutoff);
        int count = 0;
        for (TenantContract contract : stale) {
            releaseContractOccupancy(contract);
            contract.setStatus(ContractStatus.TERMINATED);
            contract.setTerminatedAt(LocalDateTime.now());
            contract.setTerminationType(com.sep490.slms2026.enums.ContractTerminationType.NO_SHOW);
            contract.setTerminationReason("Tự động hủy: khách không đến nhận nhà quá " + noShowGraceDays
                    + " ngày kể từ ngày vào ở dự kiến (" + contract.getMoveInDate() + ")");
            tenantContractRepository.save(contract);
            if (contract.getTenant() != null && contract.getTenant().getUser() != null) {
                disableTenantAccountIfNoActiveContracts(contract.getTenant().getUser());
            }
            notifyContractAutoCancelled(contract);
            count++;
            log.info("Auto-cancel HĐ #{} ({}) — no-show quá {} ngày (moveInDate={})",
                    contract.getId(), contract.getContractCode(), noShowGraceDays, contract.getMoveInDate());
        }
        return count;
    }

    private void notifyContractAutoCancelled(TenantContract contract) {
        User manager = contract.getAssignedManager();
        if (manager == null) {
            return;
        }
        String tenantName = contract.getDraftTenantName();
        if ((tenantName == null || tenantName.isBlank()) && contract.getTenant() != null
                && contract.getTenant().getUser() != null) {
            tenantName = contract.getTenant().getUser().getFullName();
        }
        if (tenantName == null || tenantName.isBlank()) {
            tenantName = "khách";
        }
        String title = "Hợp đồng tự động hủy (no-show)";
        String body = "HĐ " + contract.getContractCode() + " của " + tenantName
                + " đã tự động hủy do khách không đến nhận nhà quá " + noShowGraceDays + " ngày.";
        notificationRepository.save(com.sep490.slms2026.entity.Notification.builder()
                .userId(manager.getId())
                .title(title)
                .content(body)
                .type("TENANT_CONTRACT_NO_SHOW")
                .build());
        userPushTokenService.sendToUser(manager.getId(), title, body, Map.of(
                "screen", "ResumeContract",
                "params", Map.of("contractId", contract.getId()),
                "type", "TENANT_CONTRACT_NO_SHOW"));
    }

    private TenantContract findContract(Long contractId) {
        return tenantContractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng ID: " + contractId));
    }

    private String resolveTenantPhone(TenantContract contract) {
        if (contract.getTenant() != null && contract.getTenant().getUser() != null) {
            return contract.getTenant().getUser().getPhoneNumber();
        }
        return contract.getDraftTenantPhone();
    }

    private void ensureDepositPaymentAllowed(TenantContract contract) {
        if (contract.getPriceApprovalStatus() != null
                && contract.getPriceApprovalStatus()
                        != com.sep490.slms2026.enums.PriceApprovalStatus.APPROVED_AWAITING_DEPOSIT) {
            throw new BusinessException("Hợp đồng cần được chủ nhà duyệt giá trước khi thanh toán cọc");
        }
        if (contract.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessException("Hợp đồng này đã thanh toán cọc");
        }
        if (TenantContractPaymentAmounts.resolveInitialPaymentAmount(contract).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "Số tiền thanh toán onboard không hợp lệ (cần tiền cọc hoặc tiền nhà chu kỳ đầu)");
        }
    }

    private static boolean firstRentIncluded(TenantContract contract) {
        return TenantContractPaymentAmounts.resolveFirstRentAmount(contract).compareTo(BigDecimal.ZERO) > 0;
    }

    private void ensureRoomAvailableForDeposit(TenantContract contract) {
        Room room = contract.getRoom();
        if (room != null) {
            if (room.getStatus() == RoomStatus.RENTED) {
                throw new BusinessException("Phòng đã được thuê bởi hợp đồng khác");
            }
            if (tenantContractRepository.existsByRoomIdAndStatus(room.getId(), ContractStatus.ACTIVE)) {
                throw new BusinessException("Phòng này đã có hợp đồng đang hiệu lực");
            }
        }
    }

    @Override
    @Transactional
    public List<TenantContractResponse> getContractsByProperty(Long propertyId) {
        return tenantContractRepository.findByPropertyId(propertyId).stream()
                .peek(this::syncExpiredIfNeeded)
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void syncExpiredIfNeeded(TenantContract contract) {
        if (TenantContractStatusHelper.syncExpiredIfNeeded(contract)) {
            tenantContractRepository.save(contract);
            contractEquipmentService.restoreDisabledByContract(contract.getId());
        }
    }

    private static class TenantCreationResult {
        Tenant tenant;
        boolean created;
        boolean promoted;
        TenantCreationResult(Tenant tenant, boolean created, boolean promoted) {
            this.tenant = tenant;
            this.created = created;
            this.promoted = promoted;
        }
    }

    private Tenant getOrCreateTenant(OnboardTenantRequest request) {
        return getOrCreateTenant(
                request.getPhoneNumber(),
                request.getFullName(),
                request.getCccd(),
                request.getDateOfBirth(),
                request.getCccdIssueDate(),
                request.getCccdIssuePlace(),
                request.getPermanentAddress()).tenant;
    }

    /**
     * 1 SĐT = 1 account ({@link User}/{@link Tenant}).
     * Account đó được gắn nhiều {@link TenantContract} ACTIVE ở nhà/phòng khác nhau.
     * Không chặn theo số lượng HĐ — chỉ tái dùng account theo phone.
     */
    private TenantCreationResult getOrCreateTenant(String phone, String fullName, String cccd,
                                                   LocalDate dateOfBirth,
                                                   LocalDate cccdIssueDate,
                                                   String cccdIssuePlace,
                                                   String permanentAddress) {

        String localPhone = PhoneUtils.normalizeLocal(phone);
        String internationalPhone = PhoneUtils.toInternational(localPhone);

        // Tái dùng account theo SĐT (local / +84) hoặc username (= SĐT lúc tạo)
        User existing = findExistingUserByPhone(localPhone, internationalPhone);
        if (existing != null) {
            existing.setStatus(UserStatus.ACTIVE);
            boolean promoted = false;
            if (existing.getRole() == Role.ROLE_USER) {
                // ROLE_USER → nâng quyền lên ROLE_TENANT khi onboard
                existing.setRole(Role.ROLE_TENANT);
                promoted = true;
            } else if (existing.getRole() != Role.ROLE_TENANT) {
                throw new BusinessException("Số điện thoại đã được đăng ký cho tài khoản khác (không phải khách thuê)");
            }
            // Chuẩn hóa lưu dạng local để các lần lookup sau khớp 1 format
            if (existing.getPhoneNumber() == null || !localPhone.equals(existing.getPhoneNumber())) {
                existing.setPhoneNumber(localPhone);
            }
            if (existing.getUsername() == null || existing.getUsername().isBlank()) {
                existing.setUsername(localPhone);
            }
            Tenant profile = existing.getTenantProfile();
            if (profile == null) {
                // User chưa có Tenant profile → tạo bổ sung
                profile = new Tenant();
                profile.setUser(existing);
                profile.setCccd(cccd);
                profile.setDateOfBirth(dateOfBirth);
                profile.setCccdIssueDate(cccdIssueDate);
                profile.setCccdIssuePlace(cccdIssuePlace);
                profile.setPermanentAddress(permanentAddress);
                existing.setTenantProfile(profile);
                existing = userRepository.save(existing);
                profile = existing.getTenantProfile();
            } else {
                if (cccd != null && !cccd.isBlank()) {
                    profile.setCccd(cccd);
                }
                if (dateOfBirth != null) {
                    profile.setDateOfBirth(dateOfBirth);
                }
                if (cccdIssueDate != null) {
                    profile.setCccdIssueDate(cccdIssueDate);
                }
                if (cccdIssuePlace != null) {
                    profile.setCccdIssuePlace(cccdIssuePlace.isBlank() ? null : cccdIssuePlace.trim());
                }
                if (permanentAddress != null) {
                    profile.setPermanentAddress(permanentAddress.isBlank() ? null : permanentAddress.trim());
                }
                if (fullName != null && !fullName.isBlank()
                        && (existing.getFullName() == null || existing.getFullName().isBlank())) {
                    existing.setFullName(fullName.trim());
                }
                userRepository.save(existing);
            }
            return new TenantCreationResult(profile, false, promoted);
        }

        // Chưa có → tạo mới. Không phát mật khẩu mặc định:
        // mật khẩu random + firstLogin=true → khách phải OTP kích hoạt rồi tự đặt MK.
        String username = localPhone;
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setRole(Role.ROLE_TENANT);
        user.setStatus(UserStatus.ACTIVE);
        user.setFullName(fullName != null ? fullName.trim() : null);
        user.setPhoneNumber(localPhone);
        user.setFirstLogin(true);

        Tenant profile = new Tenant();
        profile.setUser(user);
        profile.setCccd(cccd);
        profile.setDateOfBirth(dateOfBirth);
        profile.setCccdIssueDate(cccdIssueDate);
        profile.setCccdIssuePlace(cccdIssuePlace);
        profile.setPermanentAddress(permanentAddress);
        user.setTenantProfile(profile);

        try {
            User savedUser = userRepository.saveAndFlush(user);
            return new TenantCreationResult(savedUser.getTenantProfile(), true, false);
        } catch (DataIntegrityViolationException e) {
            // Race / SĐT đã có — thử tái dùng thay vì báo nhầm "trùng tên"
            User raced = findExistingUserByPhone(localPhone, internationalPhone);
            if (raced != null && (raced.getRole() == Role.ROLE_TENANT || raced.getRole() == Role.ROLE_USER)) {
                return getOrCreateTenant(localPhone, fullName, cccd, dateOfBirth,
                        cccdIssueDate, cccdIssuePlace, permanentAddress);
            }
            String detail = e.getMostSpecificCause() != null
                    ? e.getMostSpecificCause().getMessage()
                    : e.getMessage();
            if (detail != null && detail.toLowerCase().contains("phone")) {
                throw new BusinessException("Số điện thoại đã tồn tại trong hệ thống");
            }
            if (detail != null && detail.toLowerCase().contains("username")) {
                throw new BusinessException("Tài khoản với số điện thoại này đã tồn tại");
            }
            throw new BusinessException(
                    "Không tạo được tài khoản khách thuê. Vui lòng kiểm tra lại số điện thoại");
        }
    }

    private User findExistingUserByPhone(String localPhone, String internationalPhone) {
        return userRepository.findByPhoneNumber(localPhone)
                .or(() -> userRepository.findByPhoneNumber(internationalPhone))
                .or(() -> userRepository.findByUsername(localPhone))
                .or(() -> userRepository.findByUsername(internationalPhone))
                .orElse(null);
    }

    private String generateContractCode() {
        int year = Year.now().getValue();
        String prefix = "HD-MT-" + year + "-";
        for (int attempt = 0; attempt < 5; attempt++) {
            String lastCode = tenantContractRepository.findFirstByContractCodeStartingWithOrderByContractCodeDesc(prefix)
                    .map(TenantContract::getContractCode).orElse(null);
            long next = 1;
            if (lastCode != null && lastCode.length() > prefix.length()) {
                try {
                    next = Long.parseLong(lastCode.substring(prefix.length())) + 1;
                } catch (NumberFormatException e) {
                    // ignore malformed legacy codes
                }
            }
            next += attempt;
            String code = prefix + String.format("%05d", next);
            if (!tenantContractRepository.existsByContractCode(code)) {
                return code;
            }
        }
        // fallback: timestamp + random — luôn unique đủ thực tế
        return "HD-MT-" + year + "-" + (System.currentTimeMillis() % 100_000_000L)
                + ThreadLocalRandom.current().nextInt(10, 99);
    }

    private TenantContractResponse toResponse(TenantContract c) {
        return toResponse(c, null, null, null);
    }

    /**
     * Overload cho confirm: trả thêm thông tin tài khoản tenant cho FE hiển thị.
     */
    private TenantContractResponse toResponse(TenantContract c, String tenantUsername,
                                               Boolean accountCreated, Boolean rolePromoted) {
        Tenant tenant = c.getTenant();
        User tenantUser = tenant != null ? tenant.getUser() : null;
        Room room = c.getRoom();
        com.sep490.slms2026.security.CustomUserDetails currentUser = null;
        try {
            currentUser = com.sep490.slms2026.security.SecurityUtils.requireCurrentUser();
        } catch (Exception e) {
            // Ignore if no security context
        }
        boolean isManager = false;
        if (currentUser != null) {
            isManager = currentUser.getAuthorities().stream()
                    .anyMatch(a -> Role.ROLE_MANAGER.name().equals(a.getAuthority()));
        }

        return TenantContractResponse.builder()
                .id(c.getId())
                .propertyId(c.getProperty().getId())
                .propertyName(c.getProperty().getPropertyName())
                .roomId(room != null ? room.getId() : null)
                .roomNumber(room != null ? room.getRoomNumber() : null)
                .tenantUserId(tenant != null ? tenant.getId() : null)
                .tenantFullName(tenantUser != null ? tenantUser.getFullName() : c.getDraftTenantName())
                .tenantPhone(tenantUser != null ? tenantUser.getPhoneNumber() : c.getDraftTenantPhone())
                .tenantCccd(tenant != null ? tenant.getCccd() : c.getDraftTenantCccd())
                .tenantDateOfBirth(tenant != null ? tenant.getDateOfBirth() : c.getDraftTenantDob())
                .tenantCccdIssueDate(tenant != null ? tenant.getCccdIssueDate() : c.getDraftTenantCccdIssueDate())
                .tenantCccdIssuePlace(tenant != null ? tenant.getCccdIssuePlace() : c.getDraftTenantCccdIssuePlace())
                .tenantPermanentAddress(tenant != null ? tenant.getPermanentAddress() : c.getDraftTenantAddress())
                .contractCode(c.getContractCode())
                .rentAmount(isManager ? null : c.getRentAmount())
                .deposit(isManager ? null : c.getDeposit())
                .initialPaymentAmount(isManager ? null : TenantContractPaymentAmounts.resolveInitialPaymentAmount(c))
                .depositPaymentBreakdown(isManager ? null : PaymentBreakdownBuilder.forDepositOnboard(c))
                .firstRentPaymentBreakdown(isManager ? null : PaymentBreakdownBuilder.forFirstRentPreview(c))
                .moveInDate(c.getMoveInDate())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .status(c.getStatus())
                .effective(TenantContractStatusHelper.isEffective(c.getStatus(), c.getEndDate()))
                .effectiveLabel(TenantContractStatusHelper.effectiveLabel(c.getStatus(), c.getEndDate()))
                .equipmentSnapshot(c.getEquipmentSnapshot())
                .depositMonths(c.getDepositMonths())
                .initialElectricReading(c.getInitialElectricReading())
                .initialWaterReading(c.getInitialWaterReading())
                .electricMeterImageUrl(c.getElectricMeterImageUrl())
                .electricMeterCapturedAt(c.getElectricMeterCapturedAt())
                .waterMeterImageUrl(c.getWaterMeterImageUrl())
                .waterMeterCapturedAt(c.getWaterMeterCapturedAt())
                .roomConditionUrls(mapRoomConditionUrls(c.getRoomConditionPhotos()))
                .roomConditionPhotos(mapRoomConditionPhotos(c.getRoomConditionPhotos()))
                .roomConditionNote(c.getRoomConditionNote())
                .paymentStatus(c.getPaymentStatus())
                .payosOrderCode(c.getPayosOrderCode())
                .paidAt(c.getPaidAt())
                .depositPaidAt(c.getDepositPaidAt() != null
                        ? c.getDepositPaidAt()
                        : (c.getPaidAt() != null ? c.getPaidAt() : c.getDepositCashManagerConfirmedAt()))
                .depositMethod(c.getDepositMethod() != null
                        ? c.getDepositMethod()
                        : (c.getPayosOrderCode() != null
                                ? "PAYOS"
                                : (c.getDepositCashManagerConfirmedAt() != null || c.getDepositCashTenantConfirmedAt() != null
                                        ? "CASH"
                                        : null)))
                .activatedAt(c.getActivatedAt())
                .tenantUsername(tenantUsername)
                .tenantAccountCreated(accountCreated)
                .tenantRolePromoted(rolePromoted)
                .documentUrl(resolveContractFileUrl(c))
                .documentGeneratedAt(c.getDocumentGeneratedAt())
                .priceApprovalStatus(c.getPriceApprovalStatus() != null ? c.getPriceApprovalStatus().name() : null)
                .priceRejectReason(c.getPriceRejectReason())
                .assignedManagerId(c.getAssignedManager() != null ? c.getAssignedManager().getId() : null)
                .assignedManagerName(c.getAssignedManager() != null ? c.getAssignedManager().getFullName() : null)
                .draftContractFileUrl(c.getDraftContractFileUrl())
                .contractFileAvailable(resolveContractFileUrl(c) != null)
                .expectedReceptionDate(c.getExpectedReceptionDate())
                .equipmentList(contractEquipmentService.mapSelectedToItems(c))
                .availableEquipmentList(contractEquipmentService.mapAvailableToItems(
                        c.getProperty().getId(), room != null ? room.getId() : null))
                .selectedEquipmentIds(contractEquipmentService.getSelectedIds(c))
                .selectedExistingIds(contractEquipmentService.getSelectedExistingIds(c))
                .selectedAddedIds(contractEquipmentService.getSelectedAddedIds(c))
                .notes(c.getRoomConditionNote())
                .signedAt(c.getDocumentGeneratedAt() != null ? c.getDocumentGeneratedAt() : c.getPaidAt())
                .terminatedAt(c.getTerminatedAt())
                .terminationReason(c.getTerminationReason())
                .terminationType(c.getTerminationType() != null ? c.getTerminationType().name() : null)
                .householdMembers(c.getHouseholdMembers() != null ? c.getHouseholdMembers().stream()
                        .map(hm -> com.sep490.slms2026.dto.response.HouseholdMemberResponse.builder()
                                .id(hm.getId())
                                .fullName(hm.getFullName())
                                .relation(hm.getRelation())
                                .phone(hm.getPhone())
                                .dateOfBirth(hm.getDateOfBirth())
                                .cccd(hm.getCccd())
                                .build())
                        .collect(java.util.stream.Collectors.toList()) : null)
                .build();
    }

    private void notifyAssignedManager(TenantContract contract) {
        User manager = contract.getAssignedManager();
        if (manager == null) {
            return;
        }
        String tenantName = contract.getDraftTenantName();
        if ((tenantName == null || tenantName.isBlank()) && contract.getTenant() != null
                && contract.getTenant().getUser() != null) {
            tenantName = contract.getTenant().getUser().getFullName();
        }
        if (tenantName == null || tenantName.isBlank()) {
            tenantName = "khách mới";
        }
        String roomLabel = contract.getRoom() != null && contract.getRoom().getRoomNumber() != null
                ? contract.getRoom().getRoomNumber() : "nguyên căn";
        String title = "Được gán tiếp nhận khách mới";
        String body = "Bạn được gán tiếp nhận khách mới — " + tenantName
                + ", phòng " + roomLabel + " (#" + contract.getId() + ")";

        notificationRepository.save(com.sep490.slms2026.entity.Notification.builder()
                .userId(manager.getId())
                .title(title)
                .content(body)
                .type("TENANT_ONBOARDING")
                .build());

        userPushTokenService.sendToUser(manager.getId(), title, body, Map.of(
                "screen", "ResumeContract",
                "params", Map.of("contractId", contract.getId()),
                "type", "TENANT_ONBOARDING"));
    }

    private static String resolveContractFileUrl(TenantContract contract) {
        if (contract.getDraftContractFileUrl() != null && !contract.getDraftContractFileUrl().isBlank()) {
            return contract.getDraftContractFileUrl();
        }
        return contract.getDocumentUrl();
    }

    /**
     * Có URL → luôn có timestamp bằng chứng: ưu tiên FE gửi, không thì = lúc BE lưu.
     * Không có URL → null.
     */
    private static LocalDateTime resolveCapturedAt(String imageUrl, LocalDateTime capturedAt) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        return capturedAt != null ? capturedAt : LocalDateTime.now();
    }

    private static List<ContractEvidencePhoto> resolveRoomConditionPhotos(
            List<ContractEvidencePhotoRequest> photos,
            List<String> legacyUrls) {
        LocalDateTime now = LocalDateTime.now();
        List<ContractEvidencePhoto> result = new ArrayList<>();
        if (photos != null) {
            for (ContractEvidencePhotoRequest p : photos) {
                if (p == null || p.getUrl() == null || p.getUrl().isBlank()) {
                    continue;
                }
                result.add(ContractEvidencePhoto.builder()
                        .imageUrl(p.getUrl().trim())
                        .capturedAt(p.getCapturedAt() != null ? p.getCapturedAt() : now)
                        .build());
            }
            return result;
        }
        if (legacyUrls != null) {
            for (String url : legacyUrls) {
                if (url == null || url.isBlank()) {
                    continue;
                }
                result.add(ContractEvidencePhoto.builder()
                        .imageUrl(url.trim())
                        .capturedAt(now)
                        .build());
            }
        }
        return result;
    }

    private static List<String> mapRoomConditionUrls(List<ContractEvidencePhoto> photos) {
        if (photos == null || photos.isEmpty()) {
            return List.of();
        }
        return photos.stream()
                .map(ContractEvidencePhoto::getImageUrl)
                .filter(u -> u != null && !u.isBlank())
                .toList();
    }

    private static List<ContractEvidencePhotoResponse> mapRoomConditionPhotos(List<ContractEvidencePhoto> photos) {
        if (photos == null || photos.isEmpty()) {
            return List.of();
        }
        return photos.stream()
                .map(p -> ContractEvidencePhotoResponse.builder()
                        .url(p.getImageUrl())
                        .capturedAt(p.getCapturedAt())
                        .build())
                .toList();
    }
}
