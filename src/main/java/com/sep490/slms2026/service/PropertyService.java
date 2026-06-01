package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.ZoneSummaryProjection;
import com.sep490.slms2026.dto.request.PropertyRequest;
import com.sep490.slms2026.dto.response.PropertyResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PropertyService {
    public List<ZoneSummaryProjection> getManagerDashboard(UUID managerId);
    public PropertyResponse createProperty(PropertyRequest request, UUID managerId);
    public Page<PropertyResponse> getPropertiesForManager(UUID managerId, Pageable pageable);
    public PropertyResponse updateProperty(UUID id, PropertyRequest request, UUID managerId);
    public void deleteProperty(UUID id, UUID managerId);

}
