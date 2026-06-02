package com.sep490.slms2026.mapper;

import com.sep490.slms2026.dto.request.EquipmentRequest;
import com.sep490.slms2026.dto.response.EquipmentResponse;
import com.sep490.slms2026.entity.Equipment;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EquipmentMapper {

    /**
     * Map từ request sang entity khi tạo mới.
     * room và property được xử lý thủ công trong service vì cần query DB.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "qrCode", ignore = true)
    @Mapping(target = "qrPayload", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Equipment toEntity(EquipmentRequest request);

    /**
     * Update entity từ request (dùng cho PUT/PATCH).
     * Các field null trong request sẽ bị bỏ qua nhờ NullValuePropertyMappingStrategy.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "room", ignore = true)
    @Mapping(target = "qrCode", ignore = true)
    @Mapping(target = "qrPayload", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(EquipmentRequest request, @MappingTarget Equipment equipment);

    /**
     * Map sang response DTO.
     * Các field lồng nhau (room, property) được xử lý qua @AfterMapping trong service
     * hoặc dùng expression mapping bên dưới.
     */
    @Mapping(target = "roomId",         expression = "java(equipment.getRoom() != null ? equipment.getRoom().getId() : null)")
    @Mapping(target = "roomNumber",     expression = "java(equipment.getRoom() != null ? equipment.getRoom().getRoomNumber() : null)")
    @Mapping(target = "propertyId",     expression = "java(resolvePropertyId(equipment))")
    @Mapping(target = "propertyTitle",  expression = "java(resolvePropertyTitle(equipment))")
    @Mapping(target = "propertyAddress",expression = "java(resolvePropertyAddress(equipment))")
    @Mapping(target = "assignmentType", expression = "java(resolveAssignmentType(equipment))")
    EquipmentResponse toResponse(Equipment equipment);

    // ── Helper methods được gọi trong expression ──────────────────────────

    default java.util.UUID resolvePropertyId(Equipment equipment) {
        if (equipment.getRoom() != null) {
            return equipment.getRoom().getProperty().getId();
        }
        return null; // whole-house assignment sẽ được set trong service
    }

    default String resolvePropertyTitle(Equipment equipment) {
        if (equipment.getRoom() != null) {
            return equipment.getRoom().getProperty().getTitle();
        }
        return null;
    }

    default String resolvePropertyAddress(Equipment equipment) {
        if (equipment.getRoom() != null) {
            return equipment.getRoom().getProperty().getAddress();
        }
        return null;
    }

    default String resolveAssignmentType(Equipment equipment) {
        return equipment.getRoom() != null ? "ROOM" : "WHOLE_HOUSE";
    }
}