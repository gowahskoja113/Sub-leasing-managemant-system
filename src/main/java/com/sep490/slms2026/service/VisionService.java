package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.VisionDescribeRoomResponse;
import com.sep490.slms2026.dto.response.VisionLabelsResponse;

import java.util.List;

public interface VisionService {

    /**
     * Gán nhãn vật thể trên ảnh (Cloudinary URL) để FE đối chiếu với thiết bị báo hỏng.
     */
    VisionLabelsResponse detectLabels(String imageUrl);

    /**
     * Mô tả hiện trạng phòng từ một bộ ảnh (biên bản đón khách). Quota riêng với /labels.
     */
    VisionDescribeRoomResponse describeRoom(List<String> imageUrls);
}
