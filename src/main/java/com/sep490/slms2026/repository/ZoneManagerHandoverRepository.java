package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.ZoneManagerHandover;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ZoneManagerHandoverRepository extends JpaRepository<ZoneManagerHandover, Long> {

    List<ZoneManagerHandover> findByZoneIdOrderByChangedAtDesc(UUID zoneId);

    @Query("SELECT h FROM ZoneManagerHandover h WHERE h.fromManagerId = :userId OR h.toManagerId = :userId ORDER BY h.changedAt DESC")
    List<ZoneManagerHandover> findHistoryByUserId(@Param("userId") UUID userId);

}
