//package com.sep490.slms2026.util;
//
//import com.sep490.slms2026.repository.TenantRepository;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Component;
//
//@Component
//@RequiredArgsConstructor
//public class ValidationUtil {
//
//    private final TenantRepository tenantRepository;
//
//    /**
//     * Hàm kiểm tra CCCD đã tồn tại chưa
//     * @param citizenId Số CCCD cần check
//     */
//    public void validateUniqueCitizenId(String citizenId) {
//        // Nên check null hoặc rỗng trước khi query DB
//        if (citizenId == null || citizenId.trim().isEmpty()) {
//            throw new RuntimeException("Số CCCD không được để trống!");
//        }
//
//        if (tenantRepository.existsByCitizenIdNumber(citizenId)) {
//            throw new RuntimeException("Số CCCD đã tồn tại trong hệ thống!");
//        }
//    }
//}