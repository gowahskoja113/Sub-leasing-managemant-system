package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaveMeterReadingRequest {

    @NotNull(message = "propertyId không được để trống")
    private Long propertyId;

    /** null = nhà nguyên căn */
    private Long roomId;

    /** yyyy-MM */
    @NotBlank(message = "Kỳ chốt chỉ số không được để trống")
    private String period;

    /** ELECTRIC | ELECTRICITY | WATER */
    @NotBlank(message = "Loại tiện ích không được để trống")
    private String utilityType;

    @NotNull(message = "Chỉ số cũ không được để trống")
    private BigDecimal prevReading;

    @NotNull(message = "Chỉ số mới không được để trống")
    private BigDecimal newReading;

    private String meterImageUrl;

    private UUID overrideToken;

    private String overrideReason;
}
