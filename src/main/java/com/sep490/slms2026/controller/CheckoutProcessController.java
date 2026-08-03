package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.CheckoutInspectionRequest;
import com.sep490.slms2026.dto.request.CheckoutRefundRequest;
import com.sep490.slms2026.dto.response.CheckoutInspectionResponse;
import com.sep490.slms2026.dto.response.CheckoutSettlementResponse;
import com.sep490.slms2026.service.CheckoutProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/checkout-requests/{id}")
@RequiredArgsConstructor
public class CheckoutProcessController {

    private final CheckoutProcessService checkoutProcessService;

    @PostMapping("/inspection")
    public ResponseEntity<Void> saveInspection(@PathVariable Long id, @RequestBody CheckoutInspectionRequest request) {
        checkoutProcessService.saveInspection(id, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/inspection")
    public ResponseEntity<CheckoutInspectionResponse> getInspection(@PathVariable Long id) {
        return ResponseEntity.ok(checkoutProcessService.getInspection(id));
    }

    @GetMapping("/settlement")
    public ResponseEntity<CheckoutSettlementResponse> getSettlement(@PathVariable Long id) {
        return ResponseEntity.ok(checkoutProcessService.getSettlement(id));
    }

    @PostMapping("/settlement/submit")
    public ResponseEntity<Void> submitSettlement(@PathVariable Long id) {
        checkoutProcessService.submitSettlement(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refund")
    public ResponseEntity<Void> refund(@PathVariable Long id, @RequestBody CheckoutRefundRequest request) {
        checkoutProcessService.refund(id, request);
        return ResponseEntity.ok().build();
    }
}
