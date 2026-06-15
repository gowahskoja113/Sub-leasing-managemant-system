package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.RenovationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RenovationSessionRepository extends JpaRepository<RenovationSession, Long> {

    List<RenovationSession> findByPropertyIdOrderBySessionNumberAsc(Long propertyId);

    Optional<RenovationSession> findTopByPropertyIdAndEndDateIsNullOrderBySessionNumberDesc(Long propertyId);

    Optional<RenovationSession> findByPropertyIdAndSessionNumber(Long propertyId, Integer sessionNumber);

    @Query("SELECT COALESCE(MAX(s.sessionNumber), 0) FROM RenovationSession s WHERE s.property.id = :propertyId")
    int findMaxSessionNumberByPropertyId(@Param("propertyId") Long propertyId);
}
