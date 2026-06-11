package com.sep490.slms2026.mapper;

import com.sep490.slms2026.dto.request.PropertyCreateRequest;
import com.sep490.slms2026.dto.response.PropertyResponse;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Zone;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PropertyMapper {

    @Mapping(target = "zoneId", source = "zone.id")
    @Mapping(target = "zoneName", source = "zone", qualifiedByName = "getOnlyZoneName")
    @Mapping(target = "fullAddress", source = "address")
    @Mapping(target = "shortAddress", source = "property", qualifiedByName = "extractShortAddress")
    @Mapping(target = "areaSize", source = "areaSize")
    PropertyResponse toResponse(Property property);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "zone", ignore = true)
    @Mapping(target = "inboundContract", ignore = true)
    @Mapping(target = "utilityReadings", ignore = true)
    @Mapping(target = "areaSize", source = "areaSize")
    @Mapping(target = "managedBy", ignore = true) // <--- THÊM DÒNG NÀY: Bỏ qua lỗi lệch kiểu dữ liệu khi tạo
    Property toEntity(PropertyCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "zone", ignore = true)
    @Mapping(target = "inboundContract", ignore = true)
    @Mapping(target = "utilityReadings", ignore = true)
    @Mapping(target = "managedBy", ignore = true) // <--- THÊM DÒNG NÀY: Bỏ qua lỗi lệch kiểu dữ liệu khi update
    void updateEntityFromRequest(PropertyCreateRequest request, @MappingTarget Property property);

    @Named("getOnlyZoneName")
    default String resolveZoneName(Zone zone) {
        if (zone == null) return null;
        return zone.getName();
    }

    @Named("extractShortAddress")
    default String resolveShortAddress(Property property) {
        if (property == null || property.getAddress() == null) return null;
        if (property.getZone() == null) return property.getAddress();

        String zoneFullName = property.getZone().getParent() != null
                ? property.getZone().getName() + ", " + property.getZone().getParent().getName()
                : property.getZone().getName();

        return property.getAddress().replace(", " + zoneFullName, "");
    }
}