package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.PropertyResponse;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Đếm sức chứa phòng thật theo nhà — batch để list properties không N+1.
 * {@code availableRooms} trừ phòng AVAILABLE đang bị HĐ DRAFT/PENDING giữ chỗ.
 */
@Component
@RequiredArgsConstructor
public class PropertyOccupancyAssembler {

    private static final List<ContractStatus> HOLDING_STATUSES =
            List.of(ContractStatus.DRAFT, ContractStatus.PENDING);

    private final RoomRepository roomRepository;
    private final TenantContractRepository tenantContractRepository;

    public record Occupancy(
            int roomCount,
            int availableRooms,
            int rentedRooms,
            int maintenanceRooms,
            int notOpenedRooms) {
        public static Occupancy empty() {
            return new Occupancy(0, 0, 0, 0, 0);
        }
    }

    public void apply(PropertyResponse response, Occupancy occupancy) {
        if (response == null || occupancy == null) {
            return;
        }
        response.setRoomCount(occupancy.roomCount());
        response.setAvailableRooms(occupancy.availableRooms());
        response.setRentedRooms(occupancy.rentedRooms());
        response.setMaintenanceRooms(occupancy.maintenanceRooms());
        response.setNotOpenedRooms(occupancy.notOpenedRooms());
    }

    public Occupancy loadOne(Long propertyId) {
        if (propertyId == null) {
            return Occupancy.empty();
        }
        return loadFor(List.of(propertyId)).getOrDefault(propertyId, Occupancy.empty());
    }

    public Map<Long, Occupancy> loadFor(Collection<Long> propertyIds) {
        Map<Long, Occupancy> result = new HashMap<>();
        if (propertyIds == null || propertyIds.isEmpty()) {
            return result;
        }
        Set<Long> ids = propertyIds instanceof Set<Long> s ? s : Set.copyOf(propertyIds);

        Map<Long, EnumMap<RoomStatus, Long>> byStatus = new HashMap<>();
        Map<Long, Long> totals = new HashMap<>();
        for (Object[] row : roomRepository.countGroupedByPropertyIdAndStatus(ids)) {
            Long propertyId = (Long) row[0];
            RoomStatus status = (RoomStatus) row[1];
            long count = (Long) row[2];
            byStatus.computeIfAbsent(propertyId, k -> new EnumMap<>(RoomStatus.class)).put(status, count);
            totals.merge(propertyId, count, Long::sum);
        }

        Map<Long, Long> available = new HashMap<>();
        for (Object[] row : roomRepository.countTrulyAvailableByPropertyIds(ids, HOLDING_STATUSES)) {
            available.put((Long) row[0], (Long) row[1]);
        }

        for (Long propertyId : ids) {
            EnumMap<RoomStatus, Long> statuses = byStatus.getOrDefault(propertyId, new EnumMap<>(RoomStatus.class));
            int roomCount = totals.getOrDefault(propertyId, 0L).intValue();
            int availableRooms = available.getOrDefault(propertyId, 0L).intValue();
            int rentedRooms = statuses.getOrDefault(RoomStatus.RENTED, 0L).intValue();
            int maintenanceRooms = statuses.getOrDefault(RoomStatus.MAINTENANCE, 0L).intValue();
            int notOpenedRooms = statuses.getOrDefault(RoomStatus.DRAFT, 0L).intValue();
            result.put(propertyId, new Occupancy(
                    roomCount, availableRooms, rentedRooms, maintenanceRooms, notOpenedRooms));
        }
        return result;
    }

    public void applyAll(List<PropertyResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }
        List<Long> ids = responses.stream().map(PropertyResponse::getId).filter(id -> id != null).toList();
        Map<Long, Occupancy> map = loadFor(ids);
        for (PropertyResponse response : responses) {
            apply(response, map.getOrDefault(response.getId(), Occupancy.empty()));
        }
    }

    /** Nhà nguyên căn đang bị HĐ DRAFT/PENDING/ACTIVE giữ (room IS NULL). */
    public Set<Long> wholeHouseHeldPropertyIds() {
        return Set.copyOf(tenantContractRepository.findPropertyIdsWithWholeHouseContractsInStatuses(
                List.of(ContractStatus.DRAFT, ContractStatus.PENDING, ContractStatus.ACTIVE)));
    }
}
