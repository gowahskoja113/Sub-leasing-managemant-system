package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.InboundContract;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.exception.BusinessException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Ranh giới khai thác so với hợp đồng chủ nhà ({@link InboundContract}).
 * Dùng chung cho onboard / import / đón khách / kích hoạt nhà.
 */
public final class InboundLeaseRules {

    /** Cửa sổ bàn giao cuối kỳ master lease — trừ khỏi mẫu số định giá. */
    public static final int HANDOVER_BUFFER_MONTHS = 1;

    /** HĐ ngắn hơn mức này không trừ buffer (trừ 1 tháng trên 3 tháng là quá nặng). */
    public static final int HANDOVER_MIN_TERM_MONTHS = 6;

    /** Còn ít hơn bấy nhiêu tháng khai thác thì cảnh báo khi host kích hoạt. */
    public static final int SHORT_EXPLOITATION_WARNING_MONTHS = HANDOVER_MIN_TERM_MONTHS;

    public record RevenueWindow(
            LocalDate rentableFrom,
            int leaseMonths,
            int rentableMonths,
            int handoverBufferMonths,
            int revenueMonths
    ) {
    }

    /**
     * Thời hạn HĐ chủ nhà (ngày kết thúc tính trọn ngày). Không trừ buffer / tháng đã trôi.
     */
    public static int leaseMonths(InboundContract lease) {
        if (lease == null || lease.getStartDate() == null || lease.getEndDate() == null) {
            throw new BusinessException("Nhà chưa có hợp đồng với chủ nhà");
        }
        long months = ChronoUnit.MONTHS.between(lease.getStartDate(), lease.getEndDate().plusDays(1));
        if (months <= 0) {
            throw new BusinessException("Thời hạn hợp đồng phải ít nhất 1 tháng");
        }
        return (int) months;
    }

    public static LocalDate rentableFrom(InboundContract lease, Property property, LocalDate today) {
        LocalDate from = lease.getStartDate();
        if (property != null && property.getRenovationEndDate() != null
                && property.getRenovationEndDate().isAfter(from)) {
            from = property.getRenovationEndDate();
        }
        if (today != null && today.isAfter(from)) {
            from = today;
        }
        return from;
    }

    /**
     * Mẫu số chia vốn: tháng còn khai thác (sau cải tạo / chờ duyệt) trừ cửa sổ bàn giao.
     * {@code bufferOverride = null} → dùng {@link #HANDOVER_BUFFER_MONTHS}.
     */
    public static RevenueWindow resolveRevenueWindow(
            InboundContract lease, Property property, LocalDate today) {
        return resolveRevenueWindow(lease, property, today, null);
    }

    public static RevenueWindow resolveRevenueWindow(
            InboundContract lease, Property property, LocalDate today, Integer bufferOverride) {
        int leaseMonths = leaseMonths(lease);
        LocalDate rentableFrom = rentableFrom(lease, property, today);
        long rentableMonths = ChronoUnit.MONTHS.between(rentableFrom, lease.getEndDate().plusDays(1));
        if (rentableMonths <= 0) {
            throw new BusinessException(
                    "Hợp đồng với chủ nhà không còn tháng nào khai thác được — không thể định giá");
        }
        int wanted = bufferOverride != null ? bufferOverride : HANDOVER_BUFFER_MONTHS;
        if (wanted < 0) {
            wanted = 0;
        }
        int buffer = rentableMonths >= HANDOVER_MIN_TERM_MONTHS ? wanted : 0;
        int revenueMonths = Math.max(1, (int) rentableMonths - buffer);
        return new RevenueWindow(rentableFrom, leaseMonths, (int) rentableMonths, buffer, revenueMonths);
    }

    private InboundLeaseRules() {
    }

    public static void assertOccupancyWindow(LocalDate moveIn, LocalDate end, InboundContract lease) {
        String error = occupancyErrorOrNull(moveIn, end, lease);
        if (error != null) {
            throw new BusinessException(error);
        }
    }

    public static String occupancyErrorOrNull(LocalDate moveIn, LocalDate end, InboundContract lease) {
        if (lease == null) {
            return "Nhà chưa có hợp đồng với chủ nhà — không thể cho thuê";
        }
        if (moveIn != null && lease.getStartDate() != null && moveIn.isBefore(lease.getStartDate())) {
            return String.format(
                    "Ngày vào ở (%s) không được sớm hơn ngày hợp đồng với chủ nhà có hiệu lực (%s)",
                    moveIn, lease.getStartDate());
        }
        if (end != null && lease.getEndDate() != null && end.isAfter(lease.getEndDate())) {
            return String.format(
                    "Ngày kết thúc HĐ khách (%s) vượt quá hạn hợp đồng với chủ nhà (%s)",
                    end, lease.getEndDate());
        }
        return null;
    }

    public static void assertCanReceiveTenant(LocalDate today, InboundContract lease) {
        if (lease == null) {
            throw new BusinessException("Nhà chưa có hợp đồng với chủ nhà");
        }
        if (lease.getStartDate() != null && today.isBefore(lease.getStartDate())) {
            throw new BusinessException(String.format(
                    "Chưa thể đón khách: hợp đồng với chủ nhà có hiệu lực từ %s", lease.getStartDate()));
        }
    }

    /** Rule 5: kích hoạt căn đã hết HĐ chủ nhà. {@code endDate <= hôm nay}. */
    public static void assertLeaseNotExpired(LocalDate today, InboundContract lease) {
        if (lease != null && lease.getEndDate() != null && !lease.getEndDate().isAfter(today)) {
            throw new BusinessException(String.format(
                    "Không thể kích hoạt nhà: hợp đồng với chủ nhà đã hết hạn (ngày %s)", lease.getEndDate()));
        }
    }

    public static LocalDate clampMoveInToLease(LocalDate candidate, InboundContract lease) {
        if (candidate == null) {
            return null;
        }
        if (lease != null && lease.getStartDate() != null && candidate.isBefore(lease.getStartDate())) {
            return lease.getStartDate();
        }
        return candidate;
    }

    public static boolean isHandoverWindow(LocalDate tenantEnd, InboundContract lease) {
        if (tenantEnd == null || lease == null || lease.getEndDate() == null) {
            return false;
        }
        LocalDate windowStart = lease.getEndDate().minusMonths(HANDOVER_BUFFER_MONTHS);
        return !tenantEnd.isBefore(windowStart) && !tenantEnd.isAfter(lease.getEndDate());
    }

    public static boolean isShortExploitation(LocalDate today, InboundContract lease) {
        if (lease == null || lease.getEndDate() == null || today == null) {
            return false;
        }
        return ChronoUnit.MONTHS.between(today, lease.getEndDate()) < SHORT_EXPLOITATION_WARNING_MONTHS;
    }

    public static String handoverWindowMessage(LocalDate tenantEnd, InboundContract lease) {
        if (!isHandoverWindow(tenantEnd, lease)) {
            return null;
        }
        return String.format(
                "HĐ khách kết thúc %s, trong 1 tháng cuối của hợp đồng chủ nhà (hết hạn %s). Master lease có thể cần gia hạn.",
                tenantEnd, lease.getEndDate());
    }
}
