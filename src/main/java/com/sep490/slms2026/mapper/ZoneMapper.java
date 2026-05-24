package com.sep490.slms2026.mapper;

import com.sep490.slms2026.dto.request.ZoneRequest;
import com.sep490.slms2026.dto.response.ZoneResponse;
import com.sep490.slms2026.entity.Zone;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ZoneMapper {
    Zone toEntity(ZoneRequest request);
    ZoneResponse toResponse(Zone zone);
    void updateEntityFromRequest(ZoneRequest request, @MappingTarget Zone zone);
}