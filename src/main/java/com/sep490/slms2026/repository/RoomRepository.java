package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.enums.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByPropertyIdAndDeletedIsFalse(Long propertyId);

    default List<Room> findByPropertyId(Long propertyId) {
        return findByPropertyIdAndDeletedIsFalse(propertyId);
    }

    List<Room> findByPropertyIdAndStatusAndDeletedIsFalse(Long propertyId, RoomStatus status);

    // ID các tòa có ít nhất 1 phòng ở trạng thái cho trước (vd AVAILABLE)
    @Query("SELECT DISTINCT r.property.id FROM Room r WHERE r.status = :status")
    List<Long> findPropertyIdsByRoomStatus(@Param("status") RoomStatus status);

    // Kiểm tra trùng số phòng trong cùng 1 tòa
    boolean existsByPropertyIdAndRoomNumber(Long propertyId, String roomNumber);
    default List<Room> findByPropertyIdAndStatus(Long propertyId, RoomStatus status) {
        return findByPropertyIdAndStatusAndDeletedIsFalse(propertyId, status);
    }

    boolean existsByPropertyIdAndRoomNumberAndDeletedIsFalse(Long propertyId, String roomNumber);

    Optional<Room> findByPropertyIdAndRoomNumberAndDeletedIsFalse(Long propertyId, String roomNumber);

    boolean existsByPropertyIdAndRoomNumberAndIdNotAndDeletedIsFalse(
            Long propertyId, String roomNumber, Long id);

    long countByPropertyIdAndDeletedIsFalse(Long propertyId);

    long countByPropertyIdAndStatusAndDeletedIsFalse(Long propertyId, RoomStatus status);

    default long countByPropertyIdAndStatus(Long propertyId, RoomStatus status) {
        return countByPropertyIdAndStatusAndDeletedIsFalse(propertyId, status);
    }

    long countByDeletedIsFalse();

    @Query("SELECT COALESCE(MAX(r.floor), 0) FROM Room r WHERE r.property.id = :propertyId AND r.deleted = false")
    int findMaxFloorByPropertyId(@Param("propertyId") Long propertyId);

    @Query("SELECT r FROM Room r JOIN FETCH r.property WHERE r.property.id = :propertyId AND r.deleted = false")
    List<Room> findByPropertyIdWithProperty(@Param("propertyId") Long propertyId);

    Optional<Room> findByIdAndPropertyIdAndDeletedIsFalse(Long id, Long propertyId);

    default Optional<Room> findByIdAndPropertyId(Long id, Long propertyId) {
        return findByIdAndPropertyIdAndDeletedIsFalse(id, propertyId);
    }

    @Query("""
            SELECT r FROM Room r JOIN FETCH r.property
            WHERE r.id = :roomId AND r.property.id = :propertyId AND r.deleted = false
            """)
    Optional<Room> findByIdAndPropertyIdWithProperty(
            @Param("roomId") Long roomId, @Param("propertyId") Long propertyId);

    @Query("""
       SELECT z.name, COUNT(r)
       FROM Room r
       JOIN r.property p
       JOIN p.zone z
       WHERE r.deleted = false
       GROUP BY z.name
       ORDER BY COUNT(r) DESC
       """)
    List<Object[]> getMostRoomsByZone();

    @Query("SELECT COUNT(r) FROM Room r WHERE r.property.id = :propertyId")
    long countAllByPropertyIdIncludingDeleted(@Param("propertyId") Long propertyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Room r WHERE r.property.id = :propertyId")
    void deleteAllByPropertyId(@Param("propertyId") Long propertyId);
}
