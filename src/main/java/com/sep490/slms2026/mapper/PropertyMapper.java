package com.sep490.slms2026.mapper;

import com.sep490.slms2026.dto.request.PropertyRequest;
import com.sep490.slms2026.dto.response.PropertyResponse;
import com.sep490.slms2026.entity.Property;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = {ZoneMapper.class}, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PropertyMapper {

    @Mapping(target = "zoneId", source = "zone.id")
    @Mapping(target = "zoneFullName", source = "zone")
    PropertyResponse toResponse(Property property);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "zone", ignore = true)
    @Mapping(target = "rooms", ignore = true) // 🔥 KHÓA CHẶT: Không cho tự map tự động mảng room tránh sinh rác bị null roomNumber
    Property toEntity(PropertyRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "zone", ignore = true)
    @Mapping(target = "rooms", ignore = true)
    void updateEntityFromRequest(PropertyRequest request, @MappingTarget Property property);
}