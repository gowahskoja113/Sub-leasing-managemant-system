package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.enums.PropertyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    interface PropertyNameStatusView {
        String getPropertyName();
        PropertyStatus getStatus();
    }

    @Query("SELECT p.propertyName as propertyName, p.status as status FROM Property p WHERE p.id = :id")
    Optional<PropertyNameStatusView> findNameAndStatusById(@Param("id") Long id);

    @Query("SELECT DISTINCT p FROM Property p " +
            "JOIN p.zone z " +
            "LEFT JOIN z.parent parent " +
            "LEFT JOIN parent.parent grandparent " +
            "WHERE EXISTS (SELECT 1 FROM OperationManagement om JOIN om.zones mz " +
            "              WHERE om.id = :managerId AND " +
            "              (mz.id = z.id OR mz.id = parent.id OR mz.id = grandparent.id))")
    Page<Property> findAllByManagerZones(@Param("managerId") UUID managerId, Pageable pageable);

    boolean existsByAddressIgnoreCase(String address);

    boolean existsByAddressIgnoreCaseAndIdNot(String address, Long id);

    Optional<Property> findFirstByAddressIgnoreCase(String address);

    List<Property> findByPropertyNameIgnoreCase(String propertyName);

    @Query("""
       SELECT z.name, COUNT(p)
       FROM Property p
       JOIN p.zone z
       GROUP BY z.name
       ORDER BY COUNT(p) DESC
       """)
    List<Object[]> getMostPropertiesByZone();

    @Query("""
       SELECT SUM(p.areaSize)
       FROM Property p
       """)
    Double getTotalArea();

    @Query("""
       SELECT COUNT(p)
       FROM Property p
       WHERE p.wholeHouse = true
       """)
    Long countWholeHouse();

    @Query("""
       SELECT COUNT(p)
       FROM Property p
       WHERE p.wholeHouse = false
       """)
    Long countRoomBasedProperty();

    @Query("SELECT p.id FROM Property p WHERE p.operationManagerId = :managerId")
    List<Long> findIdsByOperationManagerId(@Param("managerId") UUID managerId);

    List<Property> findByOperationManagerId(UUID operationManagerId);

    List<Property> findByZone_IdAndStatusIn(UUID zoneId, List<PropertyStatus> statuses);

    List<Property> findByStatus(PropertyStatus status);

    Page<Property> findByStatusAndOperationManagerIdIsNotNull(PropertyStatus status, Pageable pageable);

    Page<Property> findByStatusInAndOperationManagerIdIsNotNull(List<PropertyStatus> statuses, Pageable pageable);

    /**
     * Nhà còn nhận khách mới:
     * - nguyên căn: chưa có HĐ DRAFT/PENDING/ACTIVE (room IS NULL)
     * - chia phòng: còn ≥1 phòng AVAILABLE không bị HĐ DRAFT/PENDING giữ
     */
    @Query("""
            SELECT p FROM Property p
            WHERE (
                p.wholeHouse = true
                AND NOT EXISTS (
                    SELECT 1 FROM TenantContract c
                    WHERE c.property = p
                      AND c.room IS NULL
                      AND c.status IN :wholeHouseHolding
                )
            )
            OR (
                (p.wholeHouse IS NULL OR p.wholeHouse = false)
                AND EXISTS (
                    SELECT 1 FROM Room r
                    WHERE r.property = p
                      AND r.deleted = false
                      AND r.status = com.sep490.slms2026.enums.RoomStatus.AVAILABLE
                      AND NOT EXISTS (
                          SELECT 1 FROM TenantContract c
                          WHERE c.room = r
                            AND c.status IN :roomHolding
                      )
                )
            )
            """)
    Page<Property> findWithAvailableCapacity(
            @Param("wholeHouseHolding") List<com.sep490.slms2026.enums.ContractStatus> wholeHouseHolding,
            @Param("roomHolding") List<com.sep490.slms2026.enums.ContractStatus> roomHolding,
            Pageable pageable);
}
