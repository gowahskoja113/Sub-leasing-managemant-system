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

    boolean existsByAddressIgnoreCaseAndIdNot(String address, UUID id);

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
}