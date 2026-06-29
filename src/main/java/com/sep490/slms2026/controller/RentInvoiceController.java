package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.CreateRentInvoiceRequest;
import com.sep490.slms2026.dto.response.TenantInvoiceResponse;
import com.sep490.slms2026.service.TenantBillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/properties/{propertyId}")
@RequiredArgsConstructor
public class RentInvoiceController {

    private final TenantBillingService tenantBillingService;

    @PostMapping("/rooms/{roomId}/rent-invoices")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<TenantInvoiceResponse> createRoomRentInvoice(
            @PathVariable Long propertyId,
            @PathVariable Long roomId,
            @Valid @RequestBody CreateRentInvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tenantBillingService.createManagerRentInvoice(propertyId, roomId, request));
    }

    @PostMapping("/rent-invoices")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<TenantInvoiceResponse> createPropertyRentInvoice(
            @PathVariable Long propertyId,
            @Valid @RequestBody CreateRentInvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tenantBillingService.createManagerRentInvoice(propertyId, null, request));
    }
}
