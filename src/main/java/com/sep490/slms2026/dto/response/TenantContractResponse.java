package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantContractResponse {

    private Long id;
    private Long propertyId;
    private Long roomId;
    private String roomNumber;

    private UUID tenantUserId;
    private String tenantFullName;
    private String tenantPhone;
    private String tenantCccd;

    private String contractCode;
    private BigDecimal rentAmount;
    private BigDecimal deposit;
    private LocalDate moveInDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private ContractStatus status;

    /** true nếu status ACTIVE và chưa quá endDate. */
    private Boolean effective;
    /** "Còn hiệu lực" / "Không còn hiệu lực". */
    private String effectiveLabel;

    private String equipmentSnapshot;

    private Integer depositMonths;
    private BigDecimal initialElectricReading;
    private BigDecimal initialWaterReading;
    private String electricMeterImageUrl;
    private String waterMeterImageUrl;
    private List<String> roomConditionUrls;
    private String roomConditionNote;

    // Thanh toán cọc (PayOS)
    private PaymentStatus paymentStatus;
    private Long payosOrderCode;
    private String payosCheckoutUrl;
    private String payosQrCode;

    // Onboarding: thông tin tài khoản tenant sau confirm
    private String  tenantUsername;        // = SĐT khách (username đăng nhập)
    private Boolean tenantAccountCreated;  // true nếu vừa TẠO MỚI tài khoản
    private Boolean tenantRolePromoted;    // true nếu vừa nâng ROLE_USER → ROLE_TENANT

    /** URL file DOCX đã xuất (lưu storage giống ảnh). */
    private String documentUrl;
    private LocalDateTime documentGeneratedAt;
}
