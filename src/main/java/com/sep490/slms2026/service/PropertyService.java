package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.PropertyCreateRequest;
import com.sep490.slms2026.dto.response.PropertyResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PropertyService {

    PropertyResponse createProperty(PropertyCreateRequest request);

    PropertyResponse getPropertyById(Long id);

    Page<PropertyResponse> getAllProperties(Pageable pageable);

    /** Danh sách BĐS còn cho thuê được (dùng cho màn onboarding đón khách). */
    List<PropertyResponse> getRentableProperties();

    PropertyResponse updateProperty(Long id, PropertyCreateRequest request);

    void deleteProperty(Long id);
}