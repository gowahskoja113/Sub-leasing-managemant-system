package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.RoomPriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomPriceHistoryRepository extends JpaRepository<RoomPriceHistory, Long> {

    List<RoomPriceHistory> findByPropertyIdOrderByChangedAtDescIdDesc(Long propertyId);

    List<RoomPriceHistory> findByPropertyIdAndRoomIdOrderByChangedAtDescIdDesc(Long propertyId, Long roomId);

    List<RoomPriceHistory> findByPropertyIdAndRoomIdIsNullOrderByChangedAtDescIdDesc(Long propertyId);
}
