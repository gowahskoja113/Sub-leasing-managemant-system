package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.*;
import com.sep490.slms2026.dto.response.MaintenanceRequestResponse;
import com.sep490.slms2026.entity.MaintenanceRequest;
import com.sep490.slms2026.enums.MaintenanceStatus;
import com.sep490.slms2026.repository.MaintenanceRequestRepository;
import com.sep490.slms2026.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MaintenanceServiceImpl implements MaintenanceService {

    private final MaintenanceRequestRepository repository;

    @Override
    public MaintenanceRequestResponse acknowledge(Long id, MaintenanceAcknowledgeRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        req.setStatus(MaintenanceStatus.ACKNOWLEDGED);
        req.setAcknowledgedAt(LocalDateTime.now());
        req.setTechnicianId(request.getTechnicianId());
        // set note
        repository.save(req);
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse schedule(Long id, MaintenanceScheduleRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        req.setStatus(MaintenanceStatus.SCHEDULED);
        req.setScheduledSlots(String.join(",", request.getScheduledSlots()));
        repository.save(req);
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse confirmSchedule(Long id, MaintenanceConfirmScheduleRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        req.setConfirmedSlot(request.getSlot());
        repository.save(req);
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse updateStatus(Long id, MaintenanceStatusRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        req.setStatus(request.getStatus());
        req.setOnHoldReason(request.getOnHoldReason());
        repository.save(req);
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse resolve(Long id, MaintenanceResolveRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        req.setRepairCost(request.getRepairCost());
        req.setCostPaidBy(request.getCostPaidBy());
        req.setCause(request.getCause());
        req.setResolutionNote(request.getResolutionNote());
        
        // thresholds
        if (request.getRepairCost() != null && request.getRepairCost().doubleValue() > 2000000) {
            req.setStatus(MaintenanceStatus.PENDING_APPROVAL);
        } else {
            req.setStatus(MaintenanceStatus.DONE);
            req.setDoneAt(LocalDateTime.now());
        }
        
        repository.save(req);
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse approve(Long id, MaintenanceApproveRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        if (request.isApprove()) {
            req.setStatus(MaintenanceStatus.DONE);
            req.setDoneAt(LocalDateTime.now());
        } else {
            req.setStatus(MaintenanceStatus.IN_PROGRESS);
        }
        repository.save(req);
        return convertToResponse(req);
    }

    @Override
    public MaintenanceRequestResponse confirm(Long id, MaintenanceConfirmRequest request) {
        MaintenanceRequest req = repository.findById(id).orElseThrow();
        if (request.isAccept()) {
            req.setStatus(MaintenanceStatus.CONFIRMED);
            req.setTenantConfirmedAt(LocalDateTime.now());
        } else {
            req.setStatus(MaintenanceStatus.REOPENED);
            req.setReopenCount(req.getReopenCount() + 1);
        }
        repository.save(req);
        return convertToResponse(req);
    }

    @Override
    public Page<MaintenanceRequestResponse> getRequests(Pageable pageable) {
        return repository.findByDeletedFalse(pageable).map(this::convertToResponse);
    }

    private MaintenanceRequestResponse convertToResponse(MaintenanceRequest req) {
        MaintenanceRequestResponse res = new MaintenanceRequestResponse();
        res.setId(req.getId());
        res.setTitle(req.getTitle());
        res.setDescription(req.getDescription());
        res.setStatus(req.getStatus());
        res.setCostPaidBy(req.getCostPaidBy());
        res.setCause(req.getCause());
        res.setRepairCost(req.getRepairCost());
        return res;
    }
}
