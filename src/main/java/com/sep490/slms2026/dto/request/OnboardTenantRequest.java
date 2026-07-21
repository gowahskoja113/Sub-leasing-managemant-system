package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnboardTenantRequest {

    @NotBlank(message = "Họ tên khách thuê không được để trống")
    private String fullName;

    @NotBlank(message = "CCCD không được để trống")
    private String cccd;

    @NotBlank(message = "Số điện thoại không được để trống")
    private String phoneNumber;

    @NotNull(message = "Ngày vào ở không được để trống")
    private LocalDate moveInDate;

    @NotNull(message = "Giá thuê không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá thuê phải lớn hơn 0")
    private BigDecimal rentAmount;

    @NotNull(message = "Tiền cọc không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Tiền cọc không hợp lệ")
    private BigDecimal deposit;

    @NotNull(message = "Thiếu ngày kết thúc hợp đồng")
    private LocalDate endDate;

    private String equipmentSnapshot;

    /**
     * Legacy — không cần gửi. BE tự gắn toàn bộ nội thất ACTIVE của nhà/phòng vào HĐ.
     * Chỉ gửi nếu muốn giới hạn subset (không khuyến nghị).
     */
    private List<Long> selectedEquipmentIds;

    /**
     * Thiết bị lắp thêm chưa có trong hệ thống — BE tạo Equipment (ADDED_BY_TENANT) và gộp snapshot.
     * Gửi {@code []} để xóa toàn bộ lắp thêm khi update.
     */
    private List<ContractAddedEquipmentRequest> addedEquipments;

    /**
     * ID thiết bị lắp thêm đã tạo trước qua POST /properties/{id}/equipments.
     * Gửi {@code []} để xóa toàn bộ lắp thêm (khi không dùng addedEquipments inline).
     */
    private List<Long> addedEquipmentIds;

    /** @deprecated Không dùng — BE lấy hết nội thất nhà, không còn decline checkbox. */
    private List<Long> declinedEquipmentIds;

    // Số tháng cọc (1 hoặc 2) — FE tính deposit = rentAmount * depositMonths
    private Integer depositMonths;

    // Chỉ số + ảnh đồng hồ điện nước đầu kỳ
    private BigDecimal initialElectricReading;
    private BigDecimal initialWaterReading;
    private String electricMeterImageUrl;
    private String waterMeterImageUrl;
    /** Thời điểm chụp ảnh đồng hồ điện (ISO-8601). Null → BE ghi = lúc lưu. */
    private java.time.LocalDateTime electricMeterCapturedAt;
    /** Thời điểm chụp ảnh đồng hồ nước (ISO-8601). Null → BE ghi = lúc lưu. */
    private java.time.LocalDateTime waterMeterCapturedAt;

    // Ảnh hiện trạng phòng/nhà (Cloudinary URLs) + ghi chú
    /** @deprecated Ưu tiên {@link #roomConditionPhotos} kèm capturedAt. Vẫn nhận để tương thích FE cũ. */
    private List<String> roomConditionUrls;
    /** Ảnh hiện trạng kèm thời điểm chụp — ưu tiên hơn roomConditionUrls. */
    private List<ContractEvidencePhotoRequest> roomConditionPhotos;
    private String roomConditionNote;

    // Thành viên ở cùng (chủ yếu cho thuê nguyên căn)
    private List<HouseholdMemberRequest> householdMembers;

    // true (mobile): tạo HĐ ở trạng thái PENDING, phải thanh toán cọc (PayOS) + OTP rồi mới confirm.
    // false (mặc định, web): tạo HĐ ACTIVE ngay, set phòng RENTED.
    private boolean requireDepositPayment;

    // true: gửi HĐ cho host duyệt giá trước khi thanh toán cọc
    private boolean requireHostPriceApproval;

    // Các field mới cho Hợp đồng nháp (DRAFT)
    private boolean draft;
    private String draftContractFileUrl;
    private LocalDate expectedReceptionDate;

    /** Ngày sinh khách chính (DRAFT lưu tạm; HĐ chính thức lưu vào Tenant). */
    private LocalDate dateOfBirth;

    /** Ngày cấp CCCD (DRAFT lưu tạm; HĐ chính thức lưu vào Tenant). */
    private LocalDate cccdIssueDate;

    /** Nơi cấp CCCD (DRAFT lưu tạm; HĐ chính thức lưu vào Tenant). */
    private String cccdIssuePlace;

    /** Hộ khẩu thường trú / HKTT (DRAFT lưu tạm; HĐ chính thức lưu vào Tenant). */
    private String permanentAddress;
}
