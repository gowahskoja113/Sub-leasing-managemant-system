package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.VisionLabelsRequest;
import com.sep490.slms2026.dto.response.VisionLabelsResponse;
import com.sep490.slms2026.service.VisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vision")
@RequiredArgsConstructor
public class VisionController {

    private final VisionService visionService;

    /**
     * POST /api/v1/vision/labels
     * Nhận URL ảnh Cloudinary → danh sách nhãn vật thể (tiếng Anh + score).
     * TENANT (báo hỏng) và MANAGER (biên bản trả phòng) đều dùng được.
     */
    @PostMapping("/labels")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<VisionLabelsResponse> detectLabels(@Valid @RequestBody VisionLabelsRequest request) {
        return ResponseEntity.ok(visionService.detectLabels(request.getImageUrl()));
    }
}
