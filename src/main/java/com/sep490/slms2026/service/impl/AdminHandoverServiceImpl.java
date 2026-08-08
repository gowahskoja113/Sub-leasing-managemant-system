package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.AdminHandoverStatusDto;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.AdminHandoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminHandoverServiceImpl implements AdminHandoverService {

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final TenantContractRepository tenantContractRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminHandoverStatusDto getHandoverStatus(Long propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy BĐS ID=" + propertyId));
        return buildStatus(property, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminHandoverStatusDto> listHandoverStatus() {
        return propertyRepository.findAll().stream()
                .map(p -> buildStatus(p, false))
                .toList();
    }

    private AdminHandoverStatusDto buildStatus(Property property, boolean includeRooms) {
        Long propertyId = property.getId();
        String managerName = null;
        UUID omId = property.getOperationManagerId();
        if (omId != null) {
            managerName = userRepository.findById(omId).map(User::getFullName).orElse(null);
        }

        List<Room> rooms = roomRepository.findByPropertyId(propertyId);
        List<TenantContract> contracts = tenantContractRepository.findByPropertyId(propertyId);

        List<AdminHandoverStatusDto.RoomHandover> roomHandovers = new ArrayList<>();
        int handedOver = 0;

        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            TenantContract active = contracts.stream()
                    .filter(c -> c.getStatus() == ContractStatus.ACTIVE && c.getRoom() == null)
                    .findFirst()
                    .orElse(null);
            if (active != null) {
                handedOver = 1;
            }
            if (includeRooms) {
                roomHandovers.add(toRoomHandover("Toàn nhà", active));
            }
        } else {
            for (Room room : rooms) {
                TenantContract active = contracts.stream()
                        .filter(c -> c.getStatus() == ContractStatus.ACTIVE
                                && c.getRoom() != null
                                && c.getRoom().getId().equals(room.getId()))
                        .findFirst()
                        .orElse(null);
                if (active != null) {
                    handedOver++;
                }
                if (includeRooms) {
                    roomHandovers.add(toRoomHandover(room.getRoomNumber(), active));
                }
            }
        }

        int totalRooms = Boolean.TRUE.equals(property.getWholeHouse())
                ? 1
                : (property.getTotalRooms() != null ? property.getTotalRooms() : rooms.size());

        return AdminHandoverStatusDto.builder()
                .propertyId(property.getId())
                .propertyName(property.getPropertyName())
                .propertyStatus(property.getStatus() != null ? property.getStatus().name() : null)
                .operationManagerName(managerName)
                .managerAcceptedAt(property.getManagerAcceptedAt())
                .totalRooms(totalRooms)
                .roomsHandedOver(handedOver)
                .rooms(includeRooms ? roomHandovers : null)
                .build();
    }

    private AdminHandoverStatusDto.RoomHandover toRoomHandover(String roomNumber, TenantContract contract) {
        if (contract == null) {
            return AdminHandoverStatusDto.RoomHandover.builder()
                    .roomNumber(roomNumber)
                    .contractStatus(null)
                    .conditionPhotoCount(0)
                    .hasMeterReadings(false)
                    .build();
        }
        String tenantName = contract.getDraftTenantName();
        if ((tenantName == null || tenantName.isBlank())
                && contract.getTenant() != null
                && contract.getTenant().getUser() != null) {
            tenantName = contract.getTenant().getUser().getFullName();
        }
        int photoCount = contract.getRoomConditionPhotos() != null
                ? contract.getRoomConditionPhotos().size() : 0;
        boolean hasMeters = contract.getInitialElectricReading() != null
                || contract.getInitialWaterReading() != null;

        return AdminHandoverStatusDto.RoomHandover.builder()
                .roomNumber(roomNumber)
                .tenantName(tenantName)
                .contractStatus(contract.getStatus() != null ? contract.getStatus().name() : null)
                .moveInDate(contract.getMoveInDate())
                .activatedAt(contract.getActivatedAt())
                .conditionPhotoCount(photoCount)
                .hasMeterReadings(hasMeters)
                .build();
    }
}
