package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.PropertyAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PropertyAccessServiceImpl implements PropertyAccessService {

    private final PropertyRepository propertyRepository;

    @Override
    public void assertCanManageProperty(Long propertyId) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        if (user.getAuthorities().stream().anyMatch(a -> Role.ROLE_ADMIN.name().equals(a.getAuthority()))) {
            return;
        }

        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tòa nhà với ID: " + propertyId));

        if (!user.getId().equals(property.getOperationManagerId())) {
            throw new BusinessException("Bạn không có quyền quản lý tòa nhà này");
        }
    }
}
