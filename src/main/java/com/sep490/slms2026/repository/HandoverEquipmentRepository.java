package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.HandoverEquipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface HandoverEquipmentRepository extends JpaRepository<HandoverEquipment, Long> {

    List<HandoverEquipment> findByPropertyIdOrderByIdAsc(Long propertyId);

    long countByPropertyId(Long propertyId);

    @Modifying
    @Query("DELETE FROM HandoverEquipment h WHERE h.property.id = :propertyId")
    void deleteByPropertyId(Long propertyId);
}
