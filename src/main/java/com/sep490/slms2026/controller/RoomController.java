package com.sep490.slms2026.controller;

import com.sep490.slms2026.dto.request.AddRoomRequest;
import com.sep490.slms2026.dto.request.UpdateRoomStatusRequest;
import com.sep490.slms2026.dto.response.RoomResponse;
import com.sep490.slms2026.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<List<RoomResponse>> getRooms(
            @PathVariable Long propertyId) {
        return ResponseEntity.ok(roomService.getRoomsByProperty(propertyId));
    }

    /**
     * GET /api/v1/properties/{propertyId}/rooms/{roomId}
     * Lấy chi tiết 1 phòng, validate thuộc đúng tòa
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> getRoom(
            @PathVariable Long propertyId,
            @PathVariable Long roomId) {
        return ResponseEntity.ok(roomService.getRoomById(propertyId, roomId));
    }

    /**
     * PATCH /api/v1/properties/{propertyId}/rooms/{roomId}/status
     * Cập nhật trạng thái phòng: AVAILABLE (trống) hoặc MAINTENANCE (bảo trì).
     * Cho phép khi tòa nhà DRAFT hoặc ACTIVE. Không cho phép khi phòng đang RENTED.
     */
    @PatchMapping("/{roomId}/status")
    public ResponseEntity<RoomResponse> updateRoomStatus(
            @PathVariable Long propertyId,
            @PathVariable Long roomId,
            @Valid @RequestBody UpdateRoomStatusRequest request) {
        return ResponseEntity.ok(roomService.updateRoomStatus(propertyId, roomId, request));
    }
}
