package com.sep490.slms2026.mapper;

import com.sep490.slms2026.dto.request.PropertyRequest;
import com.sep490.slms2026.dto.response.PropertyResponse;
import com.sep490.slms2026.dto.response.RoomResponse;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PropertyMapper {

    @Mapping(target = "zoneId", source = "zone.id")
    @Mapping(target = "zoneName", source = "zone.name")
    PropertyResponse toResponse(Property property);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "zone", ignore = true)
    @Mapping(target = "rooms", ignore = true)
    Property toEntity(PropertyRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "zone", ignore = true)
    @Mapping(target = "rooms", ignore = true)
    void updateEntityFromRequest(PropertyRequest request, @MappingTarget Property property);

    RoomResponse toRoomResponse(Room room);
}