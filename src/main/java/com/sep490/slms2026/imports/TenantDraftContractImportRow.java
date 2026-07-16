package com.sep490.slms2026.imports;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class TenantDraftContractImportRow {

    private int rowNumber;

    /** Mã HĐ inbound (đợt 1) — ví dụ HD-MTX-01-NORENO-NOFURN */
    private String inboundContractCode;

    /** ID BĐS (nếu không dùng mã HĐ inbound) */
    private Long propertyId;

    /** Tên tòa nhà (fallback, có thể trùng) */
    private String propertyName;

    /** NGUYEN_CAN / THEO_PHONG / PHONG / trống */
    private String rentTypeRaw;

    /** Số phòng — bắt buộc khi thuê theo phòng */
    private String roomNumber;

    private String fullName;
    private String cccd;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private LocalDate cccdIssueDate;
    private String cccdIssuePlace;
    private String permanentAddress;

    private LocalDate moveInDate;
    private LocalDate endDate;
    private BigDecimal rentAmount;
    private Integer depositMonths;
    private BigDecimal deposit;
    private LocalDate expectedReceptionDate;

    /** SĐT hoặc UUID của manager đón khách */
    private String assignedManagerRaw;
}
