package com.sep490.slms2026.mapper;

import com.sep490.slms2026.dto.request.EquipmentRequest;
import com.sep490.slms2026.dto.response.EquipmentResponse;
import com.sep490.slms2026.entity.Equipment;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EquipmentMapper {

    @Mapping(target = "roomId", source = "room.id")
    @Mapping(target = "roomNumber", source = "room.roomNumber")
    @Mapping(target = "status", source = "status", qualifiedByName = "statusToString")
    EquipmentResponse toResponse(Equipment equipment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "qrCode", ignore = true)
    @Mapping(target = "qrPayload", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "maintenanceRequests", ignore = true)
    Equipment toEntity(EquipmentRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "qrCode", ignore = true)
    @Mapping(target = "qrPayload", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "maintenanceRequests", ignore = true)
    void updateEntityFromRequest(EquipmentRequest request, @MappingTarget Equipment equipment);

    @Named("statusToString")
    default String statusToString(com.sep490.slms2026.enums.EquipmentStatus status) {
        return status != null ? status.name() : null;
    }
}