package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    List<Equipment> findByPropertyId(Long propertyId);

    long countByPropertyId(Long propertyId);

    long countByManifestId(Long manifestId);

    long countByRoomId(Long roomId);

    @Query("SELECT COUNT(e) FROM Equipment e WHERE e.property.id = :propertyId AND e.catalog.id = :catalogId")
    long countByPropertyIdAndCatalogId(@Param("propertyId") Long propertyId, @Param("catalogId") Long catalogId);

    void deleteByPropertyId(Long propertyId);

    java.util.Optional<Equipment> findByIdAndPropertyId(Long id, Long propertyId);

    List<Equipment> findByRoomId(Long roomId);
}
