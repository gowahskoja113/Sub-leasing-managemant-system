package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.TenantHandoverResponse;

import java.util.UUID;

public interface TenantHandoverService {

    TenantHandoverResponse getHandover(UUID tenantUserId);

    TenantHandoverResponse acknowledgeHandover(UUID tenantUserId);
}
