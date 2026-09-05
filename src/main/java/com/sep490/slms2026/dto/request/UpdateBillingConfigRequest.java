package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateBillingConfigRequest {

    @NotNull
    @Min(0)
    @Max(14)
    private Integer reminderLeadDays;

    @NotNull
    @Min(0)
    @Max(14)
    private Integer graceDays;

    @Min(0)
    @Max(7)
    private Integer meterReminderLeadDays;
}
