package com.sep490.slms2026.controller;

import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// @RestController
// @RequestMapping("/api/v1/host/contracts")
@RequiredArgsConstructor
public class HostContractController {

    private final TenantContractRepository contractRepository;
    private final RoomRepository roomRepository;

    @GetMapping
    public Page<TenantContract> getContracts(@RequestParam(required = false) ContractStatus status, Pageable pageable) {
        if (status != null) {
            return contractRepository.findByStatus(status, pageable);
        }
        return contractRepository.findAll(pageable);
    }

    @PutMapping("/{id}/approve")
    public TenantContract approveContract(@PathVariable Long id) {
        TenantContract contract = contractRepository.findById(id).orElseThrow();
        contract.setStatus(ContractStatus.ACTIVE);
        if (contract.getRoom() != null) {
            contract.getRoom().setStatus(com.sep490.slms2026.enums.RoomStatus.RENTED);
            roomRepository.save(contract.getRoom());
        }
        return contractRepository.save(contract);
    }

    @PutMapping("/{id}/reject")
    public TenantContract rejectContract(@PathVariable Long id, @RequestBody Map<String, String> body) {
        TenantContract contract = contractRepository.findById(id).orElseThrow();
        contract.setStatus(ContractStatus.TERMINATED);
        // Note can be stored somewhere if added to TenantContract
        return contractRepository.save(contract);
    }
}
