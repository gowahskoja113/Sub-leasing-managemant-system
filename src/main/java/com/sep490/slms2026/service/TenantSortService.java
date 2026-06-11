package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.PropertyResponse;
import com.sep490.slms2026.dto.response.TenantSortResponse;

import java.util.List;

public interface TenantSortService {

    List<TenantSortResponse> getMostPropertiesByZone();

    List<TenantSortResponse> getMostRoomsByZone();

    List<PropertyResponse> getPropertiesPriceAsc();

    List<PropertyResponse> getPropertiesPriceDesc();
}