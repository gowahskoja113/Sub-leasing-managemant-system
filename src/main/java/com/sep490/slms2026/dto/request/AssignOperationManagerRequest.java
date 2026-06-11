package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignOperationManagerRequest {

    @NotNull
    private UUID operationManagerId;
}
