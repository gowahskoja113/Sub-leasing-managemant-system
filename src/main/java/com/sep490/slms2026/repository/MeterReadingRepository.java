package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.MeterReading;
import com.sep490.slms2026.enums.UtilityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MeterReadingRepository extends JpaRepository<MeterReading, Long> {

    Optional<MeterReading> findTopByPropertyIdAndRoomIdAndUtilityTypeOrderByRecordedAtDesc(
            Long propertyId, Long roomId, UtilityType utilityType);

    Optional<MeterReading> findTopByPropertyIdAndRoomIsNullAndUtilityTypeOrderByRecordedAtDesc(
            Long propertyId, UtilityType utilityType);
}
