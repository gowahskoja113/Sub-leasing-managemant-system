package com.sep490.slms2026.repository;

import com.sep490.slms2026.dto.ZoneSummaryProjection;
import com.sep490.slms2026.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    @Query("SELECT mz.id AS zoneId, mz.name AS zoneName, " +
            "SUM(CASE WHEN p.wholeHouse = true THEN 1 ELSE 0 END) AS wholeHouseCount, " +
            "SUM(CASE WHEN p.wholeHouse = false THEN 1 ELSE 0 END) AS roomBasedCount " +
            "FROM Zone mz JOIN mz.managers m " +
            "LEFT JOIN Zone child ON child.parent.id = mz.id " +
            "LEFT JOIN Property p ON (p.zone.id = mz.id OR p.zone.id = child.id) " +
            "WHERE m.id = :managerId " +
            "GROUP BY mz.id, mz.name")
    List<ZoneSummaryProjection> getZoneSummaryByManager(@Param("managerId") UUID managerId);

    List<Zone> findAllByLevel(Integer level);

    List<Zone> findAllByParentId(UUID parentId);

    boolean existsByNameAndParentId(String name, UUID parentId);

    boolean existsByNameAndParentIdAndIdNot(String name, UUID parentId, UUID id);

    @Query("SELECT COUNT(z) > 0 FROM Zone z WHERE z.name = :name AND z.parent IS NULL")
    boolean existsByNameAndParentIsNull(@Param("name") String name);

    @Query("SELECT COUNT(z) > 0 FROM Zone z WHERE z.name = :name AND z.parent IS NULL AND z.id <> :id")
    boolean existsByNameAndParentIsNullAndIdNot(@Param("name") String name, @Param("id") UUID id);

    @Query("SELECT z FROM Zone z WHERE LOWER(z.name) = LOWER(:name) AND z.level = 1 AND z.parent IS NULL")
    Optional<Zone> findCityLevelZoneByNameIgnoreCase(@Param("name") String name);

    @Query("SELECT z FROM Zone z WHERE LOWER(z.name) = LOWER(:name) AND z.level = 2 AND z.parent.id = :parentId")
    Optional<Zone> findDistrictLevelZoneByNameIgnoreCaseAndParentId(
            @Param("name") String name, @Param("parentId") UUID parentId);
}