package com.sep490.slms2026.mapper;

import com.sep490.slms2026.dto.request.ZoneRequest;
import com.sep490.slms2026.dto.response.ZoneResponse;
import com.sep490.slms2026.entity.Zone;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ZoneMapper {

    Zone toEntity(ZoneRequest request);

    @Mapping(source = "parent.id", target = "parentId")
    @Mapping(source = "parent.name", target = "parentName")
    ZoneResponse toResponse(Zone zone);

    void updateEntityFromRequest(ZoneRequest request, @MappingTarget Zone zone);

    @AfterMapping
    default void setFullName(Zone zone, @MappingTarget ZoneResponse response) {
        if (zone == null) return;
        response.setFullName(buildFullName(zone));
    }

    default String buildFullName(Zone zone) {
        StringBuilder sb = new StringBuilder(zone.getName());
        Zone currentParent = zone.getParent();
        while (currentParent != null) {
            sb.append(", ").append(currentParent.getName());
            currentParent = currentParent.getParent();
        }
        return sb.toString();
    }
}