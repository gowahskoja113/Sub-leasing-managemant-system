package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Resolve hợp đồng ACTIVE của tenant khi 1 account có thể gắn nhiều HĐ.
 */
public final class TenantActiveContractResolver {

    private TenantActiveContractResolver() {
    }

    public static List<TenantContract> listActive(List<TenantContract> contracts) {
        if (contracts == null || contracts.isEmpty()) {
            return List.of();
        }
        return contracts.stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                .sorted(Comparator
                        .comparing(TenantContract::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(TenantContract::getId, Comparator.reverseOrder()))
                .toList();
    }

    /**
     * @param contractId optional — nếu null và có nhiều ACTIVE thì ném BusinessException
     *                   (trừ khi {@code allowPickLatestWhenAmbiguous}=true cho dashboard backward-compat)
     */
    public static TenantContract resolve(List<TenantContract> contracts, Long contractId,
                                         boolean allowPickLatestWhenAmbiguous) {
        List<TenantContract> active = listActive(contracts);
        if (active.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy hợp đồng đang hiệu lực");
        }
        if (contractId != null) {
            return active.stream()
                    .filter(c -> contractId.equals(c.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            "Hợp đồng không thuộc tài khoản của bạn hoặc không đang hiệu lực"));
        }
        if (active.size() == 1 || allowPickLatestWhenAmbiguous) {
            return active.get(0);
        }
        throw new BusinessException(
                "Bạn đang thuê nhiều nhà. Vui lòng chọn hợp đồng (truyền contractId)");
    }

    public static void assertOwnedByTenant(TenantContract contract, UUID tenantUserId) {
        if (contract.getTenant() == null || contract.getTenant().getUser() == null
                || !tenantUserId.equals(contract.getTenant().getUser().getId())) {
            throw new BusinessException("Hợp đồng không thuộc tài khoản của bạn");
        }
    }
}
