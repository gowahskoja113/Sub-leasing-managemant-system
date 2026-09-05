package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.AdminHandoverStatusDto;
import com.sep490.slms2026.service.AdminHandoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminHandoverController {

    private final AdminHandoverService adminHandoverService;

    /**
     * Tiến độ nhận nhà / giao phòng.
     * <ul>
     *   <li>Không query {@code propertyId} → list tóm tắt mọi toà (bảng admin).</li>
     *   <li>Có {@code propertyId} → chi tiết 1 toà kèm rooms[].</li>
     * </ul>
     */
    @GetMapping("/handover-status")
    public ResponseEntity<?> getHandoverStatus(@RequestParam(required = false) Long propertyId) {
        if (propertyId != null) {
            return ResponseEntity.ok(adminHandoverService.getHandoverStatus(propertyId));
        }
        List<AdminHandoverStatusDto> list = adminHandoverService.listHandoverStatus();
        return ResponseEntity.ok(list);
    }
}
