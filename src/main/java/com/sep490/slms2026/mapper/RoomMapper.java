package com.sep490.slms2026.mapper;

import com.sep490.slms2026.dto.request.AddRoomRequest;
import com.sep490.slms2026.dto.request.UpdateRoomRequest;
import com.sep490.slms2026.dto.response.RoomResponse;
import com.sep490.slms2026.entity.Room;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        // Bỏ qua field null trong source khi update — dùng cho PATCH sau này
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface RoomMapper {

    // Request → Entity
    // property phải set thủ công trong service vì cần fetch từ DB
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "property", ignore = true)
    @Mapping(target = "status", ignore = true) // default DRAFT trong entity
    Room toEntity(AddRoomRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "property", ignore = true)
    @Mapping(target = "status", ignore = true)
    void updateEntity(UpdateRoomRequest request, @MappingTarget Room room);

    // Entity → Response
    // Lấy property.id và property.propertyName từ nested object
    @Mapping(source = "property.id", target = "propertyId")
    @Mapping(source = "property.propertyName", target = "propertyName")
    RoomResponse toResponse(Room room);
}
