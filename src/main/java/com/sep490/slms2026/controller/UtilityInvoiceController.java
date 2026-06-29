package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.CreateUtilityInvoiceRequest;
import com.sep490.slms2026.dto.response.UtilityInvoiceResponse;
import com.sep490.slms2026.service.UtilityInvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/properties/{propertyId}")
@RequiredArgsConstructor
public class UtilityInvoiceController {

    private final UtilityInvoiceService utilityInvoiceService;

    @PostMapping("/rooms/{roomId}/utility-invoices")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<UtilityInvoiceResponse> createRoomInvoice(
            @PathVariable Long propertyId,
            @PathVariable Long roomId,
            @Valid @RequestBody CreateUtilityInvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(utilityInvoiceService.createRoomInvoice(propertyId, roomId, request));
    }

    @PostMapping("/utility-invoices")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<UtilityInvoiceResponse> createPropertyInvoice(
            @PathVariable Long propertyId,
            @Valid @RequestBody CreateUtilityInvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(utilityInvoiceService.createPropertyInvoice(propertyId, request));
    }
}
