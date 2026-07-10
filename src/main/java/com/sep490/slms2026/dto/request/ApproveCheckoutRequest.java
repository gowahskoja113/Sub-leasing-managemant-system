package com.sep490.slms2026.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApproveCheckoutRequest {

    /** Ghi chú nội bộ manager khi duyệt. */
    private String managerNote;
}
