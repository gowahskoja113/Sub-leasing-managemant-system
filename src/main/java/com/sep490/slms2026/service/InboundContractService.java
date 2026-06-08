package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.CreateInboundContractRequest;
import com.sep490.slms2026.dto.response.InboundContractResponse;

public interface InboundContractService {

    InboundContractResponse signContract(Long propertyId, CreateInboundContractRequest request);

    InboundContractResponse getContractByProperty(Long propertyId);
}
