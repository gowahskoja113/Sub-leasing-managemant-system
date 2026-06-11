package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.PropertyCreateRequest;
import com.sep490.slms2026.dto.response.PropertyResponse;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Zone;
import com.sep490.slms2026.enums.PropertyStatus;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.ZoneRepository;
import com.sep490.slms2026.service.PropertyService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PropertyServiceImpl implements PropertyService {

    private final PropertyRepository propertyRepository;
    private final ZoneRepository zoneRepository;

    @Override
    @Transactional
    public PropertyResponse createProperty(PropertyCreateRequest request) {
        Zone zone = zoneRepository.findById(request.getZoneId())
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy khu vực (Zone) yêu cầu."));

        Property property = new Property();
        property.setPropertyName(request.getPropertyName());
        property.setZone(zone);
        property.setCreatedBy(request.getCreatedBy() != null ? request.getCreatedBy() : request.getManagedBy());
        property.setAreaSize(request.getAreaSize());
        property.setImageUrls(request.getImageUrls());
        property.setDescriptions(request.getDescriptions());
        property.setAddress(request.getAddress() + ", " + buildZoneFullName(zone));

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
        return mapToResponse(saved, request.getAddress());
    }

    @Override
    @Transactional(readOnly = true)
    public PropertyResponse getPropertyById(Long id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy tài sản với ID: " + id));

        String zoneFullName = buildZoneFullName(property.getZone());
        String shortAddress = property.getAddress().replace(", " + zoneFullName, "");

        return mapToResponse(property, shortAddress);
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
    @Transactional
    public PropertyResponse updateProperty(Long id, PropertyCreateRequest request) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy tài sản với ID: " + id));

        property.setPropertyName(request.getPropertyName());
        if (request.getCreatedBy() != null) {
            property.setCreatedBy(request.getCreatedBy());
        } else if (request.getManagedBy() != null) {
            property.setCreatedBy(request.getManagedBy());
        }
        property.setAreaSize(request.getAreaSize());
        property.setImageUrls(request.getImageUrls());
        property.setDescriptions(request.getDescriptions());

        if (!property.getZone().getId().equals(request.getZoneId())) {
            Zone newZone = zoneRepository.findById(request.getZoneId())
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy khu vực (Zone) mới."));
            property.setZone(newZone);
        }

        property.setAddress(request.getAddress() + ", " + buildZoneFullName(property.getZone()));

        if (property.getStatus() != PropertyStatus.ACTIVE) {
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
        return mapToResponse(updated, request.getAddress());
    }

    @Override
    @Transactional
    public void deleteProperty(Long id) {
        Property property = propertyRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy tài sản với ID: " + id));

        if (property.getStatus() == PropertyStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Không thể xóa tài sản đã kích hoạt (ACTIVE). Vui lòng thanh lý hợp đồng gốc trước!");
        }

        propertyRepository.delete(property);
    }

    private String buildZoneFullName(Zone zone) {
        if (zone.getParent() != null) {
            return zone.getName() + ", " + zone.getParent().getName();
        }
        return zone.getName();
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
        response.setStatus(property.getStatus().name());
        response.setDescriptions(property.getDescriptions());
        response.setPrice(property.getPrice());
        response.setCreatedBy(property.getCreatedBy());
        response.setOperationManagerId(property.getOperationManagerId());
        response.setRenovationCompleted(property.isRenovationCompleted());
        response.setImageUrls(property.getImageUrls());
        return response;
    }
}
