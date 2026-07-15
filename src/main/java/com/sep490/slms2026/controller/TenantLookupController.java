package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.TenantLookupResponse;
import com.sep490.slms2026.entity.Tenant;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Tra cứu khách thuê đã có sẵn theo SĐT để tự điền form onboarding.
 */
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantLookupController {

    private final UserRepository userRepository;

    @GetMapping("/lookup")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<TenantLookupResponse> lookupByPhone(@RequestParam String phone) {
        User user = userRepository.findByPhoneNumber(phone)
                .orElse(null);

        if (user == null) {
            return ResponseEntity.ok(TenantLookupResponse.builder().exists(false).build());
        }

        boolean eligible = (user.getRole() == Role.ROLE_USER || user.getRole() == Role.ROLE_TENANT);

        Tenant profile = user.getTenantProfile();
        return ResponseEntity.ok(TenantLookupResponse.builder()
                .exists(true)
                .eligible(eligible)
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .cccd(profile != null ? profile.getCccd() : null)
                .dateOfBirth(profile != null ? profile.getDateOfBirth() : null)
                .cccdIssueDate(profile != null ? profile.getCccdIssueDate() : null)
                .cccdIssuePlace(profile != null ? profile.getCccdIssuePlace() : null)
                .role(user.getRole() != null ? user.getRole().name() : null)
                .build());
    }
}
