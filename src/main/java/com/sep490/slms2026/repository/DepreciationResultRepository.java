package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.DepreciationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DepreciationResultRepository extends JpaRepository<DepreciationResult, Long> {

    @Query("SELECT d FROM DepreciationResult d WHERE d.inboundContract.property.id = :propertyId AND d.room IS NULL AND d.supersededAt IS NULL")
    Optional<DepreciationResult> findWholeHouseByPropertyId(@Param("propertyId") Long propertyId);

    @Query("SELECT d FROM DepreciationResult d WHERE d.inboundContract.property.id = :propertyId AND d.room IS NOT NULL AND d.supersededAt IS NULL")
    List<DepreciationResult> findAllRoomLevelByPropertyId(@Param("propertyId") Long propertyId);

    @Query("SELECT d FROM DepreciationResult d WHERE d.room.id = :roomId AND d.supersededAt IS NULL")
    Optional<DepreciationResult> findByRoomId(@Param("roomId") Long roomId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM DepreciationResult d WHERE d.inboundContract.property.id = :propertyId")
    void deleteByPropertyId(@Param("propertyId") Long propertyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM DepreciationResult d WHERE d.inboundContract.property.id = :propertyId AND d.pricingVersion = :version")
    void deleteByPropertyIdAndPricingVersion(@Param("propertyId") Long propertyId, @Param("version") Integer version);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DepreciationResult d SET d.supersededAt = CURRENT_TIMESTAMP WHERE d.inboundContract.property.id = :propertyId AND d.pricingVersion < :newVersion AND d.supersededAt IS NULL")
    void supersedeOldVersions(@Param("propertyId") Long propertyId, @Param("newVersion") Integer newVersion);

    @Query("SELECT MAX(d.pricingVersion) FROM DepreciationResult d WHERE d.inboundContract.property.id = :propertyId")
    Integer findMaxPricingVersionByPropertyId(@Param("propertyId") Long propertyId);

    @Query("SELECT COUNT(d) FROM DepreciationResult d WHERE d.inboundContract.property.id = :propertyId")
    long countByPropertyId(@Param("propertyId") Long propertyId);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM DepreciationResult d "
            + "WHERE d.inboundContract.property.id = :propertyId AND d.room IS NULL AND d.supersededAt IS NULL")
    boolean existsByInboundContractPropertyIdAndRoomIsNull(@Param("propertyId") Long propertyId);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM DepreciationResult d "
            + "WHERE d.inboundContract.property.id = :propertyId AND d.room IS NOT NULL AND d.supersededAt IS NULL")
    boolean existsByInboundContractPropertyIdAndRoomIsNotNull(@Param("propertyId") Long propertyId);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN true ELSE false END FROM DepreciationResult d "
            + "WHERE d.inboundContract.property.id = :propertyId AND d.supersededAt IS NULL")
    boolean existsByPropertyId(@Param("propertyId") Long propertyId);
}
