package com.sep490.slms2026.repository;

import com.sep490.slms2026.dto.ZoneSummaryProjection;
import com.sep490.slms2026.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    @Query("SELECT z.id AS zoneId, z.name AS zoneName, " +
            "SUM(CASE WHEN p.isWholeHouse = true THEN 1 ELSE 0 END) AS wholeHouseCount, " +
            "SUM(CASE WHEN p.isWholeHouse = false THEN 1 ELSE 0 END) AS roomBasedCount " +
            "FROM Zone z " +
            "JOIN z.managers m " +
            "LEFT JOIN z.properties p " +
            "WHERE m.id = :managerId " +
            "GROUP BY z.id, z.name")
    List<ZoneSummaryProjection> getZoneSummaryByManager(@Param("managerId") UUID managerId);
}