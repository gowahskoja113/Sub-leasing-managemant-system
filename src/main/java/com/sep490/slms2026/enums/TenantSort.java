package com.sep490.slms2026.enums;

public enum TenantSort{
    MOST_HOUSES_IN_AREA,     // Khu vực có nhiều nhà nguyên căn nhất
    MOST_ROOMS_IN_AREA,      // Khu vực có nhiều phòng cho thuê nhất
    MOST_TENANTS_IN_HOUSE,   // Nhà có nhiều người thuê nhất
    POPULATION_DENSITY,      // Mật độ người thuê trên tổng số phòng của khu vực
    PRICE_ASC,               // Giá tăng dần
    PRICE_DESC,              // Giá giảm dần
    HOUSE_TYPE               // Theo loại nhà
}