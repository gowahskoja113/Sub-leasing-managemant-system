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

    /** Tạo link/QR thanh toán cọc qua PayOS cho hợp đồng đang PENDING. */
    TenantContractResponse createDepositPayment(Long contractId);

    /** Hoàn tất HĐ (sau khi đã thanh toán cọc + OTP): set ACTIVE, phòng RENTED. */
    TenantContractResponse confirmContract(Long contractId, String otp);

    /** Gửi OTP SMS tới SĐT khách thuê để xác nhận hoàn tất HĐ. */
    void sendContractConfirmOtp(Long contractId);

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

    /**
     * Thanh lý / chấm dứt HĐ đang ACTIVE (hoặc EXPIRED) — trả phòng sớm, vi phạm, thỏa thuận.
     */
    TenantContractResponse terminateActiveContract(Long contractId, com.sep490.slms2026.dto.request.TerminateContractRequest request);

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
}
