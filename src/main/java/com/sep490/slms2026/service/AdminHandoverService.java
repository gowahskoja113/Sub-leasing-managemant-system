package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.AdminHandoverStatusDto;

import java.util.List;

public interface AdminHandoverService {
    /** Chi tiết 1 toà (có rooms[]). */
    AdminHandoverStatusDto getHandoverStatus(Long propertyId);

    /** Tóm tắt toàn bộ toà — bảng admin (không kèm rooms[]). */
    List<AdminHandoverStatusDto> listHandoverStatus();
}
