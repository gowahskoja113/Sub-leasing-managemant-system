package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.MasterLeaseRequest;
import com.sep490.slms2026.dto.response.MasterLeaseResponse;
import com.sep490.slms2026.entity.MasterLease;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.enums.MasterLeaseStatus;
import com.sep490.slms2026.repository.MasterLeaseRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.service.MasterLeaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MasterLeaseServiceImpl implements MasterLeaseService {

    private final MasterLeaseRepository repository;
    private final PropertyRepository propertyRepository;

    @Override
    public Page<MasterLeaseResponse> getMasterLeases(MasterLeaseStatus status, Long propertyId, Pageable pageable) {
        if (status != null && propertyId != null) {
            return repository.findByStatusAndPropertyIdAndDeletedFalse(status, propertyId, pageable).map(this::convertToResponse);
        } else if (status != null) {
            return repository.findByStatusAndDeletedFalse(status, pageable).map(this::convertToResponse);
        } else if (propertyId != null) {
            return repository.findByPropertyIdAndDeletedFalse(propertyId, pageable).map(this::convertToResponse);
        }
        return repository.findByDeletedFalse(pageable).map(this::convertToResponse);
    }

    @Override
    public MasterLeaseResponse getMasterLease(Long id) {
        return convertToResponse(repository.findById(id).orElseThrow());
    }

    @Override
    public MasterLeaseResponse createMasterLease(MasterLeaseRequest request) {
        Property property = propertyRepository.findById(request.getPropertyId()).orElseThrow();
        MasterLease lease = MasterLease.builder()
                .property(property)
                .ownerName(request.getOwnerName())
                .ownerPhone(request.getOwnerPhone())
                .monthlyRent(request.getMonthlyRent())
                .deposit(request.getDeposit())
                .paymentDay(request.getPaymentDay())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .escalationPct(request.getEscalationPct())
                .status(MasterLeaseStatus.ACTIVE)
                .build();
        return convertToResponse(repository.save(lease));
    }

    @Override
    public MasterLeaseResponse updateMasterLease(Long id, MasterLeaseRequest request) {
        MasterLease lease = repository.findById(id).orElseThrow();
        lease.setOwnerName(request.getOwnerName());
        lease.setOwnerPhone(request.getOwnerPhone());
        lease.setMonthlyRent(request.getMonthlyRent());
        lease.setDeposit(request.getDeposit());
        lease.setPaymentDay(request.getPaymentDay());
        lease.setStartDate(request.getStartDate());
        lease.setEndDate(request.getEndDate());
        lease.setEscalationPct(request.getEscalationPct());
        return convertToResponse(repository.save(lease));
    }

    @Override
    public void terminateMasterLease(Long id) {
        MasterLease lease = repository.findById(id).orElseThrow();
        lease.setStatus(MasterLeaseStatus.TERMINATED);
        repository.save(lease);
    }

    private MasterLeaseResponse convertToResponse(MasterLease lease) {
        MasterLeaseResponse res = new MasterLeaseResponse();
        res.setId(lease.getId());
        res.setPropertyId(lease.getProperty().getId());
        res.setPropertyName(lease.getProperty().getPropertyName());
        res.setOwnerName(lease.getOwnerName());
        res.setOwnerPhone(lease.getOwnerPhone());
        res.setMonthlyRent(lease.getMonthlyRent());
        res.setDeposit(lease.getDeposit());
        res.setPaymentDay(lease.getPaymentDay());
        res.setStartDate(lease.getStartDate());
        res.setEndDate(lease.getEndDate());
        res.setEscalationPct(lease.getEscalationPct());
        res.setStatus(lease.getStatus());
        res.setCreatedAt(lease.getCreatedAt());
        return res;
    }
}
