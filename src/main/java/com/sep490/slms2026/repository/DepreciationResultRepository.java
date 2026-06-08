package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.DepreciationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DepreciationResultRepository extends JpaRepository<DepreciationResult, Long> {

    @Query("SELECT d FROM DepreciationResult d WHERE d.inboundContract.property.id = :propertyId AND d.room IS NULL")
    Optional<DepreciationResult> findWholeHouseByPropertyId(@Param("propertyId") Long propertyId);

    @Query("SELECT d FROM DepreciationResult d WHERE d.inboundContract.property.id = :propertyId AND d.room IS NOT NULL")
    List<DepreciationResult> findAllRoomLevelByPropertyId(@Param("propertyId") Long propertyId);

    Optional<DepreciationResult> findByRoomId(Long roomId);

    @Modifying
    @Query("DELETE FROM DepreciationResult d WHERE d.inboundContract.property.id = :propertyId")
    void deleteByPropertyId(@Param("propertyId") Long propertyId);

    boolean existsByInboundContractPropertyIdAndRoomIsNull(Long propertyId);

    boolean existsByInboundContractPropertyIdAndRoomIsNotNull(Long propertyId);
}
