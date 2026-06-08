package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.Renovation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface RenovationRepository extends JpaRepository<Renovation, Long> {

    List<Renovation> findByPropertyId(Long propertyId);

    boolean existsByPropertyIdAndCompletedFalse(Long propertyId);

    boolean existsByPropertyIdAndRoomIsNotNull(Long propertyId);

    @Query("SELECT COALESCE(SUM(r.cost), 0) FROM Renovation r " +
            "WHERE r.property.id = :propertyId AND r.room IS NULL AND r.cost IS NOT NULL")
    BigDecimal sumCostByPropertyIdAndRoomIsNull(@Param("propertyId") Long propertyId);

    @Query("SELECT COALESCE(SUM(r.cost), 0) FROM Renovation r " +
            "WHERE r.room.id = :roomId AND r.cost IS NOT NULL")
    BigDecimal sumCostByRoomId(@Param("roomId") Long roomId);
}
