package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.CreateUtilityBillRequest;
import com.sep490.slms2026.dto.response.UtilityBillResponse;
import com.sep490.slms2026.enums.UtilityType;
import com.sep490.slms2026.service.UtilityBillService;
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

    private final UtilityBillService utilityBillService;

    @PostMapping
    public ResponseEntity<UtilityBillResponse> create(@RequestBody @Valid CreateUtilityBillRequest request) {
        return ResponseEntity.ok(utilityBillService.createUtilityBill(request));
    }

    @GetMapping
    public ResponseEntity<List<UtilityBillResponse>> list(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String type) {
        UtilityType utilityType = type == null || type.isBlank() ? null : UtilityTypeMapper.fromApi(type);
        return ResponseEntity.ok(utilityBillService.getUtilityBills(propertyId, month, year, utilityType, false));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        utilityBillService.revokeUtilityBill(id);
        return ResponseEntity.noContent().build();
    }
}

