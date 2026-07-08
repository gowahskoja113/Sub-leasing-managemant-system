package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.ViewingInterestType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ViewingLeadPropertyItemRequest {

    @NotNull(message = "propertyId không được để trống")
    private Long propertyId;

    @NotNull(message = "interestType không được để trống (WHOLE_HOUSE hoặc ROOM)")
    private ViewingInterestType interestType;

    /** Bắt buộc khi interestType = ROOM; phải null khi interestType = WHOLE_HOUSE */
    private Long roomId;

    private String note;
}
