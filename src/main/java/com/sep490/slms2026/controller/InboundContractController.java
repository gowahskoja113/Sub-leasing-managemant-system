package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.CreateInboundContractRequest;
import com.sep490.slms2026.dto.response.InboundContractResponse;
import com.sep490.slms2026.service.InboundContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/inbound-contract")
@RequiredArgsConstructor
public class InboundContractController {

    private final InboundContractService inboundContractService;

    @PostMapping
    public ResponseEntity<InboundContractResponse> signContract(
            @PathVariable Long propertyId,
            @Valid @RequestBody CreateInboundContractRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inboundContractService.signContract(propertyId, request));
    }

    @GetMapping
    public ResponseEntity<InboundContractResponse> getContract(@PathVariable Long propertyId) {
        return ResponseEntity.ok(inboundContractService.getContractByProperty(propertyId));
    }
}
