package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.RoomStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateRoomStatusRequest {

    @NotNull
    private RoomStatus status;
}
