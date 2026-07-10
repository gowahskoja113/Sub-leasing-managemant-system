package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.ContractStatus;

import java.time.LocalDate;

/**
 * Trạng thái hiệu lực hợp đồng thuê: ACTIVE + chưa quá endDate = còn hiệu lực.
 */
public final class TenantContractStatusHelper {

    private TenantContractStatusHelper() {
    }

    /** Còn hiệu lực nếu status ACTIVE và (không có endDate hoặc endDate >= hôm nay). */
    public static boolean isEffective(ContractStatus status, LocalDate endDate) {
        if (status != ContractStatus.ACTIVE) {
            return false;
        }
        if (endDate == null) {
            return true;
        }
        return !endDate.isBefore(LocalDate.now());
    }

    /** Nhãn hiển thị cho FE. */
    public static String effectiveLabel(ContractStatus status, LocalDate endDate) {
        return isEffective(status, endDate) ? "Còn hiệu lực" : "Không còn hiệu lực";
    }

    /**
     * Tự chuyển ACTIVE → EXPIRED khi đã quá endDate (chỉ đổi status trên entity).
     * Caller cần save entity và gọi {@link com.sep490.slms2026.service.ContractEquipmentService#restoreDisabledByContract}
     * hoặc {@link com.sep490.slms2026.service.TenantOnboardingService#syncExpiredIfNeeded} để khôi phục thiết bị.
     *
     * @return true nếu entity bị đổi status (caller nên save).
     */
    public static boolean syncExpiredIfNeeded(TenantContract contract) {
        if (contract.getStatus() == ContractStatus.ACTIVE
                && contract.getEndDate() != null
                && contract.getEndDate().isBefore(LocalDate.now())) {
            contract.setStatus(ContractStatus.EXPIRED);
            return true;
        }
        return false;
    }
}
