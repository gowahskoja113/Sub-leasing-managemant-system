package com.sep490.slms2026.dto.request;

import com.sep490.slms2026.enums.CostPaidBy;
import com.sep490.slms2026.enums.DamageCause;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MaintenanceCompleteRequest {
    /** Ghi chú sau khi sửa (optional). */
    private String resolutionNote;
    /** URL ảnh AFTER nếu đã upload sẵn; hoặc upload qua POST /photos?type=AFTER. */
    private List<String> afterImages;

    /** Ai chịu chi phí sửa chữa. Mặc định HOST nếu không gửi. */
    private CostPaidBy costPaidBy;
    /** Nguyên nhân hư hỏng — bắt buộc khi costPaidBy=TENANT. */
    private DamageCause cause;
    /**
     * Số tiền bồi thường (đã chốt). Bắt buộc &gt; 0 khi costPaidBy=TENANT.
     * FE gợi ý theo công thức khấu hao/penaltyFee; manager có thể sửa trước khi gửi.
     */
    private BigDecimal repairCost;
}
