package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.EvnBillResponse;
import com.sep490.slms2026.enums.UtilityType;
import com.sep490.slms2026.service.EvnBillService;
import com.sep490.slms2026.util.UtilityTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manager/utility-bills")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class ManagerUtilityBillController {

    private final EvnBillService evnBillService;

    @GetMapping
    public ResponseEntity<List<EvnBillResponse>> list(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String type) {
        UtilityType utilityType = type == null || type.isBlank() ? null : UtilityTypeMapper.fromApi(type);
        return ResponseEntity.ok(evnBillService.getUtilityBills(propertyId, month, year, utilityType, true));
    }
}
