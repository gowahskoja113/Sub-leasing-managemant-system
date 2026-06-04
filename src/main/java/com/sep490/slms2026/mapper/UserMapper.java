package com.sep490.slms2026.mapper;

import com.sep490.slms2026.dto.response.TenantResponse;
import com.sep490.slms2026.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source = "tenantProfile.fullName", target = "fullName")
    @Mapping(source = "tenantProfile.citizenIdNumber", target = "citizenIdNumber")
    @Mapping(source = "tenantProfile.roomRentalStatus", target = "roomRentalStatus")
    TenantResponse toTenantResponse(User user);
}