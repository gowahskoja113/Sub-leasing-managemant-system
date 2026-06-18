package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.OnboardTenantRequest;
import com.sep490.slms2026.dto.response.TenantContractResponse;

import java.util.List;

public interface TenantOnboardingService {

    /**
     * Onboard khách thuê vào 1 phòng (roomId != null) hoặc thuê nguyên căn (roomId == null):
     * tạo/tra tài khoản khách (ROLE_TENANT), tạo hợp đồng thuê ACTIVE, chuyển phòng sang RENTED.
     */
    TenantContractResponse onboardTenant(Long propertyId, Long roomId, OnboardTenantRequest request);

    List<TenantContractResponse> getContractsByProperty(Long propertyId);

    TenantContractResponse getContract(Long contractId);

    /** Tạo link/QR thanh toán cọc qua PayOS cho hợp đồng đang PENDING. */
    TenantContractResponse createDepositPayment(Long contractId);

    /** Hoàn tất HĐ (sau khi đã thanh toán cọc + OTP): set ACTIVE, phòng RENTED. */
    TenantContractResponse confirmContract(Long contractId);

    /** Đánh dấu đã thanh toán theo orderCode (gọi từ webhook PayOS). */
    void markDepositPaid(Long payosOrderCode);

    /** Chủ động hỏi PayOS trạng thái đơn & đồng bộ paymentStatus (dùng cho nút "Kiểm tra" / local không có webhook). */
    TenantContractResponse syncPaymentStatus(Long contractId);
}
