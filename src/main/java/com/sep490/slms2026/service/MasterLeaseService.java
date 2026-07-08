package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.MasterLeaseRequest;
import com.sep490.slms2026.dto.response.MasterLeaseResponse;
import com.sep490.slms2026.enums.MasterLeaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MasterLeaseService {
    Page<MasterLeaseResponse> getMasterLeases(MasterLeaseStatus status, Long propertyId, Pageable pageable);
    MasterLeaseResponse getMasterLease(Long id);
    MasterLeaseResponse createMasterLease(MasterLeaseRequest request);
    MasterLeaseResponse updateMasterLease(Long id, MasterLeaseRequest request);
    void terminateMasterLease(Long id);
}
