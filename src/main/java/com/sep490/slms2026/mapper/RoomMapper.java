package com.sep490.slms2026.mapper;

import com.sep490.slms2026.dto.request.RoomRequest;
import com.sep490.slms2026.dto.response.RoomResponse;
import com.sep490.slms2026.entity.Room;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RoomMapper {

    RoomResponse toResponse(Room room);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "property", ignore = true)
    Room toEntity(RoomRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "property", ignore = true)
    void updateEntityFromRequest(RoomRequest request, @MappingTarget Room room);
}