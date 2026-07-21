package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import com.sep490.slms2026.dto.response.TenantContractDetailResponse.EquipmentItem;

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
    private LocalDate tenantDateOfBirth;
    private LocalDate tenantCccdIssueDate;
    private String tenantCccdIssuePlace;
    private String tenantPermanentAddress;

    private String type;
    private String lessorName;
    private String lessorPhone;
    private String lesseeName;
    private String lesseeCccd;
    private String lesseePhone;
    private String propertyName;
    private String notes;
    private LocalDateTime signedAt;
    private LocalDateTime terminatedAt;
    private String terminationReason;
    private String terminationType;
    private String pdfUrl;
    /** Thiết bị khách nhận bàn giao theo HĐ (đã chọn). */
    private List<EquipmentItem> equipmentList;

    /** Thiết bị ACTIVE trong phạm vi HĐ — dùng cho checkbox chọn lúc tạo/sửa draft. */
    private List<EquipmentItem> availableEquipmentList;

    /** ID thiết bị đã chọn (tiện cho FE đồng bộ checkbox). */
    private List<Long> selectedEquipmentIds;

    /** ID thiết bị có sẵn đã chọn (subset của selectedEquipmentIds). */
    private List<Long> selectedExistingIds;

    /** ID thiết bị lắp thêm đã gắn HĐ. */
    private List<Long> selectedAddedIds;

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
    private java.time.LocalDateTime electricMeterCapturedAt;
    private java.time.LocalDateTime waterMeterCapturedAt;
    /** Legacy — danh sách URL thuần (vẫn trả để FE cũ không vỡ). */
    private List<String> roomConditionUrls;
    /** Ảnh hiện trạng kèm thời điểm chụp (bằng chứng). */
    private List<ContractEvidencePhotoResponse> roomConditionPhotos;
    private String roomConditionNote;

    // Thanh toán cọc (PayOS / chuyển khoản)
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

    private String priceApprovalStatus;
    private String priceRejectReason;

    // Các field mới cho Hợp đồng nháp (DRAFT)
    private UUID assignedManagerId;
    private String assignedManagerName;
    private String draftContractFileUrl;
    private LocalDate expectedReceptionDate;
    
    private List<HouseholdMemberResponse> householdMembers;

    /** true nếu đã có file HĐ (Cloudinary hoặc legacy) — FE bật nút View Contract. */
    private Boolean contractFileAvailable;

    public String getCode() {
        return this.contractCode;
    }

    public String getRoomCode() {
        return this.roomNumber;
    }
}
