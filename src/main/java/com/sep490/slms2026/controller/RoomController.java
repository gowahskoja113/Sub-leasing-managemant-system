package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.RoomRequest;
import com.sep490.slms2026.dto.response.RoomResponse;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    // ➕ Thêm phòng lẻ vào một tòa nhà chung cư có sẵn
    @PostMapping("/property/{propertyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<RoomResponse> addRoom(
            @PathVariable Long propertyId,
            @RequestBody RoomRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return new ResponseEntity<>(roomService.addRoomToProperty(propertyId, request, userDetails.getId()), HttpStatus.CREATED);
    }

    // 🔍 Lấy tất cả danh sách phòng trọ của 1 tòa nhà cụ thể (phân trang)
    @GetMapping("/property/{propertyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Page<RoomResponse>> getRoomsByProperty(
            @PathVariable Long propertyId,
            Pageable pageable) {
        return ResponseEntity.ok(roomService.getRoomsByProperty(propertyId, pageable));
    }

    // 🔍 Xem chi tiết thông tin một căn phòng
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<RoomResponse> getRoomDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(roomService.getRoomDetail(id));
    }

    // ✏️ Cập nhật thông tin phòng (Giá, cọc, diện tích, số phòng)
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<RoomResponse> updateRoom(
            @PathVariable UUID id,
            @RequestBody RoomRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(roomService.updateRoom(id, request, userDetails.getId()));
    }

    // ❌ Xóa phòng khỏi hệ thống
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteRoom(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        roomService.deleteRoom(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}