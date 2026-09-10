package com.sep490.slms2026.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedMeterReadingItem {

    /** null = nhà nguyên căn */
    private Long roomId;
    private String roomNumber;
    private Long contractId;
    private String tenantName;
    private BigDecimal prevReading;
    /** LAST_INVOICE | LAST_READING | HANDOVER */
    private String prevSource;
    /** null = chưa chốt kỳ này */
    private BigDecimal newReading;
    private String meterImageUrl;
    /** ISO-8601 offset, ví dụ 2026-09-30T17:20:11+07:00 */
    private String capturedAt;
    /** null = chưa phát hành */
    private Long invoiceId;
    private String invoiceStatus;
}
