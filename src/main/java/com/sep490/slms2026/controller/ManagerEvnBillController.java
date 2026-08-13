package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.EvnBillResponse;
import com.sep490.slms2026.service.EvnBillService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manager/evn-bills")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class ManagerEvnBillController {

    private final EvnBillService evnBillService;

    @GetMapping
    public ResponseEntity<List<EvnBillResponse>> getEvnBills(
            @RequestParam(required = false) Long propertyId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(evnBillService.getEvnBills(propertyId, month, year, true));
    }
}
