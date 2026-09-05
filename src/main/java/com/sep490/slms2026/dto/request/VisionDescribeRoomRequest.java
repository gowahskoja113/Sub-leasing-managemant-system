package com.sep490.slms2026.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VisionDescribeRoomRequest {

    @NotEmpty(message = "Cần ít nhất 1 ảnh hiện trạng")
    @Size(max = 8, message = "Tối đa 8 ảnh mỗi lần mô tả")
    private List<String> imageUrls;
}
