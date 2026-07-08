package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.Equipment;
import com.sep490.slms2026.enums.EquipmentSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    List<Equipment> findByPropertyId(Long propertyId);

    List<Equipment> findByPropertyIdAndSourceOrderByIdAsc(Long propertyId, EquipmentSource source);

    @Query("""
            SELECT e FROM Equipment e
            WHERE e.property.id = :propertyId
              AND e.source = :source
              AND e.renovationSession.sessionNumber = :sessionNumber
            ORDER BY e.id ASC
            """)
    List<Equipment> findByPropertyIdAndSourceAndRenovationSession_SessionNumberOrderByIdAsc(
            @Param("propertyId") Long propertyId,
            @Param("source") EquipmentSource source,
            @Param("sessionNumber") Integer sessionNumber);

    long countByPropertyId(Long propertyId);

    long countByPropertyIdAndSource(Long propertyId, EquipmentSource source);

    @Query("SELECT COALESCE(SUM(e.price), 0) FROM Equipment e WHERE e.property.id = :propertyId "
            + "AND e.source = 'PURCHASED' AND e.operationalStatus = 'ACTIVE'")
    java.math.BigDecimal sumPurchasedEquipmentCostByPropertyId(@Param("propertyId") Long propertyId);

    @Query("SELECT COALESCE(SUM(e.price), 0) FROM Equipment e WHERE e.room.id = :roomId "
            + "AND e.source = 'PURCHASED' AND e.operationalStatus = 'ACTIVE'")
    java.math.BigDecimal sumPurchasedEquipmentCostByRoomId(@Param("roomId") Long roomId);

    @Query("SELECT COUNT(e) FROM Equipment e WHERE e.manifest.id = :manifestId "
            + "AND e.operationalStatus = 'ACTIVE'")
    long countActiveByManifestId(@Param("manifestId") Long manifestId);

    long countByManifestId(Long manifestId);

    long countByRoomId(Long roomId);

    List<Equipment> findByRoomId(Long roomId);

    @Query("SELECT COUNT(e) FROM Equipment e WHERE e.property.id = :propertyId AND e.catalog.id = :catalogId")
    long countByPropertyIdAndCatalogId(@Param("propertyId") Long propertyId, @Param("catalogId") Long catalogId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Equipment e WHERE e.property.id = :propertyId")
    void deleteByPropertyId(@Param("propertyId") Long propertyId);

    java.util.Optional<Equipment> findByIdAndPropertyId(Long id, Long propertyId);

    List<Equipment> findByRenovationSessionIdOrderByIdAsc(Long sessionId);

    @Query("""
            SELECT e FROM Equipment e
            WHERE e.property.id = :propertyId
              AND e.source = 'PURCHASED'
              AND e.operationalStatus = 'ACTIVE'
              AND e.catalog.id = :catalogId
              AND (
                (:roomId IS NOT NULL AND e.room.id = :roomId)
                OR (:houseArea IS NOT NULL AND e.houseArea = :houseArea AND e.room IS NULL)
              )
            ORDER BY e.id ASC
            """)
    List<Equipment> findActivePurchasedAtPlacement(
            @Param("propertyId") Long propertyId,
            @Param("catalogId") Long catalogId,
            @Param("roomId") Long roomId,
            @Param("houseArea") com.sep490.slms2026.enums.HouseArea houseArea);

    @Query("SELECT DISTINCT e.catalog.name FROM Equipment e WHERE e.property.id = :propertyId "
            + "AND e.operationalStatus = 'ACTIVE' ORDER BY e.catalog.name")
    List<String> findDistinctAmenityNamesByPropertyId(@Param("propertyId") Long propertyId);
    Optional<Equipment> findByQrCode(String qrCode);

    @Query("""
            SELECT e FROM Equipment e
            WHERE e.property.id = :propertyId
              AND (:roomId IS NULL OR e.room.id = :roomId OR e.room IS NULL)
              AND e.operationalStatus = 'ACTIVE'
            ORDER BY e.catalog.name, e.id
            """)
    List<Equipment> findActiveForTenantPlacement(
            @Param("propertyId") Long propertyId,
            @Param("roomId") Long roomId);

    List<Equipment> findByDisabledByContractId(Long contractId);
}
