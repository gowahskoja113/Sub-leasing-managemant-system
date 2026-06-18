package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.MonthlyReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonthlyReadingRepository extends JpaRepository<MonthlyReading, Long> {

    boolean existsByRoomId(Long roomId);

    boolean existsByPropertyId(Long propertyId);

    long countByPropertyId(Long propertyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM MonthlyReading m WHERE m.property.id = :propertyId")
    void deleteByPropertyId(@Param("propertyId") Long propertyId);
}
