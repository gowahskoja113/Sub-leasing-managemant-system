package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.dto.request.ContractAddedEquipmentRequest;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDraftContractRequest {
    private String fullName;
    private String cccd;
    private String phoneNumber;
    private LocalDate moveInDate;
    private BigDecimal rentAmount;
    private BigDecimal deposit;
    private LocalDate endDate;
    private String equipmentSnapshot;

    /**
     * Legacy — không cần gửi. BE tự gắn toàn bộ nội thất ACTIVE của nhà/phòng.
     * Chỉ gửi nếu muốn giới hạn subset (không khuyến nghị).
     */
    private List<Long> selectedEquipmentIds;

    /** Thiết bị lắp thêm inline (chưa có trong DB). Gửi [] để xóa hết lắp thêm. */
    private List<ContractAddedEquipmentRequest> addedEquipments;

    /** ID thiết bị lắp thêm đã POST /equipments trước đó. Gửi [] để xóa hết lắp thêm. */
    private List<Long> addedEquipmentIds;

    /** @deprecated Không dùng — BE lấy hết nội thất nhà. */
    private List<Long> declinedEquipmentIds;
    private Integer depositMonths;
    private BigDecimal initialElectricReading;
    private BigDecimal initialWaterReading;
    private String electricMeterImageUrl;
    private String waterMeterImageUrl;
    /** Thời điểm chụp ảnh đồng hồ điện. Null khi có URL mới → BE ghi = lúc lưu. */
    private java.time.LocalDateTime electricMeterCapturedAt;
    /** Thời điểm chụp ảnh đồng hồ nước. Null khi có URL mới → BE ghi = lúc lưu. */
    private java.time.LocalDateTime waterMeterCapturedAt;
    /** @deprecated Ưu tiên {@link #roomConditionPhotos}. */
    private List<String> roomConditionUrls;
    /** Ảnh hiện trạng kèm thời điểm chụp — ưu tiên hơn roomConditionUrls. */
    private List<ContractEvidencePhotoRequest> roomConditionPhotos;
    private String roomConditionNote;
    private List<HouseholdMemberRequest> householdMembers;

    private LocalDate expectedReceptionDate;
    private String draftContractFileUrl;

    /** Ngày sinh khách chính. */
    private LocalDate dateOfBirth;

    /** Ngày cấp CCCD. */
    private LocalDate cccdIssueDate;

    /** Nơi cấp CCCD. */
    private String cccdIssuePlace;

    /** Hộ khẩu thường trú (HKTT). */
    private String permanentAddress;
}
