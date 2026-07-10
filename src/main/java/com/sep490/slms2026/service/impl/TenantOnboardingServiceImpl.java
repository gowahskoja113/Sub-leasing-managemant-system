package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.ContractAddedEquipmentRequest;
import com.sep490.slms2026.dto.request.HouseholdMemberRequest;
import com.sep490.slms2026.dto.request.OnboardTenantRequest;
import com.sep490.slms2026.dto.request.TerminateContractRequest;
import com.sep490.slms2026.dto.response.TenantContractResponse;
import com.sep490.slms2026.entity.HouseholdMember;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.Tenant;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.OtpPurpose;
import com.sep490.slms2026.enums.PaymentStatus;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.repository.NotificationRepository;
import com.sep490.slms2026.service.ContractEquipmentService;
import com.sep490.slms2026.service.OtpService;
import com.sep490.slms2026.service.PayosService;
import com.sep490.slms2026.service.PushNotificationService;
import com.sep490.slms2026.service.TenantOnboardingService;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantOnboardingServiceImpl implements TenantOnboardingService {

    private static final String DEFAULT_TENANT_PASSWORD = "tenant123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final TenantContractRepository tenantContractRepository;
    private final PayosService payosService;
    private final OtpService otpService;
    private final ContractEquipmentService contractEquipmentService;
    private final NotificationRepository notificationRepository;
    private final PushNotificationService pushNotificationService;

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

        Tenant tenant = request.isDraft() ? null : getOrCreateTenant(request);

        User assignedManager = null;
        if (request.getAssignedManagerId() != null && !request.getAssignedManagerId().isBlank()) {
            assignedManager = userRepository.findById(java.util.UUID.fromString(request.getAssignedManagerId()))
                    .orElse(null);
        }

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
                .waterMeterImageUrl(request.getWaterMeterImageUrl())
                .roomConditionUrls(request.getRoomConditionUrls() != null
                        ? new ArrayList<>(request.getRoomConditionUrls()) : new ArrayList<>())
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

        TenantContract saved = tenantContractRepository.save(contract);

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
            Room room = contract.getRoom();
            if (room != null) {
                if (room.getStatus() == RoomStatus.RENTED) {
                    throw new BusinessException("Phòng đã được thuê bởi hợp đồng khác");
                }
                if (tenantContractRepository.existsByRoomIdAndStatus(room.getId(), ContractStatus.ACTIVE)) {
                    throw new BusinessException("Phòng này đã có hợp đồng đang hiệu lực");
                }
            }
            if (contract.getMoveInDate() == null || contract.getMoveInDate().isBefore(LocalDate.now())) {
                throw new BusinessException("Ngày vào ở không hợp lệ để thu cọc");
            }
            contract.setStatus(ContractStatus.PENDING);
        }

        if (contract.getPriceApprovalStatus() != null && contract.getPriceApprovalStatus() != com.sep490.slms2026.enums.PriceApprovalStatus.APPROVED_AWAITING_DEPOSIT) {
            throw new BusinessException("Hợp đồng cần được chủ nhà duyệt giá trước khi thanh toán cọc");
        }
        if (contract.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessException("Hợp đồng này đã thanh toán cọc");
        }
        BigDecimal deposit = contract.getDeposit() != null ? contract.getDeposit() : BigDecimal.ZERO;
        long amount = deposit.longValue();
        if (amount <= 0) {
            throw new BusinessException("Số tiền cọc không hợp lệ");
        }
        long orderCode = System.currentTimeMillis(); // duy nhất, < giới hạn PayOS
        PayosService.PaymentLink link = payosService.createPaymentLink(
                orderCode, amount, "Coc HD " + contract.getId());

        contract.setPayosOrderCode(link.orderCode);
        contract.setPaymentStatus(PaymentStatus.PENDING);
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
            throw new BusinessException("Chưa thanh toán cọc, không thể hoàn tất hợp đồng");
        }

        String tenantPhone = contract.getTenant() != null ? contract.getTenant().getUser().getPhoneNumber() : contract.getDraftTenantPhone();
        if (tenantPhone == null) {
            throw new BusinessException("Không tìm thấy số điện thoại khách thuê để gửi OTP");
        }
        otpService.verifyOrThrow(tenantPhone, otp, OtpPurpose.CONTRACT_CONFIRM, contractId);

        boolean accountCreated = false;
        boolean rolePromoted = false;
        if (contract.getTenant() == null) {
            TenantCreationResult result = getOrCreateTenant(
                    contract.getDraftTenantPhone(),
                    contract.getDraftTenantName(),
                    contract.getDraftTenantCccd(),
                    contract.getDraftTenantDob());
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
        TenantContract saved = tenantContractRepository.save(contract);
        contractEquipmentService.disableDeclinedForActiveContract(saved);

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
            throw new BusinessException("Chưa thanh toán cọc, không thể gửi OTP xác nhận");
        }
        String tenantPhone = contract.getTenant() != null ? contract.getTenant().getUser().getPhoneNumber() : contract.getDraftTenantPhone();
        if (tenantPhone == null) {
            throw new BusinessException("Không tìm thấy số điện thoại khách thuê để gửi OTP");
        }
        otpService.sendOtp(tenantPhone, OtpPurpose.CONTRACT_CONFIRM, contractId);
    }

    @Override
    @Transactional
    public TenantContractResponse syncPaymentStatus(Long contractId) {
        TenantContract contract = findContract(contractId);
        if (contract.getPaymentStatus() != PaymentStatus.PAID && contract.getPayosOrderCode() != null) {
            String status = payosService.getPaymentStatus(contract.getPayosOrderCode());
            if ("PAID".equalsIgnoreCase(status)) {
                contract.setPaymentStatus(PaymentStatus.PAID);
                contract.setPaidAt(LocalDateTime.now());
                contract = tenantContractRepository.save(contract);
            }
        }
        return toResponse(contract);
    }

    @Override
    @Transactional
    public void markDepositPaid(Long payosOrderCode) {
        tenantContractRepository.findByPayosOrderCode(payosOrderCode).ifPresent(contract -> {
            contract.setPaymentStatus(PaymentStatus.PAID);
            contract.setPaidAt(LocalDateTime.now());
            tenantContractRepository.save(contract);
        });
    }

    @Override
    @Transactional
    public List<TenantContractResponse> getManagedContracts(String status) {
        java.util.UUID managerUserId = com.sep490.slms2026.security.SecurityUtils.requireCurrentUser().getId();
        List<TenantContract> contracts;
        if (status != null && !status.isBlank()) {
            if ("DRAFT".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status)) {
                try {
                    ContractStatus contractStatus = ContractStatus.valueOf(status.toUpperCase());
                    contracts = tenantContractRepository.findManagedContractsByStatus(managerUserId, contractStatus);
                } catch (IllegalArgumentException e) {
                    contracts = new ArrayList<>();
                }
            } else {
                try {
                    com.sep490.slms2026.enums.PriceApprovalStatus enumStatus = com.sep490.slms2026.enums.PriceApprovalStatus.valueOf(status.toUpperCase());
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
        return toResponse(saved);
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
        if (request.getSelectedEquipmentIds() != null
                || request.getDeclinedEquipmentIds() != null
                || request.getAddedEquipments() != null
                || request.getAddedEquipmentIds() != null) {
            contractEquipmentService.resolveAndApplyHandover(
                    contract,
                    request.getSelectedEquipmentIds(),
                    request.getDeclinedEquipmentIds(),
                    request.getAddedEquipments(),
                    request.getAddedEquipmentIds());
        } else if (request.getEquipmentSnapshot() != null) {
            contract.setEquipmentSnapshot(request.getEquipmentSnapshot());
        }
        if (request.getInitialElectricReading() != null) contract.setInitialElectricReading(request.getInitialElectricReading());
        if (request.getInitialWaterReading() != null) contract.setInitialWaterReading(request.getInitialWaterReading());
        if (request.getElectricMeterImageUrl() != null) contract.setElectricMeterImageUrl(request.getElectricMeterImageUrl());
        if (request.getWaterMeterImageUrl() != null) contract.setWaterMeterImageUrl(request.getWaterMeterImageUrl());
        if (request.getRoomConditionNote() != null) contract.setRoomConditionNote(request.getRoomConditionNote());
        if (request.getExpectedReceptionDate() != null) contract.setExpectedReceptionDate(request.getExpectedReceptionDate());
        
        if (request.getRoomConditionUrls() != null) {
            contract.setRoomConditionUrls(new ArrayList<>(request.getRoomConditionUrls()));
        }

        if (request.getFullName() != null) contract.setDraftTenantName(request.getFullName());
        if (request.getPhoneNumber() != null) contract.setDraftTenantPhone(request.getPhoneNumber());
        if (request.getCccd() != null) contract.setDraftTenantCccd(request.getCccd());
        if (request.getDateOfBirth() != null) contract.setDraftTenantDob(request.getDateOfBirth());
        if (request.getDraftContractFileUrl() != null) contract.setDraftContractFileUrl(request.getDraftContractFileUrl());

        boolean notifyManager = false;
        if (request.getAssignedManagerId() != null) {
            User manager = userRepository.findById(request.getAssignedManagerId())
                    .orElseThrow(() -> new BusinessException("Không tìm thấy quản lý"));
            UUID previousManagerId = contract.getAssignedManager() != null
                    ? contract.getAssignedManager().getId() : null;
            contract.setAssignedManager(manager);
            notifyManager = !manager.getId().equals(previousManagerId);
        }

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
        if (notifyManager && saved.getAssignedManager() != null) {
            notifyAssignedManager(saved);
        }
        return toResponse(saved);
    }

    @Override
    @Transactional
    public TenantContractResponse assignManager(Long contractId, com.sep490.slms2026.dto.request.AssignManagerRequest request) {
        TenantContract contract = findContract(contractId);
        UUID previousManagerId = contract.getAssignedManager() != null
                ? contract.getAssignedManager().getId() : null;

        if (request.getAssignedManagerId() != null) {
            User manager = userRepository.findById(request.getAssignedManagerId())
                    .orElseThrow(() -> new BusinessException("Không tìm thấy quản lý"));
            contract.setAssignedManager(manager);
        } else {
            contract.setAssignedManager(null);
        }
        
        if (request.getExpectedReceptionDate() != null) {
            contract.setExpectedReceptionDate(request.getExpectedReceptionDate());
        }

        TenantContract saved = tenantContractRepository.save(contract);
        if (saved.getAssignedManager() != null
                && !saved.getAssignedManager().getId().equals(previousManagerId)) {
            notifyAssignedManager(saved);
        }
        return toResponse(saved);
    }

    private TenantContract findContract(Long contractId) {
        return tenantContractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng ID: " + contractId));
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
                request.getDateOfBirth()).tenant;
    }

    private TenantCreationResult getOrCreateTenant(String phone, String fullName, String cccd, LocalDate dateOfBirth) {

        // Tái dùng tài khoản đã có theo SĐT (đồng bộ với chức năng tra cứu tự điền)
        User existing = userRepository.findByPhoneNumber(phone).orElse(null);
        if (existing != null) {
            boolean promoted = false;
            if (existing.getRole() == Role.ROLE_USER) {
                // ROLE_USER → nâng quyền lên ROLE_TENANT khi onboard
                existing.setRole(Role.ROLE_TENANT);
                if (existing.getPhoneNumber() == null) {
                    existing.setPhoneNumber(phone);
                }
                promoted = true;
            } else if (existing.getRole() != Role.ROLE_TENANT) {
                throw new BusinessException("Số điện thoại đã được đăng ký cho tài khoản khác (không phải khách thuê)");
            }
            Tenant profile = existing.getTenantProfile();
            if (profile == null) {
                // User chưa có Tenant profile → tạo bổ sung
                profile = new Tenant();
                profile.setUser(existing);
                profile.setCccd(cccd);
                profile.setDateOfBirth(dateOfBirth);
                existing.setTenantProfile(profile);
                existing = userRepository.save(existing);
                profile = existing.getTenantProfile();
            } else if (dateOfBirth != null) {
                profile.setDateOfBirth(dateOfBirth);
            }
            return new TenantCreationResult(profile, false, promoted);
        }

        // Chưa có → tạo mới
        String username = phone;
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(DEFAULT_TENANT_PASSWORD));
        user.setRole(Role.ROLE_TENANT);
        user.setStatus(UserStatus.ACTIVE);
        user.setFullName(fullName);
        user.setPhoneNumber(phone);
        user.setFirstLogin(true);

        Tenant profile = new Tenant();
        profile.setUser(user);
        profile.setCccd(cccd);
        profile.setDateOfBirth(dateOfBirth);
        user.setTenantProfile(profile);

        try {
            User savedUser = userRepository.saveAndFlush(user);
            return new TenantCreationResult(savedUser.getTenantProfile(), true, false);
        } catch (DataIntegrityViolationException e) {
            // fullName là UNIQUE trên bảng User → trùng tên sẽ vi phạm ràng buộc
            throw new BusinessException(
                    "Tên khách thuê hoặc SĐT đã tồn tại trong hệ thống, vui lòng kiểm tra lại");
        }
    }

    private String generateContractCode() {
        long next = tenantContractRepository.count() + 1;
        return "HD-MT-" + Year.now().getValue() + "-" + String.format("%05d", next);
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
        return TenantContractResponse.builder()
                .id(c.getId())
                .propertyId(c.getProperty().getId())
                .roomId(room != null ? room.getId() : null)
                .roomNumber(room != null ? room.getRoomNumber() : null)
                .tenantUserId(tenant != null ? tenant.getId() : null)
                .tenantFullName(tenantUser != null ? tenantUser.getFullName() : c.getDraftTenantName())
                .tenantPhone(tenantUser != null ? tenantUser.getPhoneNumber() : c.getDraftTenantPhone())
                .tenantCccd(tenant != null ? tenant.getCccd() : c.getDraftTenantCccd())
                .tenantDateOfBirth(tenant != null ? tenant.getDateOfBirth() : c.getDraftTenantDob())
                .contractCode(c.getContractCode())
                .rentAmount(c.getRentAmount())
                .deposit(c.getDeposit())
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
                .waterMeterImageUrl(c.getWaterMeterImageUrl())
                .roomConditionUrls(c.getRoomConditionUrls())
                .roomConditionNote(c.getRoomConditionNote())
                .paymentStatus(c.getPaymentStatus())
                .payosOrderCode(c.getPayosOrderCode())
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

        String token = manager.getPushToken();
        if (token != null && !token.isBlank()) {
            pushNotificationService.sendPushNotification(
                    token, title, body, Map.of("contractId", contract.getId()));
        }
    }

    private static String resolveContractFileUrl(TenantContract contract) {
        if (contract.getDraftContractFileUrl() != null && !contract.getDraftContractFileUrl().isBlank()) {
            return contract.getDraftContractFileUrl();
        }
        return contract.getDocumentUrl();
    }
}
