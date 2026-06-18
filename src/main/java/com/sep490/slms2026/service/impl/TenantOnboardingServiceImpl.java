package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.HouseholdMemberRequest;
import com.sep490.slms2026.dto.request.OnboardTenantRequest;
import com.sep490.slms2026.dto.response.TenantContractResponse;
import com.sep490.slms2026.entity.HouseholdMember;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.Tenant;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.PaymentStatus;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.PayosService;
import com.sep490.slms2026.service.TenantOnboardingService;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class TenantOnboardingServiceImpl implements TenantOnboardingService {

    private static final String DEFAULT_TENANT_PASSWORD = "123456";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final TenantContractRepository tenantContractRepository;
    private final PayosService payosService;

    @Override
    @Transactional
    public TenantContractResponse onboardTenant(Long propertyId, Long roomId, OnboardTenantRequest request) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tòa nhà với ID: " + propertyId));

        // Validate ngày
        if (request.getEndDate() != null && !request.getEndDate().isAfter(request.getMoveInDate())) {
            throw new BusinessException("Ngày kết thúc hợp đồng phải sau ngày vào ở");
        }

        Room room = null;
        if (roomId != null) {
            room = roomRepository.findByIdAndPropertyId(roomId, propertyId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy phòng ID " + roomId + " thuộc tòa nhà ID " + propertyId));

            // Quy tắc 1-HĐ-active theo phòng
            if (tenantContractRepository.existsByRoomIdAndStatus(roomId, ContractStatus.ACTIVE)) {
                throw new BusinessException("Phòng này đã có hợp đồng đang hiệu lực");
            }
            if (room.getStatus() == RoomStatus.RENTED) {
                throw new BusinessException("Phòng này đang được cho thuê");
            }
        } else {
            // Thuê nguyên căn: chặn nếu đã có HĐ active cấp tòa
            if (tenantContractRepository.existsByPropertyIdAndRoomIsNullAndStatus(propertyId, ContractStatus.ACTIVE)) {
                throw new BusinessException("Căn nhà này đã có hợp đồng nguyên căn đang hiệu lực");
            }
        }

        Tenant tenant = getOrCreateTenant(request);

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
                .status(request.isRequireDepositPayment() ? ContractStatus.PENDING : ContractStatus.ACTIVE)
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

        TenantContract saved = tenantContractRepository.save(contract);

        // Chỉ set phòng RENTED khi HĐ kích hoạt ngay (không yêu cầu thanh toán cọc trước).
        // Với luồng có thanh toán (mobile): phòng sẽ được set RENTED ở bước confirm.
        if (room != null && !request.isRequireDepositPayment()) {
            room.setStatus(RoomStatus.RENTED);
            roomRepository.save(room);
        }

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantContractResponse getContract(Long contractId) {
        return toResponse(findContract(contractId));
    }

    @Override
    @Transactional
    public TenantContractResponse createDepositPayment(Long contractId) {
        TenantContract contract = findContract(contractId);
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
    public TenantContractResponse confirmContract(Long contractId) {
        TenantContract contract = findContract(contractId);

        if (contract.getStatus() == ContractStatus.ACTIVE) {
            return toResponse(contract); // idempotent
        }
        if (contract.getPaymentStatus() != PaymentStatus.PAID) {
            throw new BusinessException("Chưa thanh toán cọc, không thể hoàn tất hợp đồng");
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
        return toResponse(tenantContractRepository.save(contract));
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

    private TenantContract findContract(Long contractId) {
        return tenantContractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng ID: " + contractId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantContractResponse> getContractsByProperty(Long propertyId) {
        return tenantContractRepository.findByPropertyId(propertyId).stream()
                .map(this::toResponse)
                .toList();
    }

    private Tenant getOrCreateTenant(OnboardTenantRequest request) {
        String phone = request.getPhoneNumber();

        // Tái dùng tài khoản đã có theo SĐT (đồng bộ với chức năng tra cứu tự điền)
        User existing = userRepository.findByPhoneNumber(phone).orElse(null);
        if (existing != null) {
            if (existing.getRole() != Role.ROLE_TENANT) {
                throw new BusinessException("Số điện thoại đã được đăng ký cho tài khoản khác (không phải khách thuê)");
            }
            Tenant profile = existing.getTenantProfile();
            if (profile == null) {
                // Trường hợp hiếm: user tenant chưa có profile -> tạo bổ sung
                profile = new Tenant();
                profile.setUser(existing);
                profile.setCccd(request.getCccd());
                existing.setTenantProfile(profile);
                existing = userRepository.save(existing);
                profile = existing.getTenantProfile();
            }
            return profile;
        }

        // Chưa có -> tạo mới
        String username = "t" + phone;
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(DEFAULT_TENANT_PASSWORD));
        user.setRole(Role.ROLE_TENANT);
        user.setStatus(UserStatus.ACTIVE);
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());

        Tenant profile = new Tenant();
        profile.setUser(user);
        profile.setCccd(request.getCccd());
        user.setTenantProfile(profile);

        try {
            User savedUser = userRepository.saveAndFlush(user);
            return savedUser.getTenantProfile();
        } catch (DataIntegrityViolationException e) {
            // fullName là UNIQUE trên bảng User -> trùng tên sẽ vi phạm ràng buộc
            throw new BusinessException(
                    "Tên khách thuê hoặc SĐT đã tồn tại trong hệ thống, vui lòng kiểm tra lại");
        }
    }

    private String generateContractCode() {
        long next = tenantContractRepository.count() + 1;
        return "HD-MT-" + Year.now().getValue() + "-" + String.format("%05d", next);
    }

    private TenantContractResponse toResponse(TenantContract c) {
        Tenant tenant = c.getTenant();
        User tenantUser = tenant.getUser();
        Room room = c.getRoom();
        return TenantContractResponse.builder()
                .id(c.getId())
                .propertyId(c.getProperty().getId())
                .roomId(room != null ? room.getId() : null)
                .roomNumber(room != null ? room.getRoomNumber() : null)
                .tenantUserId(tenant.getId())
                .tenantFullName(tenantUser.getFullName())
                .tenantPhone(tenantUser.getPhoneNumber())
                .tenantCccd(tenant.getCccd())
                .contractCode(c.getContractCode())
                .rentAmount(c.getRentAmount())
                .deposit(c.getDeposit())
                .moveInDate(c.getMoveInDate())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .status(c.getStatus())
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
                .build();
    }
}
