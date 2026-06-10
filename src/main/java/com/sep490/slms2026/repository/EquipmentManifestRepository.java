package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.EquipmentManifest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EquipmentManifestRepository extends JpaRepository<EquipmentManifest, Long> {

    List<EquipmentManifest> findByPropertyId(Long propertyId);

    Optional<EquipmentManifest> findByPropertyIdAndCatalogId(Long propertyId, Long catalogId);

    void deleteByPropertyId(Long propertyId);

    @Query("SELECT COALESCE(SUM(m.quantity), 0) FROM EquipmentManifest m WHERE m.property.id = :propertyId")
    int sumQuantityByPropertyId(@Param("propertyId") Long propertyId);
}
