package com.sep490.slms2026.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutInspectionResponse {
    private Long id;
    private String roomConditionNote;
    private Integer electricityFinalReading;
    private String electricMeterImageUrl;
    private Integer waterFinalReading;
    private String waterMeterImageUrl;
    private List<String> photos;
    private List<DamageItemResponse> damages;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DamageItemResponse {
        private Long id;
        private Long equipmentId;
        private String label;
        private BigDecimal amount;
        private String note;
        private List<String> photos;
    }
}
