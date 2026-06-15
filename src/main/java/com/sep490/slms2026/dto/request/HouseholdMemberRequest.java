package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HouseholdMemberRequest {

    @NotBlank(message = "Tên thành viên không được để trống")
    private String fullName;

    private String relation;
    private String phone;
    private LocalDate dateOfBirth;
    private String cccd;
}
