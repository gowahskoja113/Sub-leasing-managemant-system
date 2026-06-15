package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.enums.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByPropertyId(Long propertyId);

    List<Room> findByPropertyIdAndStatus(Long propertyId, RoomStatus status);

    // Kiểm tra trùng số phòng trong cùng 1 tòa
    boolean existsByPropertyIdAndRoomNumber(Long propertyId, String roomNumber);

    boolean existsByPropertyIdAndRoomNumberAndIdNot(Long propertyId, String roomNumber, Long id);

    // Đếm số phòng hiện có của tòa — dùng để validate không vượt totalRooms
    long countByPropertyId(Long propertyId);

    long countByPropertyIdAndStatus(Long propertyId, RoomStatus status);

    @Query("SELECT COALESCE(MAX(r.floor), 0) FROM Room r WHERE r.property.id = :propertyId")
    int findMaxFloorByPropertyId(@Param("propertyId") Long propertyId);

    // Fetch kèm property trong 1 query — tránh N+1 khi load danh sách phòng
    @Query("SELECT r FROM Room r JOIN FETCH r.property WHERE r.property.id = :propertyId")
    List<Room> findByPropertyIdWithProperty(@Param("propertyId") Long propertyId);

    Optional<Room> findByIdAndPropertyId(Long id, Long propertyId);

    @Query("SELECT r FROM Room r JOIN FETCH r.property WHERE r.id = :roomId AND r.property.id = :propertyId")
    Optional<Room> findByIdAndPropertyIdWithProperty(
            @Param("roomId") Long roomId, @Param("propertyId") Long propertyId);
    @Query("""
       SELECT z.name, COUNT(r)
       FROM Room r
       JOIN r.property p
       JOIN p.zone z
       GROUP BY z.name
       ORDER BY COUNT(r) DESC
       """)
    List<Object[]> getMostRoomsByZone();
}
