package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.CreateUtilityBillRequest;
import com.sep490.slms2026.dto.response.EvnBillResponse;
import com.sep490.slms2026.enums.UtilityType;
import com.sep490.slms2026.service.EvnBillService;
import com.sep490.slms2026.util.UtilityTypeMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/utility-bills")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUtilityBillController {

    private final EvnBillService evnBillService;

    @PostMapping
    public ResponseEntity<EvnBillResponse> create(@RequestBody @Valid CreateUtilityBillRequest request) {
        return ResponseEntity.ok(evnBillService.createUtilityBill(request));
    }

    @GetMapping
    public ResponseEntity<List<EvnBillResponse>> list(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String type) {
        UtilityType utilityType = type == null || type.isBlank() ? null : UtilityTypeMapper.fromApi(type);
        return ResponseEntity.ok(evnBillService.getUtilityBills(propertyId, month, year, utilityType, false));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        evnBillService.revokeUtilityBill(id);
        return ResponseEntity.noContent().build();
    }
}
