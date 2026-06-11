package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.AddRoomRequest;
import com.sep490.slms2026.dto.request.UpdateRoomStatusRequest;
import com.sep490.slms2026.dto.response.RoomResponse;

import java.util.List;

public interface RoomService {

    /**
     * Thêm phòng vào tòa nhà.
     * Chỉ cho phép khi property.wholeHouse = false.
     *
     * @param propertyId ID của tòa nhà
     * @param request    Thông tin phòng cần tạo
     * @return RoomResponse phòng vừa tạo
     */
    RoomResponse addRoom(Long propertyId, AddRoomRequest request);

    /**
     * Lấy danh sách tất cả phòng của 1 tòa nhà.
     */
    List<RoomResponse> getRoomsByProperty(Long propertyId);

    /**
     * Lấy chi tiết 1 phòng theo ID, validate thuộc đúng tòa.
     */
    RoomResponse getRoomById(Long propertyId, Long roomId);

    /**
     * Cập nhật trạng thái phòng (AVAILABLE / MAINTENANCE).
     * Không cho phép khi phòng đang RENTED.
     */
    RoomResponse updateRoomStatus(Long propertyId, Long roomId, UpdateRoomStatusRequest request);
}
