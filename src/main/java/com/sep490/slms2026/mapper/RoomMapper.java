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
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "appliedPrice", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Room toEntity(AddRoomRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "property", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "appliedPrice", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntity(UpdateRoomRequest request, @MappingTarget Room room);

    // Entity → Response
    // Lấy property.id và property.propertyName từ nested object
    @Mapping(source = "property.id", target = "propertyId")
    @Mapping(source = "property.propertyName", target = "propertyName")
    @Mapping(source = "price", target = "listedPrice")
    @Mapping(target = "priceLocked", ignore = true)
    @Mapping(target = "currentTenant", ignore = true)
    RoomResponse toResponse(Room room);

    @AfterMapping
    default void fillAppliedPrice(Room room, @MappingTarget RoomResponse response) {
        java.math.BigDecimal listed = room.getPrice();
        java.math.BigDecimal applied = room.getAppliedPrice() != null ? room.getAppliedPrice() : listed;
        response.setListedPrice(listed);
        response.setAppliedPrice(applied);
        if (response.getPrice() == null) {
            response.setPrice(listed);
        }
    }

    @AfterMapping
    default void defaultAppliedOnCreate(@MappingTarget Room room) {
        if (room.getAppliedPrice() == null && room.getPrice() != null) {
            room.setAppliedPrice(room.getPrice());
        }
    }
}
