package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.dto.request.ContractAddedEquipmentRequest;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

    /** Thiết bị có sẵn khách nhận. Gửi [] nếu không nhận món nào. */
    private List<Long> selectedEquipmentIds;

    /** Thiết bị lắp thêm inline (chưa có trong DB). Gửi [] để xóa hết lắp thêm. */
    private List<ContractAddedEquipmentRequest> addedEquipments;

    /** ID thiết bị lắp thêm đã POST /equipments trước đó. Gửi [] để xóa hết lắp thêm. */
    private List<Long> addedEquipmentIds;

    /** @deprecated Dùng {@link #selectedEquipmentIds}. */
    private List<Long> declinedEquipmentIds;
    private Integer depositMonths;
    private BigDecimal initialElectricReading;
    private BigDecimal initialWaterReading;
    private String electricMeterImageUrl;
    private String waterMeterImageUrl;
    private List<String> roomConditionUrls;
    private String roomConditionNote;
    private List<HouseholdMemberRequest> householdMembers;
    
    private UUID assignedManagerId;
    private LocalDate expectedReceptionDate;
    private String draftContractFileUrl;

    /** Ngày sinh khách chính. */
    private LocalDate dateOfBirth;
}
