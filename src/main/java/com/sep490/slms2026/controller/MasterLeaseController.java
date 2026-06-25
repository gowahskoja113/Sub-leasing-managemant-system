package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.MasterLeaseRequest;
import com.sep490.slms2026.dto.response.MasterLeaseResponse;
import com.sep490.slms2026.enums.MasterLeaseStatus;
import com.sep490.slms2026.service.MasterLeaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/host/master-leases")
@RequiredArgsConstructor
public class MasterLeaseController {

    private final MasterLeaseService service;

    @GetMapping
    public Page<MasterLeaseResponse> getMasterLeases(
            @RequestParam(required = false) MasterLeaseStatus status,
            @RequestParam(required = false) Long propertyId,
            Pageable pageable) {
        return service.getMasterLeases(status, propertyId, pageable);
    }

    @GetMapping("/{id}")
    public MasterLeaseResponse getMasterLease(@PathVariable Long id) {
        return service.getMasterLease(id);
    }

    @PostMapping
    public MasterLeaseResponse createMasterLease(@RequestBody MasterLeaseRequest request) {
        return service.createMasterLease(request);
    }

    @PutMapping("/{id}")
    public MasterLeaseResponse updateMasterLease(@PathVariable Long id, @RequestBody MasterLeaseRequest request) {
        return service.updateMasterLease(id, request);
    }

    @PostMapping("/{id}/terminate")
    public void terminateMasterLease(@PathVariable Long id) {
        service.terminateMasterLease(id);
    }
}
