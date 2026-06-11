package com.sep490.slms2026.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignOperationManagerRequest {

    /** UUID string trong JSON — cùng giá trị với field `id` từ GET /api/v1/user/managers */
    @NotNull
    @JsonAlias({"id", "managerId", "userId"})
    private UUID operationManagerId;
}
