package com.sep490.slms2026.mapper;

import com.sep490.slms2026.dto.response.MaintenanceHistoryResponse;
import com.sep490.slms2026.dto.response.MaintenancePhotoResponse;
import com.sep490.slms2026.dto.response.MaintenanceResponse;
import com.sep490.slms2026.entity.MaintenanceHistory;
import com.sep490.slms2026.entity.MaintenancePhoto;
import com.sep490.slms2026.entity.MaintenanceRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MaintenanceMapper {

    @Mapping(target = "equipmentId", source = "equipment.id")
    @Mapping(target = "equipmentName", source = "equipment.name")
    @Mapping(target = "tenantId", source = "tenant.id")
    @Mapping(target = "tenantName", source = "tenant.fullName")
    @Mapping(target = "contractId", source = "contract.id")
    @Mapping(target = "priority", expression = "java(request.getPriority().name())")
    @Mapping(target = "status", expression = "java(request.getStatus().name())")
    MaintenanceResponse toResponse(MaintenanceRequest request);

    @Mapping(target = "photoType", expression = "java(photo.getPhotoType().name())")
    MaintenancePhotoResponse toPhotoResponse(MaintenancePhoto photo);

    @Mapping(target = "performedById", source = "performedBy.id")
    @Mapping(target = "performedByName", source = "performedBy.fullName")
    @Mapping(target = "statusChangedTo", expression = "java(history.getStatusChangedTo().name())")
    MaintenanceHistoryResponse toHistoryResponse(MaintenanceHistory history);
}