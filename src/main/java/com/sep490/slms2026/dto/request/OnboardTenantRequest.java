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

    // Optional
    private LocalDate endDate;

    private String equipmentSnapshot;

    // Số tháng cọc (1 hoặc 2) — FE tính deposit = rentAmount * depositMonths
    private Integer depositMonths;

    // Chỉ số + ảnh đồng hồ điện nước đầu kỳ
    private BigDecimal initialElectricReading;
    private BigDecimal initialWaterReading;
    private String electricMeterImageUrl;
    private String waterMeterImageUrl;

    // Ảnh hiện trạng phòng/nhà (Cloudinary URLs) + ghi chú
    private List<String> roomConditionUrls;
    private String roomConditionNote;

    // Thành viên ở cùng (chủ yếu cho thuê nguyên căn)
    private List<HouseholdMemberRequest> householdMembers;

    // true (mobile): tạo HĐ ở trạng thái PENDING, phải thanh toán cọc (PayOS) + OTP rồi mới confirm.
    // false (mặc định, web): tạo HĐ ACTIVE ngay, set phòng RENTED.
    private boolean requireDepositPayment;
}
