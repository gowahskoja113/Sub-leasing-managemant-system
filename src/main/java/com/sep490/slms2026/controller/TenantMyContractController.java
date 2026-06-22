package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.TenantContractResponse;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.TenantContractDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Khách thuê xem danh sách hợp đồng của mình (kèm URL file, trạng thái hiệu lực). */
@RestController
@RequestMapping("/api/v1/me/tenant-contracts")
@RequiredArgsConstructor
public class TenantMyContractController {

    private final TenantContractDocumentService tenantContractDocumentService;

    @GetMapping
    @PreAuthorize("hasRole('TENANT')")
    public ResponseEntity<List<TenantContractResponse>> myContracts() {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        return ResponseEntity.ok(tenantContractDocumentService.listContractsForTenant(user.getId()));
    }
}
