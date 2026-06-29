package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.AddRoomRequest;
import com.sep490.slms2026.dto.request.UpdateRoomRequest;
import com.sep490.slms2026.dto.request.UpdateRoomStatusRequest;
import com.sep490.slms2026.dto.response.RoomResponse;
import com.sep490.slms2026.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/properties/{propertyId}/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    /**
     * POST /api/v1/properties/{propertyId}/rooms
     * Thêm phòng mới vào tòa nhà (chỉ khi wholeHouse = false)
     */
    @PostMapping
    public ResponseEntity<RoomResponse> addRoom(
            @PathVariable Long propertyId,
            @Valid @RequestBody AddRoomRequest request) {
        RoomResponse response = roomService.addRoom(propertyId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/v1/properties/{propertyId}/rooms
     * Lấy danh sách tất cả phòng của 1 tòa nhà
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER', 'USER')")
    public ResponseEntity<List<RoomResponse>> getRooms(
            @PathVariable Long propertyId) {
        return ResponseEntity.ok(roomService.getRoomsByProperty(propertyId));
    }

    /**
     * GET /api/v1/properties/{propertyId}/rooms/{roomId}
     * Lấy chi tiết 1 phòng, validate thuộc đúng tòa
     */
    @GetMapping("/{roomId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER', 'USER')")
    public ResponseEntity<RoomResponse> getRoom(
            @PathVariable Long propertyId,
            @PathVariable Long roomId) {
        return ResponseEntity.ok(roomService.getRoomById(propertyId, roomId));
    }

    /**
     * PATCH /api/v1/properties/{propertyId}/rooms/{roomId}/status
     * Cập nhật trạng thái phòng: AVAILABLE, MAINTENANCE hoặc DISABLED.
     * Cho phép khi tòa nhà DRAFT hoặc ACTIVE. Không cho phép khi phòng đang RENTED.
     */
    @PatchMapping("/{roomId}/status")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<RoomResponse> updateRoomStatus(
            @PathVariable Long propertyId,
            @PathVariable Long roomId,
            @Valid @RequestBody UpdateRoomStatusRequest request) {
        return ResponseEntity.ok(roomService.updateRoomStatus(propertyId, roomId, request));
    }

    /**
     * PUT /api/v1/properties/{propertyId}/rooms/{roomId}
     * Cập nhật thông tin phòng (số phòng, tầng, diện tích...).
     * Cho phép khi tòa nhà DRAFT hoặc UNDER_RENOVATION. Không cho phép khi phòng đang RENTED.
     */
    @PutMapping("/{roomId}")
    public ResponseEntity<RoomResponse> updateRoom(
            @PathVariable Long propertyId,
            @PathVariable Long roomId,
            @Valid @RequestBody UpdateRoomRequest request) {
        return ResponseEntity.ok(roomService.updateRoom(propertyId, roomId, request));
    }

    /**
     * DELETE /api/v1/properties/{propertyId}/rooms/{roomId}
     * Ẩn phòng (soft delete) khi đập bỏ/gộp phòng sau cải tạo.
     * Thiết bị trong phòng tự động chuyển về kho (roomId = null).
     * Cho phép khi tòa nhà DRAFT hoặc UNDER_RENOVATION. Không cho phép khi phòng đang RENTED.
     */
    @DeleteMapping("/{roomId}")
    public ResponseEntity<Void> deleteRoom(
            @PathVariable Long propertyId,
            @PathVariable Long roomId) {
        roomService.deleteRoom(propertyId, roomId);
        return ResponseEntity.noContent().build();
    }
}
