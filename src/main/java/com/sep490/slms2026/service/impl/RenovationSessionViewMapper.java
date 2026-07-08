package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.RenovationSessionLineResponse;
import com.sep490.slms2026.dto.response.RenovationSessionResponse;
import com.sep490.slms2026.dto.response.SessionEquipmentResponse;
import com.sep490.slms2026.entity.Equipment;
import com.sep490.slms2026.entity.RenovationLine;
import com.sep490.slms2026.entity.RenovationSession;
import com.sep490.slms2026.enums.EquipmentOperationalStatus;
import com.sep490.slms2026.enums.RenovationSessionStatus;
import com.sep490.slms2026.repository.EquipmentRepository;
import com.sep490.slms2026.repository.RenovationLineRepository;
import com.sep490.slms2026.repository.RenovationSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RenovationSessionViewMapper {

    private final RenovationSessionRepository renovationSessionRepository;
    private final RenovationLineRepository renovationLineRepository;
    private final EquipmentRepository equipmentRepository;

    public Optional<RenovationSessionResponse> findActiveSession(Long propertyId) {
        return renovationSessionRepository
                .findTopByPropertyIdAndStatusOrderBySessionNumberDesc(propertyId, RenovationSessionStatus.ACTIVE)
                .map(this::toResponse);
    }

    public List<RenovationSessionResponse> listHistoryNewestFirst(Long propertyId) {
        return renovationSessionRepository.findByPropertyIdOrderBySessionNumberAsc(propertyId).stream()
                .sorted(Comparator.comparing(RenovationSession::getSessionNumber).reversed())
                .map(this::toResponse)
                .toList();
    }

    public RenovationSessionResponse toResponse(RenovationSession session) {
        List<RenovationLine> lines = renovationLineRepository.findBySessionIdOrderByIdAsc(session.getId());
        BigDecimal totalCost = lines.stream()
                .map(RenovationLine::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        RenovationSessionStatus status = session.getStatus() != null
                ? session.getStatus() : RenovationSessionStatus.IN_PROGRESS;

        List<Equipment> sessionEquipments = equipmentRepository
                .findByRenovationSessionIdOrderByIdAsc(session.getId());

        return RenovationSessionResponse.builder()
                .id(session.getId())
                .sessionNumber(session.getSessionNumber())
                .versionLabel("v" + session.getSessionNumber())
                .status(status.name())
                .currentEffective(status == RenovationSessionStatus.ACTIVE)
                .startDate(session.getStartDate())
                .endDate(session.getEndDate())
                .disabledAt(session.getDisabledAt())
                .totalCost(totalCost)
                .lines(lines.stream().map(this::toLineResponse).toList())
                .equipments(sessionEquipments.stream().map(this::toEquipmentResponse).toList())
                .build();
    }

    private RenovationSessionLineResponse toLineResponse(RenovationLine line) {
        return RenovationSessionLineResponse.builder()
                .id(line.getId())
                .categoryName(line.getCategory().getName())
                .cost(line.getCost())
                .note(line.getNote())
                .build();
    }

    private SessionEquipmentResponse toEquipmentResponse(Equipment equipment) {
        EquipmentOperationalStatus opStatus = equipment.getOperationalStatus() != null
                ? equipment.getOperationalStatus() : EquipmentOperationalStatus.ACTIVE;
        return SessionEquipmentResponse.builder()
                .id(equipment.getId())
                .catalogId(equipment.getCatalog().getId())
                .catalogName(equipment.getCatalog().getName())
                .roomId(equipment.getRoom() != null ? equipment.getRoom().getId() : null)
                .roomNumber(equipment.getRoom() != null ? equipment.getRoom().getRoomNumber() : null)
                .houseArea(equipment.getHouseArea())
                .source(equipment.getSource())
                .status(equipment.getStatus())
                .operationalStatus(opStatus.name())
                .currentEffective(opStatus == EquipmentOperationalStatus.ACTIVE)
                .price(equipment.getPrice())
                .note(equipment.getNote())
                .warrantyMonths(equipment.getWarrantyMonths())
                .warrantyStartDate(equipment.getWarrantyStartDate())
                .warrantyEndDate(equipment.getWarrantyEndDate())
                .disabledAt(equipment.getDisabledAt())
                .build();
    }
}
