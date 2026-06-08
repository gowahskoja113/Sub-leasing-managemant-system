package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.AddRenovationRequest;
import com.sep490.slms2026.dto.response.RenovationResponse;

import java.util.List;

public interface RenovationService {

    RenovationResponse addRenovation(Long propertyId, AddRenovationRequest request);

    List<RenovationResponse> getRenovationsByProperty(Long propertyId);
}
