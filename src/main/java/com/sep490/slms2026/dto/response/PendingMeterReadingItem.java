package com.sep490.slms2026.dto.response;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingMeterReadingItem {

    private Long propertyId;
    private String propertyName;
    private Long roomId;
    private String roomNumber;
    private Long contractId;
    private String utilityType;
    private String period;
    private int billingDay;
    private LocalDate meterDueDate;
    private boolean hasReading;
    private boolean hasPhoto;
}
