package com.sep490.slms2026.dto.response;

import com.sep490.slms2026.enums.PricingScope;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepreciationCalculationResponse {

    private Long propertyId;
    private PricingScope pricingScope;

    // WHOLE_HOUSE: chỉ có 1 phần tử hoặc dùng wholeHouseResult
    private DepreciationResultResponse wholeHouseResult;

    // ROOM: danh sách khấu hao từng phòng
    private List<DepreciationResultResponse> roomResults;
}
