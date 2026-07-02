package com.sep490.slms2026.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HouseholdMemberResponse {
    private Long id;
    private String fullName;
    private String relation;
    private String phone;
    private LocalDate dateOfBirth;
    private String cccd;
}
