package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.TenantCreationRequest;
import com.sep490.slms2026.dto.response.TenantResponse;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.Tenant;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.mapper.UserMapper;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.UserService;
import com.sep490.slms2026.util.SmsService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@AllArgsConstructor
public class UserServiceImpl implements UserService {

    @Autowired
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SmsService smsService;
    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;

    @Override
    public User createUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        String username = user.getUsername();
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username đã tồn tại trên hệ thống!");
        }

        String phone = user.getPhoneNumber();
        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("Phone number is required");
        }
        if (userRepository.existsByPhoneNumber(phone)) {
            throw new RuntimeException("Số điện thoại đã được đăng ký!");
        }

        String rawPassword = user.getPassword();
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        String encoded = passwordEncoder.encode(rawPassword);
        user.setPassword(encoded);

        return userRepository.save(user);
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public User getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với ID: " + id));
    }

    @Override
    public User updateUser(UUID id, User userDetails) {
        User existingUser = getUserById(id);

        // Tránh trùng số điện thoại khi đổi thông tin công khai
        if (!existingUser.getPhoneNumber().equals(userDetails.getPhoneNumber())
                && userRepository.existsByPhoneNumber(userDetails.getPhoneNumber())) {
            throw new RuntimeException("Số điện thoại mới đã tồn tại!");
        }

        existingUser.setPhoneNumber(userDetails.getPhoneNumber());
        if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
            existingUser.setPassword(userDetails.getPassword()); // Cân nhắc mã hóa BCrypt sau này
        }
        existingUser.setRole(userDetails.getRole());

        return userRepository.save(existingUser);
    }

    @Override
    @Transactional
    public User changeUserStatus(UUID id, UserStatus newStatus) {
        User user = getUserById(id);
        user.setStatus(newStatus);
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public TenantResponse createTenant(TenantCreationRequest request) {

        // Validate: không được truyền cả roomId lẫn propertyId cùng lúc
        if (request.getRoomId() != null && request.getPropertyId() != null) {
            throw new RuntimeException("Chỉ được gán tenant vào phòng HOẶC nguyên căn, không được cả hai!");
        }

        // 1. Validate thông tin User
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username đã tồn tại trên hệ thống!");
        }
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new RuntimeException("Số điện thoại đã được đăng ký!");
        }

        // 2. Validate thông tin Tenant (CCCD không được trùng)
        if (tenantRepository.existsByCitizenIdNumber(request.getCitizenIdNumber())) {
            throw new RuntimeException("Số CCCD đã tồn tại trong hệ thống!");
        }

        // Mật khẩu SMS (biến {P}) phải trùng mật khẩu lưu DB
        String rawPassword = smsService.preparePasswordForTenantSms(request.getPassword());

        // 3. Khởi tạo User
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setPhoneNumber(request.getPhoneNumber());
        user.setRole(Role.ROLE_TENANT);
        user.setStatus(UserStatus.ACTIVE);

        // 4. Khởi tạo Tenant
        Tenant tenant = new Tenant();
        tenant.setFullName(request.getFullName());
        tenant.setCitizenIdNumber(request.getCitizenIdNumber());
        tenant.setStartDate(LocalDate.now());

        // 5. Phân nhánh gán phòng hoặc nguyên căn
        if (request.getRoomId() != null) {
            // --- Trường hợp thuê phòng ---
            // FIX LỖI Ở ĐÂY: Đổi sang findById để dùng được orElseThrow
            Room room = roomRepository.findById(request.getRoomId())
                    .orElseThrow(() -> new RuntimeException("Phòng không tồn tại!"));

            // ĐỒNG BỘ ENUM: Nên check theo Enum thay vì so sánh String "OCCUPIED"
            if (RoomStatus.AVAILABLE.equals(room.getStatus()) || RoomStatus.RENTED.equals(room.getStatus())) {
                throw new RuntimeException("Phòng đã có người ở, không thể gán thêm!");
            }

            tenant.setRoom(room);
            tenant.setProperty(null);
            tenant.setRoomRentalStatus("RENTING");

            room.setStatus(RoomStatus.RENTED);
            roomRepository.save(room);

        } else if (request.getPropertyId() != null) {
            // --- Trường hợp thuê nguyên căn ---
            Property property = propertyRepository.findById(request.getPropertyId())
                    .orElseThrow(() -> new RuntimeException("Property không tồn tại!"));

            if (!property.getWholeHouse()) {
                throw new RuntimeException("Property này không phải nguyên căn, hãy chọn phòng cụ thể!");
            }

            // ĐỒNG BỘ ENUM: Thay vì dùng chuỗi "OCCUPIED", hãy dùng PropertyStatus.OCCUPIED
            if (PropertyStatus.OCCUPIED.equals(property.getPropertyStatus())) {
                throw new RuntimeException("Nguyên căn này đã có người thuê!");
            }

            tenant.setProperty(property);
            tenant.setRoom(null);
            tenant.setRoomRentalStatus("RENTING");

            property.setPropertyStatus(PropertyStatus.OCCUPIED);
            propertyRepository.save(property);

        } else {
            // Không gán phòng nào — tạo tenant trống
            tenant.setRoomRentalStatus("NO_ROOM");
        }

        // 6. Liên kết User và Tenant
        tenant.setUser(user);
        user.setTenantProfile(tenant);

        // 7. Lưu xuống DB
        User savedUser = userRepository.save(user);

        // Gửi SMS bằng password chưa mã hóa
        smsService.sendCredentialsSms(savedUser.getPhoneNumber(), savedUser.getUsername(), rawPassword);

        // Dùng mapper chuyển Entity thành Response DTO rồi mới trả về
        return userMapper.toTenantResponse(savedUser);
    }
}