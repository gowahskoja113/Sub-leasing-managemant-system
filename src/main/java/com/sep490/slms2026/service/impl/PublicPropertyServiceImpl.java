package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.GuestPropertyResponse;
import com.sep490.slms2026.dto.response.RoomResponse;
import com.sep490.slms2026.entity.MonthlyReading;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Zone;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.enums.UtilityType;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.mapper.RoomMapper;
import com.sep490.slms2026.repository.EquipmentRepository;
import com.sep490.slms2026.repository.MonthlyReadingRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.service.PublicPropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PublicPropertyServiceImpl implements PublicPropertyService {

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final TenantContractRepository tenantContractRepository;
    private final EquipmentRepository equipmentRepository;
    private final MonthlyReadingRepository monthlyReadingRepository;
    private final RoomMapper roomMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<GuestPropertyResponse> listPublicProperties(Pageable pageable) {
        Set<Long> wholeHouseRentedIds = loadWholeHouseRentedIds();
        Set<Long> propertyIdsWithAvailableRooms = loadPropertyIdsWithAvailableRooms();

        return propertyRepository
                .findByStatusAndOperationManagerIdIsNotNull(PropertyStatus.ACTIVE, pageable)
                .map(property -> mapToGuestResponse(
                        property,
                        wholeHouseRentedIds,
                        propertyIdsWithAvailableRooms));
    }

    @Override
    @Transactional(readOnly = true)
    public GuestPropertyResponse getPublicProperty(Long id) {
        Property property = loadPublicProperty(id);
        return mapToGuestResponse(
                property,
                loadWholeHouseRentedIds(),
                loadPropertyIdsWithAvailableRooms());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomResponse> getPublicRooms(Long propertyId) {
        loadPublicProperty(propertyId);

        return roomRepository.findByPropertyIdWithProperty(propertyId).stream()
                .filter(room -> room.getStatus() == RoomStatus.AVAILABLE)
                .map(roomMapper::toResponse)
                .toList();
    }

    private Property loadPublicProperty(Long id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài sản với ID: " + id));

        if (property.getStatus() != PropertyStatus.ACTIVE || property.getOperationManagerId() == null) {
            throw new ResourceNotFoundException("Không tìm thấy tài sản với ID: " + id);
        }
        return property;
    }

    private Set<Long> loadWholeHouseRentedIds() {
        return new HashSet<>(tenantContractRepository
                .findByRoomIsNullAndStatus(ContractStatus.ACTIVE).stream()
                .map(c -> c.getProperty().getId())
                .toList());
    }

    private Set<Long> loadPropertyIdsWithAvailableRooms() {
        return new HashSet<>(roomRepository.findPropertyIdsByRoomStatus(RoomStatus.AVAILABLE));
    }

    private GuestPropertyResponse mapToGuestResponse(
            Property property,
            Set<Long> wholeHouseRentedIds,
            Set<Long> propertyIdsWithAvailableRooms) {

        Zone zone = property.getZone();
        String zoneFullName = buildZoneFullName(zone);
        String shortAddress = property.getAddress().replace(", " + zoneFullName, "");

        GuestPropertyResponse response = new GuestPropertyResponse();
        response.setId(property.getId());
        response.setPropertyName(property.getPropertyName());
        response.setShortAddress(shortAddress);
        response.setFullAddress(property.getAddress());
        response.setZoneId(zone.getId());
        response.setZoneName(zone.getName());
        response.setWholeHouse(property.getWholeHouse());
        response.setHasRenovation(property.getHasRenovation());
        response.setTotalFloor(property.getTotalFloor());
        response.setTotalRooms(property.getTotalRooms());
        response.setAreaSize(property.getAreaSize());
        response.setStatus(property.getStatus().name());
        response.setDescriptions(property.getDescriptions());
        response.setPrice(property.getPrice());
        response.setRenovationCompleted(property.isRenovationCompleted());
        response.setImageUrls(property.getImageUrls());

        response.setLatitude(property.getLatitude());
        response.setLongitude(property.getLongitude());
        response.setDepositMonths(property.getDepositMonths());
        response.setServiceFee(property.getServiceFee());
        response.setElectricityUnitPrice(resolveElectricityUnitPrice(property));
        response.setWaterUnitPrice(resolveWaterUnitPrice(property));
        response.setAmenities(equipmentRepository.findDistinctAmenityNamesByPropertyId(property.getId()));

        boolean rentable = Boolean.TRUE.equals(property.getWholeHouse())
                ? !wholeHouseRentedIds.contains(property.getId())
                : propertyIdsWithAvailableRooms.contains(property.getId());
        response.setRentalAvailable(rentable);

        return response;
    }

    private BigDecimal resolveElectricityUnitPrice(Property property) {
        if (property.getElectricityUnitPrice() != null) {
            return property.getElectricityUnitPrice();
        }
        return monthlyReadingRepository
                .findTopByPropertyIdAndRoomIsNullAndUtilityTypeOrderByBillingMonthDesc(
                        property.getId(), UtilityType.ELECTRIC)
                .map(MonthlyReading::getUnitPrice)
                .orElse(null);
    }

    private BigDecimal resolveWaterUnitPrice(Property property) {
        if (property.getWaterUnitPrice() != null) {
            return property.getWaterUnitPrice();
        }
        return monthlyReadingRepository
                .findTopByPropertyIdAndRoomIsNullAndUtilityTypeOrderByBillingMonthDesc(
                        property.getId(), UtilityType.WATER)
                .map(MonthlyReading::getUnitPrice)
                .orElse(null);
    }

    private String buildZoneFullName(Zone zone) {
        if (zone.getParent() != null) {
            return zone.getName() + ", " + zone.getParent().getName();
        }
        return zone.getName();
    }
}
