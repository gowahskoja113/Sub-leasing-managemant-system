package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.OnboardTenantRequest;
import com.sep490.slms2026.dto.response.TenantContractResponse;
import com.sep490.slms2026.entity.TenantContract;

import java.util.List;

public interface TenantOnboardingService {

    /**
     * Onboard khách thuê vào 1 phòng (roomId != null) hoặc thuê nguyên căn (roomId == null):
     * tạo/tra tài khoản khách (ROLE_TENANT), tạo hợp đồng thuê ACTIVE, chuyển phòng sang RENTED.
     */
    TenantContractResponse onboardTenant(Long propertyId, Long roomId, OnboardTenantRequest request);

    List<TenantContractResponse> getContractsByProperty(Long propertyId);

    TenantContractResponse getContract(Long contractId);

    /**
     * Tạo link/QR thanh toán onboard qua PayOS (cọc + tiền nhà chu kỳ đầu trên một QR).
     * Sau khi thanh toán thành công, BE ghi nhận hoá đơn onboard + FIRST RENT (PAID) nếu có tiền nhà.
     */
    TenantContractResponse createDepositPayment(Long contractId);

    /**
     * Manager xác nhận OTP của mình ({@code CONTRACT_CONFIRM_MANAGER}).
     * Chỉ kích hoạt HĐ khi cả tenant lẫn manager đã verify.
     */
    TenantContractResponse confirmContract(Long contractId, String otp);

    /**
     * Tenant (đã login) xác nhận OTP của mình ({@code CONTRACT_CONFIRM_TENANT}).
     * Chỉ kích hoạt HĐ khi cả hai bên đã verify.
     */
    TenantContractResponse confirmContractByTenant(Long contractId, String otp);

    /**
     * Manager gửi lại OTP của mình. Tenant mới là người khởi động gửi cả 2 mã
     * qua {@link #sendDualContractConfirmOtps(Long)}.
     */
    void sendContractConfirmOtp(Long contractId);

    /**
     * Tenant bấm "Gửi OTP xác nhận": sinh 2 mã (TENANT + MANAGER), gửi về số override.
     * Kiểm tra cửa sổ nhận sớm tại bước này (không chặn lúc verify).
     */
    void sendDualContractConfirmOtps(Long contractId);

    /** HĐ PENDING + PAID đang chờ tenant xác nhận (cho RootNavigator ép màn confirm). */
    java.util.Optional<TenantContractResponse> findPendingConfirmForTenant(java.util.UUID tenantUserId);

    /** Đánh dấu đã thanh toán theo orderCode (gọi từ webhook PayOS). */
    void markDepositPaid(Long payosOrderCode);

    /** Chủ động hỏi PayOS trạng thái đơn & đồng bộ paymentStatus (dùng cho nút "Kiểm tra" / local không có webhook). */
    TenantContractResponse syncPaymentStatus(Long contractId);

    /** Lấy danh sách hợp đồng chờ xử lý của manager (lọc theo JWT), có thể filter theo trạng thái. */
    List<TenantContractResponse> getManagedContracts(String status);

    /** Chỉnh giá & gửi duyệt lại đối với HĐ đang bị từ chối hoặc chờ duyệt. */
    TenantContractResponse resubmitApproval(Long contractId, com.sep490.slms2026.dto.request.ResubmitApprovalRequest request);

    /** Hủy hợp đồng đang chờ (chưa ACTIVE). */
    void cancelContract(Long contractId);
    
    /** Hard delete hợp đồng (chỉ dành cho test/import nhầm). */
    void deleteContract(Long contractId);

    /**
     * Thanh lý / chấm dứt HĐ đang ACTIVE (hoặc EXPIRED) — trả phòng sớm, vi phạm, thỏa thuận.
     * Không khoá tài khoản khách — khoá theo vòng đời cọc (xác nhận hoàn cọc / im lặng 30 ngày).
     */
    TenantContractResponse terminateActiveContract(Long contractId, com.sep490.slms2026.dto.request.TerminateContractRequest request);

    /**
     * Gia hạn hợp đồng đang ACTIVE.
     * Dời endDate (không vượt quá InboundContract), cập nhật giá thuê (nếu có),
     * ghi lịch sử giá, và gửi thông báo.
     */
    TenantContractResponse extendContract(Long contractId, com.sep490.slms2026.dto.request.ExtendContractRequest request);

    /**
     * Khoá {@code ROLE_TENANT} nếu không còn HĐ sống (DRAFT/PENDING/ACTIVE/EXPIRED),
     * bỏ qua {@code exceptContractId} (HĐ vừa xác nhận cọc / đang im lặng — có thể vẫn ACTIVE).
     * Gọi sau confirmRefund hoặc cron 30 ngày — không gọi lúc thanh lý.
     */
    void disableTenantAccountIfNoActiveContracts(java.util.UUID tenantUserId, Long exceptContractId);

    /** Đồng bộ ACTIVE→EXPIRED khi quá endDate; khôi phục thiết bị DISABLE. Không giải phóng phòng. */
    void syncExpiredIfNeeded(TenantContract contract);

    /** Lấy danh sách hợp đồng lọc theo trạng thái (nháp). */
    List<TenantContractResponse> getContractsByStatus(String status);

    /** Cập nhật thông tin hợp đồng nháp. */
    TenantContractResponse updateDraftContract(Long contractId, com.sep490.slms2026.dto.request.UpdateDraftContractRequest request);

    /**
     * Cascade khi đổi Operation Manager của nhà: gán lại {@code assignedManager} cho mọi hợp đồng
     * chưa kết thúc (DRAFT/PENDING/ACTIVE) của nhà sang quản lý mới + gửi thông báo. Trả về số HĐ đã đổi.
     */
    int reassignManagerForProperty(Long propertyId, java.util.UUID newManagerId);

    /**
     * One-shot backfill: HĐ DRAFT/PENDING/ACTIVE còn {@code assignedManager = null} nhưng nhà đã có
     * {@code operationManagerId} → gán lại + notify. Idempotent (chạy lại không đụng HĐ đã có manager).
     */
    int backfillMissingAssignedManagers();

    /**
     * Tự động hủy HĐ nháp/chờ (DRAFT/PENDING) mà khách không đến nhận nhà quá hạn (no-show):
     * {@code moveInDate + noShowGraceDays < hôm nay}. Ghi {@code terminationType = NO_SHOW},
     * giải phóng phòng/căn + notify quản lý. Trả về số HĐ đã hủy.
     */
    int autoCancelNoShowContracts();

    /**
     * Cron 07:15: nhắc quản lý đón khách (mai / hôm nay / quá hạn 1-3-7 ngày).
     * Quá hạn gửi thêm admin + host. Trả về số HĐ đã bắn nhắc.
     */
    int remindUpcomingReception();
}
