package com.sep490.slms2026.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingConfigResponse {

    private int reminderLeadDays;
    private int graceDays;
    private int meterReminderLeadDays;
    private LocalDateTime updatedAt;
}
