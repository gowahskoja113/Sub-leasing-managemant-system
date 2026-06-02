package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.RoomRequest;
import com.sep490.slms2026.dto.response.RoomResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface RoomService {
    RoomResponse addRoomToProperty(UUID propertyId, RoomRequest request, UUID managerId);
    Page<RoomResponse> getRoomsByProperty(UUID propertyId, Pageable pageable);
    RoomResponse getRoomDetail(UUID roomId);
    RoomResponse updateRoom(UUID roomId, RoomRequest request, UUID managerId);
    void deleteRoom(UUID roomId, UUID managerId);
}