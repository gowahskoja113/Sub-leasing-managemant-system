package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class AssignContractManagerRequest {

    /** User ID của ROLE_MANAGER được gán đón khách / nhận thông báo HĐ. */
    @NotNull(message = "managerId là bắt buộc")
    private UUID managerId;
}
