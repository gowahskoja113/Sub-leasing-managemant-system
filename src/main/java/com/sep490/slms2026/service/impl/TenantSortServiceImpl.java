package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.PropertyResponse;
import com.sep490.slms2026.dto.response.TenantSortResponse;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.service.TenantSortService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantSortServiceImpl implements TenantSortService {

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;

    @Override
    public List<TenantSortResponse> getMostPropertiesByZone() {

        return propertyRepository.getMostPropertiesByZone()
                .stream()
                .map(x -> new TenantSortResponse(
                        (String) x[0],
                        (Long) x[1]
                ))
                .toList();
    }

    @Override
    public List<TenantSortResponse> getMostRoomsByZone() {

        return roomRepository.getMostRoomsByZone()
                .stream()
                .map(x -> new TenantSortResponse(
                        (String) x[0],
                        (Long) x[1]
                ))
                .toList();
    }

    @Override
    public List<PropertyResponse> getPropertiesPriceAsc() {

        return propertyRepository.findAll(
                        Sort.by("price").ascending()
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<PropertyResponse> getPropertiesPriceDesc() {

        return propertyRepository.findAll(
                        Sort.by("price").descending()
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private PropertyResponse toResponse(Property property) {

        PropertyResponse response = new PropertyResponse();

        response.setId(property.getId());
        response.setPropertyName(property.getPropertyName());
        response.setPrice(property.getPrice());
        response.setZoneName(property.getZone().getName());

        return response;
    }
}