package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.DashboardResponse;
import com.sep490.slms2026.repository.InboundContractRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final InboundContractRepository inboundContractRepository;

    @Override
    public DashboardResponse getDashboard() {

        return DashboardResponse.builder()
                .totalProperties(propertyRepository.count())
                .totalRooms(roomRepository.countByDeletedIsFalse())
                .wholeHouseCount(propertyRepository.countWholeHouse())
                .roomBasedPropertyCount(propertyRepository.countRoomBasedProperty())
                .totalArea(propertyRepository.getTotalArea())
                .totalInboundCost(inboundContractRepository.getTotalInboundCost())
                .build();
    }
}