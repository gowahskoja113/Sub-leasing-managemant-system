package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.response.GuestPropertyResponse;
import com.sep490.slms2026.dto.response.RoomResponse;
import com.sep490.slms2026.service.PublicPropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/properties")
@RequiredArgsConstructor
public class PublicPropertyController {

    private final PublicPropertyService publicPropertyService;

    /** Danh sách nhà ACTIVE đã gán operation manager — không cần đăng nhập. */
    @GetMapping
    public ResponseEntity<Page<GuestPropertyResponse>> listPublicProperties(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(publicPropertyService.listPublicProperties(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GuestPropertyResponse> getPublicProperty(@PathVariable Long id) {
        return ResponseEntity.ok(publicPropertyService.getPublicProperty(id));
    }

    @GetMapping("/{id}/rooms")
    public ResponseEntity<List<RoomResponse>> getPublicRooms(@PathVariable Long id) {
        return ResponseEntity.ok(publicPropertyService.getPublicRooms(id));
    }
}
