package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.CreateEvnBillRequest;
import com.sep490.slms2026.dto.response.EvnBillResponse;
import com.sep490.slms2026.service.EvnBillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/evn-bills")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminEvnBillController {

    private final EvnBillService evnBillService;

    @PostMapping
    public ResponseEntity<EvnBillResponse> createEvnBill(@RequestBody @Valid CreateEvnBillRequest request) {
        return ResponseEntity.ok(evnBillService.createEvnBill(request));
    }

    @GetMapping
    public ResponseEntity<List<EvnBillResponse>> getEvnBills(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(evnBillService.getEvnBills(propertyId, month, year, false));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeEvnBill(@PathVariable Long id) {
        evnBillService.revokeEvnBill(id);
        return ResponseEntity.noContent().build();
    }
}
