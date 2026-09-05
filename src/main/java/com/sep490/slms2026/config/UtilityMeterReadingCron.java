package com.sep490.slms2026.config;

import com.sep490.slms2026.service.UtilityBillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cron nhắc quản lý chụp đồng hồ nhà chia phòng trong ngày phát hành hoá đơn tổng.
 * 15:00 còn thiếu · 20:00 rủi ro lệch kỳ · 08:00 ngày sau leo thang admin/host.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UtilityMeterReadingCron {

    private final UtilityBillService utilityBillService;

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Ho_Chi_Minh")
    @Scheduled(cron = "0 0 18 * * *", zone = "Asia/Ho_Chi_Minh")
    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Ho_Chi_Minh")
    public void remindUtilityMeterReading() {
        int reminded = utilityBillService.remindUtilityMeterReading();
        if (reminded > 0) {
            log.info("UtilityMeterReadingCron: đã nhắc {} hoá đơn tiện ích chưa ghi đủ chỉ số phòng", reminded);
        }
    }
}
