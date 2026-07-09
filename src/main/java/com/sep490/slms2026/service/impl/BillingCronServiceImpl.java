package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.entity.Notification;
import com.sep490.slms2026.entity.TenantInvoice;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.TenantInvoiceStatus;
import com.sep490.slms2026.repository.NotificationRepository;
import com.sep490.slms2026.repository.TenantInvoiceRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.BillingCronService;
import com.sep490.slms2026.service.PushNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingCronServiceImpl implements BillingCronService {

    private final TenantInvoiceRepository tenantInvoiceRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final PushNotificationService pushNotificationService;

    @Value("${billing.reminder-days-before:3}")
    private int reminderDaysBefore;

    @Value("${billing.late-fee-percent:2}")
    private int lateFeePercent;

    @Value("${billing.overdue-renotify-days:7}")
    private int overdueRenotifyDays;

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void dailyBillingSweepTask() {
        log.info("Starting daily billing sweep CRON job...");
        Map<String, Integer> result = runDailySweep();
        log.info("Finished daily billing sweep CRON job. Results: {}", result);
    }

    @Override
    @Transactional
    public Map<String, Integer> runDailySweep() {
        int reminded = 0;
        int overdueMarked = 0;
        int renotified = 0;

        LocalDate today = LocalDate.now();
        List<TenantInvoiceStatus> statuses = List.of(
                TenantInvoiceStatus.PENDING,
                TenantInvoiceStatus.PARTIAL,
                TenantInvoiceStatus.OVERDUE
        );
        List<TenantInvoice> invoices = tenantInvoiceRepository.findByStatusInAndDueDateIsNotNull(statuses);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        for (TenantInvoice invoice : invoices) {
            // Check idempotency: if we already reminded today, skip
            if (today.equals(invoice.getLastReminderDate())) {
                continue;
            }

            long daysUntilDue = ChronoUnit.DAYS.between(today, invoice.getDueDate());
            boolean stateChanged = false;

            if (invoice.getStatus() == TenantInvoiceStatus.OVERDUE) {
                // Case C: Renotify every 7 days
                if (invoice.getLastReminderDate() == null ||
                        ChronoUnit.DAYS.between(invoice.getLastReminderDate(), today) >= overdueRenotifyDays) {
                    
                    long overdueDays = ChronoUnit.DAYS.between(invoice.getDueDate(), today);
                    String title = "Hóa đơn quá hạn";
                    String content = String.format("Hóa đơn %s đã quá hạn %d ngày. Tổng phải trả %sđ. Vui lòng thanh toán ngay. (#%d)",
                            invoice.getCode(), overdueDays, formatCurrency(invoice.getGrandTotal()), invoice.getId());
                    
                    sendNotification(invoice, "BILLING_OVERDUE", title, content);
                    invoice.setLastReminderDate(today);
                    stateChanged = true;
                    renotified++;
                }
            } else { // PENDING or PARTIAL
                if (daysUntilDue < 0) {
                    // Case B: Overdue
                    invoice.setStatus(TenantInvoiceStatus.OVERDUE);
                    
                    // Apply late fee if not applied yet
                    if (invoice.getLateFee() == null || invoice.getLateFee().compareTo(BigDecimal.ZERO) == 0) {
                        BigDecimal lateFee = invoice.getTotalAmount()
                                .multiply(BigDecimal.valueOf(lateFeePercent))
                                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
                        
                        // Round to thousands (nghìn đồng)
                        long lateFeeLong = (lateFee.longValue() / 1000) * 1000;
                        BigDecimal roundedLateFee = BigDecimal.valueOf(lateFeeLong);
                        
                        invoice.setLateFee(roundedLateFee);
                        invoice.setGrandTotal(invoice.getTotalAmount().add(roundedLateFee));
                        
                        // Clear PayOS fields to force generating new QR with updated grandTotal
                        invoice.setPayosOrderCode(null);
                        invoice.setPayosCheckoutUrl(null);
                        invoice.setPayosQrCode(null);
                    }
                    
                    long overdueDays = -daysUntilDue;
                    String title = "Hóa đơn quá hạn";
                    String content = String.format("Hóa đơn %s đã quá hạn %d ngày. Phí trễ hạn %sđ đã được cộng, tổng phải trả %sđ. (#%d)",
                            invoice.getCode(), overdueDays, formatCurrency(invoice.getLateFee()), formatCurrency(invoice.getGrandTotal()), invoice.getId());
                    
                    sendNotification(invoice, "BILLING_OVERDUE", title, content);
                    invoice.setLastReminderDate(today);
                    stateChanged = true;
                    overdueMarked++;
                } else if (daysUntilDue == reminderDaysBefore || daysUntilDue == 0) {
                    // Case A: Reminder before due date or on due date
                    String title = "Hóa đơn sắp đến hạn";
                    String period = invoice.getBillingPeriod() != null ? invoice.getBillingPeriod() : "";
                    String content = String.format("Hóa đơn %s (%s) %sđ đến hạn ngày %s. Vui lòng thanh toán đúng hạn. (#%d)",
                            invoice.getCode(), period, formatCurrency(invoice.getGrandTotal()), invoice.getDueDate().format(formatter), invoice.getId());
                    
                    sendNotification(invoice, "BILLING_REMINDER", title, content);
                    invoice.setLastReminderDate(today);
                    stateChanged = true;
                    reminded++;
                }
            }
            
            if (stateChanged) {
                tenantInvoiceRepository.save(invoice);
            }
        }

        Map<String, Integer> stats = new HashMap<>();
        stats.put("reminded", reminded);
        stats.put("overdueMarked", overdueMarked);
        stats.put("renotified", renotified);
        return stats;
    }
    
    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0";
        return String.format("%,d", amount.longValue()).replace(',', '.');
    }

    private void sendNotification(TenantInvoice invoice, String type, String title, String content) {
        Notification notification = Notification.builder()
                .userId(invoice.getTenantUserId())
                .title(title)
                .content(content)
                .type(type)
                .read(false)
                .build();
        notificationRepository.save(notification);

        Optional<User> userOpt = userRepository.findById(invoice.getTenantUserId());
        if (userOpt.isPresent() && userOpt.get().getPushToken() != null) {
            pushNotificationService.sendPushNotification(
                    userOpt.get().getPushToken(), 
                    title, 
                    content, 
                    Map.of("invoiceId", invoice.getId())
            );
        }
    }
}
