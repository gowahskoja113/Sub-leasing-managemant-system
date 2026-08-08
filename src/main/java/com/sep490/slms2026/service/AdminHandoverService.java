package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.AdminHandoverStatusDto;

public interface AdminHandoverService {
    AdminHandoverStatusDto getHandoverStatus(Long propertyId);
}
