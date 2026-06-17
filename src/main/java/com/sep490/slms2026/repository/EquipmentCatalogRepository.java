package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.EquipmentCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EquipmentCatalogRepository extends JpaRepository<EquipmentCatalog, Long> {

    List<EquipmentCatalog> findByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    Optional<EquipmentCatalog> findFirstByNameIgnoreCaseAndActiveTrue(String name);
}
