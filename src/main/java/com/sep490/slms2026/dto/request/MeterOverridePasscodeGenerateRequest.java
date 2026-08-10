package com.sep490.slms2026.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeterOverridePasscodeGenerateRequest {

    /** TTL phút cho mã này; null → dùng config mặc định. */
    private Integer ttlMinutes;

    /** Ghi chú nội bộ (vd. "Manager An — đón khách P.302"). */
    private String note;
}
