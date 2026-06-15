package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.OnboardTenantRequest;
import com.sep490.slms2026.dto.response.TenantContractResponse;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.Tenant;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.TenantOnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
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
                .moveInDate(request.getMoveInDate())
                .startDate(request.getMoveInDate())
                .endDate(request.getEndDate())
                .equipmentSnapshot(request.getEquipmentSnapshot())
                .roomConditionUrl(request.getRoomConditionUrl())
                .status(ContractStatus.ACTIVE)
                .build();

        TenantContract saved = tenantContractRepository.save(contract);

        // Chuyển phòng sang RENTED — ghi trực tiếp (endpoint PATCH /status cố tình cấm set RENTED)
        if (room != null) {
            room.setStatus(RoomStatus.RENTED);
            roomRepository.save(room);
        }

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TenantContractResponse> getContractsByProperty(Long propertyId) {
        return tenantContractRepository.findByPropertyId(propertyId).stream()
                .map(this::toResponse)
                .toList();
    }

    private Tenant getOrCreateTenant(OnboardTenantRequest request) {
        String username = "t" + request.getPhoneNumber();

        User existing = userRepository.findByUsername(username)
                .filter(u -> u.getRole() == Role.ROLE_TENANT)
                .orElse(null);
        if (existing != null) {
            Tenant profile = existing.getTenantProfile();
            if (profile == null) {
                // Trường hợp hiếm: user tenant chưa có profile -> tạo bổ sung
                profile = new Tenant();
                profile.setUser(existing);
                profile.setCccd(request.getCccd());
                existing.setTenantProfile(profile);
                userRepository.save(existing);
            }
            return profile;
        }

        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new BusinessException("Số điện thoại đã được đăng ký cho tài khoản khác");
        }

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
                .build();
    }
}
