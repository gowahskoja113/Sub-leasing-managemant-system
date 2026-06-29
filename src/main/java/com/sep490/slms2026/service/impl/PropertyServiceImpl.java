package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.PropertyCreateRequest;
import com.sep490.slms2026.dto.response.HandoverEquipmentResponse;
import com.sep490.slms2026.dto.response.PropertyResponse;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Zone;
import com.sep490.slms2026.repository.HandoverEquipmentRepository;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.ZoneRepository;
import com.sep490.slms2026.service.PropertyDeletionService;
import com.sep490.slms2026.service.PropertyService;
import com.sep490.slms2026.exception.ConflictException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PropertyServiceImpl implements PropertyService {

    private final PropertyRepository propertyRepository;
    private final ZoneRepository zoneRepository;
    private final PropertyDeletionService propertyDeletionService;
    private final TenantContractRepository tenantContractRepository;
    private final RoomRepository roomRepository;
    private final HandoverEquipmentRepository handoverEquipmentRepository;
    private final RenovationSessionViewMapper renovationSessionViewMapper;

    @Override
    @Transactional
    public PropertyResponse createProperty(PropertyCreateRequest request) {
        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy khu vực (Zone) yêu cầu."));

        Property property = new Property();
        property.setPropertyName(request.getPropertyName());
        property.setZone(zone);
        if (request.getCreatedBy() != null) {
            property.setCreatedBy(request.getCreatedBy());
        }
        property.setAreaSize(request.getAreaSize());
        property.setLength(request.getLength());
        property.setWidth(request.getWidth());
        property.setImageUrls(request.getImageUrls());
        property.setDescriptions(request.getDescriptions());
        String shortAddress = request.getAddress().trim();
        String fullAddress = buildFullAddress(shortAddress, zone);
        assertAddressAvailable(fullAddress, null);
        property.setAddress(fullAddress);

        if (request.getTotalFloor() != null) {
            property.setTotalFloor(request.getTotalFloor());
        }
        if (request.getTotalRooms() != null) {
            property.setTotalRooms(request.getTotalRooms());
        }

        if (request.getWholeHouse() != null) {
            property.setWholeHouse(request.getWholeHouse());
        }

        property.setStatus(PropertyStatus.DRAFT);
        Property saved = propertyRepository.save(property);
        return mapToResponse(saved, shortAddress);
    }

    @Override
    @Transactional(readOnly = true)
    public PropertyResponse getPropertyById(Long id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy tài sản với ID: " + id));

        String zoneFullName = buildZoneFullName(property.getZone());
        String shortAddress = property.getAddress().replace(", " + zoneFullName, "");

        PropertyResponse response = mapToResponse(property, shortAddress);
        response.setHandoverEquipments(handoverEquipmentRepository.findByPropertyIdOrderByIdAsc(id).stream()
                .map(he -> HandoverEquipmentResponse.builder()
                        .id(he.getId())
                        .catalogId(he.getCatalog().getId())
                        .catalogName(he.getCatalog().getName())
                        .description(he.getDescription())
                        .roomNumber(he.getRoomNumber())
                        .houseArea(he.getHouseArea())
                        .status(he.getStatus())
                        .quantity(he.getQuantity())
                        .note(he.getNote())
                        .build())
                .toList());
        response.setActiveRenovationSession(
                renovationSessionViewMapper.findActiveSession(id).orElse(null));
        response.setRenovationSessions(renovationSessionViewMapper.listHistoryNewestFirst(id));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PropertyResponse> getAllProperties(Pageable pageable) {
        return propertyRepository.findAll(pageable).map(property -> {
            String zoneFullName = buildZoneFullName(property.getZone());
            String shortAddress = property.getAddress().replace(", " + zoneFullName, "");
            return mapToResponse(property, shortAddress);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<PropertyResponse> getRentableProperties() {
        // Nhà nguyên căn đã có HĐ active (room == null) -> không còn cho thuê
        Set<Long> wholeHouseRentedIds = tenantContractRepository
                .findByRoomIsNullAndStatus(ContractStatus.ACTIVE).stream()
                .map(c -> c.getProperty().getId())
                .collect(Collectors.toSet());
        // Tòa có ít nhất 1 phòng trống
        Set<Long> propertyIdsWithAvailableRooms =
                new HashSet<>(roomRepository.findPropertyIdsByRoomStatus(RoomStatus.AVAILABLE));

        List<PropertyResponse> result = new ArrayList<>();
        for (Property p : propertyRepository.findAll()) {
            if (p.getStatus() != PropertyStatus.ACTIVE) {
                continue;
            }
            boolean rentable = Boolean.TRUE.equals(p.getWholeHouse())
                    ? !wholeHouseRentedIds.contains(p.getId())          // nguyên căn: chưa có khách
                    : propertyIdsWithAvailableRooms.contains(p.getId()); // chia phòng: còn phòng trống
            if (!rentable) {
                continue;
            }
            String zoneFullName = buildZoneFullName(p.getZone());
            String shortAddress = p.getAddress().replace(", " + zoneFullName, "");
            PropertyResponse resp = mapToResponse(p, shortAddress);
            resp.setRentalAvailable(true);
            result.add(resp);
        }
        return result;
    }

    @Override
    @Transactional
    public PropertyResponse updateProperty(Long id, PropertyCreateRequest request) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy tài sản với ID: " + id));

        property.setPropertyName(request.getPropertyName());
        if (request.getCreatedBy() != null) {
            property.setCreatedBy(request.getCreatedBy());
        }
        property.setAreaSize(request.getAreaSize());
        property.setLength(request.getLength());
        property.setWidth(request.getWidth());
        property.setImageUrls(request.getImageUrls());
        property.setDescriptions(request.getDescriptions());

        if (!property.getZone().getId().equals(request.getZoneId())) {
            Zone newZone = zoneRepository.findById(request.getZoneId())
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy khu vực (Zone) mới."));
            property.setZone(newZone);
        }

        String shortAddress = request.getAddress().trim();
        String fullAddress = buildFullAddress(shortAddress, property.getZone());
        assertAddressAvailable(fullAddress, id);
        property.setAddress(fullAddress);

        if (property.getStatus() != PropertyStatus.ACTIVE && property.getStatus() != PropertyStatus.RENTED) {
            if (request.getTotalFloor() != null) {
                property.setTotalFloor(request.getTotalFloor());
            }
            if (request.getTotalRooms() != null) {
                property.setTotalRooms(request.getTotalRooms());
            }
            if (request.getWholeHouse() != null) {
                property.setWholeHouse(request.getWholeHouse());
            }
        }

        Property updated = propertyRepository.save(property);
        return mapToResponse(updated, shortAddress);
    }

    @Override
    @Transactional
    public void deleteProperty(Long id) {
        propertyDeletionService.purgeProperty(id);
    }

    private String buildZoneFullName(Zone zone) {
        if (zone.getParent() != null) {
            return zone.getName() + ", " + zone.getParent().getName();
        }
        return zone.getName();
    }

    private String buildFullAddress(String shortAddress, Zone zone) {
        return shortAddress + ", " + buildZoneFullName(zone);
    }

    private void assertAddressAvailable(String fullAddress, Long excludePropertyId) {
        boolean exists = excludePropertyId == null
                ? propertyRepository.existsByAddressIgnoreCase(fullAddress)
                : propertyRepository.existsByAddressIgnoreCaseAndIdNot(fullAddress, excludePropertyId);
        if (exists) {
            throw new ConflictException("Địa chỉ này đã được sử dụng cho một tòa nhà khác");
        }
    }

    private PropertyResponse mapToResponse(Property property, String shortAddress) {
        PropertyResponse response = new PropertyResponse();
        response.setId(property.getId());
        response.setPropertyName(property.getPropertyName());
        response.setShortAddress(shortAddress);
        response.setFullAddress(property.getAddress());
        response.setZoneId(property.getZone().getId());
        response.setZoneName(property.getZone().getName());
        response.setWholeHouse(property.getWholeHouse());
        response.setHasRenovation(property.getHasRenovation());
        response.setTotalFloor(property.getTotalFloor());
        response.setTotalRooms(property.getTotalRooms());
        response.setAreaSize(property.getAreaSize());
        response.setLength(property.getLength());
        response.setWidth(property.getWidth());
        response.setStatus(property.getStatus().name());
        response.setDescriptions(property.getDescriptions());
        response.setPrice(property.getPrice());
        response.setCreatedBy(property.getCreatedBy());
        response.setOperationManagerId(property.getOperationManagerId());
        response.setRenovationCompleted(property.isRenovationCompleted());
        response.setImageUrls(property.getImageUrls());
        response.setElectricityUnitPrice(property.getElectricityUnitPrice());
        response.setWaterUnitPrice(property.getWaterUnitPrice());
        return response;
    }
}
