package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    // Tìm kiếm danh sách phòng phân trang thuộc một Bất động sản
    Page<Room> findAllByPropertyId(UUID propertyId);

    // Kiểm tra trùng số phòng trong cùng một tòa nhà khi thêm mới
    boolean existsByRoomNumberAndPropertyId(String roomNumber, UUID propertyId);

    // Kiểm tra trùng số phòng trong cùng một tòa nhà khi cập nhật
    boolean existsByRoomNumberAndPropertyIdAndIdNot(String roomNumber, UUID propertyId, UUID roomId);
}