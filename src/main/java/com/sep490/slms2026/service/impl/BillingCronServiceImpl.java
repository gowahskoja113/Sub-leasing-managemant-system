package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.entity.Notification;
import com.sep490.slms2026.entity.TenantInvoice;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.TenantInvoiceType;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.enums.TenantInvoiceStatus;
import java.time.YearMonth;
import java.util.UUID;
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
import java.time.LocalDateTime;
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
    private final TenantContractRepository tenantContractRepository;

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

            if (invoice.getInvoiceType() == TenantInvoiceType.RENT) {
                String period = "tháng " + String.format("%02d/%d", invoice.getBillingMonth(), invoice.getBillingYear());
                String formattedAmount = formatCurrency(invoice.getGrandTotal());
                String formattedDueDate = invoice.getDueDate().format(formatter);
                
                if (daysUntilDue == 2) {
                    String title = "⏰ Còn 2 ngày tới hạn đóng tiền phòng";
                    String content = String.format("Tiền phòng %s %sđ chưa được thanh toán. Hạn cuối là %s. Thanh toán sớm để không bị ghi nhận quá hạn.", period, formattedAmount, formattedDueDate);
                    sendNotification(invoice, "BILLING_REMINDER", title, content);
                    invoice.setLastReminderDate(today);
                    stateChanged = true;
                    reminded++;
                } else if (daysUntilDue == 0) {
                    String title = "⚠️ Hôm nay là hạn cuối đóng tiền phòng";
                    String content = String.format("Tiền phòng %s %sđ đến hạn hôm nay (%s). Vui lòng thanh toán trong hôm nay; sau hôm nay hoá đơn sẽ bị ghi nhận quá hạn.", period, formattedAmount, formattedDueDate);
                    sendNotification(invoice, "BILLING_REMINDER", title, content);
                    invoice.setLastReminderDate(today);
                    stateChanged = true;
                    reminded++;
                } else if (daysUntilDue < 0) {
                    if (invoice.getStatus() != TenantInvoiceStatus.OVERDUE) {
                        invoice.setStatus(TenantInvoiceStatus.OVERDUE);
                        String title = "Hóa đơn tiền phòng quá hạn";
                        String content = String.format("Tiền phòng %s %sđ đã quá hạn thanh toán. Vui lòng thanh toán ngay.", period, formattedAmount);
                        sendNotification(invoice, "BILLING_OVERDUE", title, content);
                        invoice.setLastReminderDate(today);
                        stateChanged = true;
                        overdueMarked++;
                    } else {
                        // Already overdue, check for 10 days overdue
                        long overdueDays = ChronoUnit.DAYS.between(invoice.getDueDate(), today);
                        if (overdueDays == 10 && !Boolean.TRUE.equals(invoice.getTenantContract().getTerminationProposed())) {
                            // Notify manager
                            UUID managerId = null;
                            if (invoice.getTenantContract().getProperty().getManagedBy() != null) {
                                managerId = invoice.getTenantContract().getProperty().getManagedBy();
                            } else if (invoice.getTenantContract().getProperty().getOperationManagerId() != null) {
                                managerId = invoice.getTenantContract().getProperty().getOperationManagerId();
                            }
                            if (managerId != null) {
                                String tenantName = invoice.getTenantContract().getTenant().getUser().getFullName();
                                String roomStr = invoice.getTenantContract().getRoom() != null ? invoice.getTenantContract().getRoom().getRoomNumber() : "Nguyên căn";
                                String title = "⛔ Quá hạn tiền phòng 10 ngày — được quyền chấm dứt hợp đồng";
                                String content = String.format("%s · Phòng %s chưa thanh toán tiền phòng %s (%sđ). Hợp đồng đã bị gắn cờ đề nghị chấm dứt.", tenantName, roomStr, period, formattedAmount);
                                sendPushNotificationOnly(managerId, title, content, "RENT_OVERDUE_MANAGER", "RentInvoice");
                            }
                            
                            // Notify all hosts (ROLE_OWNER)
                            List<com.sep490.slms2026.entity.User> hosts = userRepository.findByRoleAndStatus(com.sep490.slms2026.enums.Role.ROLE_OWNER, com.sep490.slms2026.enums.UserStatus.ACTIVE);
                            for (com.sep490.slms2026.entity.User host : hosts) {
                                String tenantName = invoice.getTenantContract().getTenant().getUser().getFullName();
                                String roomStr = invoice.getTenantContract().getRoom() != null ? invoice.getTenantContract().getRoom().getRoomNumber() : "nguyên căn";
                                String propertyName = invoice.getTenantContract().getProperty().getPropertyName();
                                String title = "⛔ Khách thuê quá hạn tiền phòng 10 ngày";
                                String content = String.format("Khách %s (Phòng %s, nhà %s) quá hạn thanh toán tiền phòng %s. Quản lý đã nhận được thông báo đề nghị chấm dứt hợp đồng.", 
                                        tenantName, roomStr, propertyName, period);
                                sendPushNotificationOnly(host.getId(), title, content, "RENT_OVERDUE_HOST", "RentInvoice");
                            }
                            
                            invoice.getTenantContract().setTerminationProposed(true);
                            tenantContractRepository.save(invoice.getTenantContract());
                            
                            invoice.setLastReminderDate(today);
                            stateChanged = true;
                        }
                    }
                }
            } else {
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
        String screen = "InvoiceList";
        Map<String, Object> params = Map.of("invoiceId", invoice.getId());
        String paramsJson = null;
        try {
            paramsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(params);
        } catch (Exception e) {}

        Notification notification = Notification.builder()
                .userId(invoice.getTenantUserId())
                .title(title)
                .content(content)
                .type(type)
                .screen(screen)
                .paramsJson(paramsJson)
                .read(false)
                .build();
        notificationRepository.save(notification);

        Optional<User> userOpt = userRepository.findById(invoice.getTenantUserId());
        if (userOpt.isPresent() && userOpt.get().getPushToken() != null) {
            Map<String, Object> pushData = new HashMap<>(params);
            pushData.put("screen", screen);
            pushNotificationService.sendPushNotification(
                    userOpt.get().getPushToken(), 
                    title, 
                    content, 
                    pushData
            );
        }
    }

    private void sendPushNotificationOnly(UUID userId, String title, String content, String type, String screen) {
        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .content(content)
                .type(type)
                .screen(screen)
                .read(false)
                .build();
        notificationRepository.save(notification);

        userRepository.findById(userId).ifPresent(u -> {
            if (u.getPushToken() != null && !u.getPushToken().isBlank()) {
                pushNotificationService.sendPushNotification(u.getPushToken(), title, content, Map.of("screen", screen));
            }
        });
    }

    @Scheduled(cron = "0 5 0 1 * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void generateMonthlyRentInvoices() {
        log.info("Starting generateMonthlyRentInvoices...");
        List<TenantContract> activeContracts = tenantContractRepository.findByStatus(ContractStatus.ACTIVE);
        YearMonth currentMonth = YearMonth.now();
        LocalDate dueDate = currentMonth.atDay(5);
        
        for (TenantContract contract : activeContracts) {
            try {
                var existing = tenantInvoiceRepository.findByTenantContractIdAndInvoiceTypeAndBillingYearAndBillingMonth(
                    contract.getId(), TenantInvoiceType.RENT, currentMonth.getYear(), currentMonth.getMonthValue());
                if (existing.isPresent()) continue;

                BigDecimal amount = contract.getRentAmount();
                if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) continue;

                LocalDate billStart = contract.getStartDate();
                YearMonth prevMonth = currentMonth.minusMonths(1);
                if (billStart.getYear() == prevMonth.getYear() && billStart.getMonthValue() == prevMonth.getMonthValue()) {
                    long prevDays = java.time.temporal.ChronoUnit.DAYS.between(billStart, prevMonth.atEndOfMonth()) + 1;
                    if (prevDays <= 3) {
                        BigDecimal prevAmount = contract.getRentAmount()
                            .multiply(BigDecimal.valueOf(prevDays))
                            .divide(BigDecimal.valueOf(prevMonth.lengthOfMonth()), 0, java.math.RoundingMode.HALF_UP);
                        amount = amount.add(prevAmount);
                    }
                }

                TenantInvoice invoice = tenantInvoiceRepository.save(TenantInvoice.builder()
                        .code("HD-RENT-" + contract.getId() + "-" + currentMonth)
                        .tenantUserId(contract.getTenant().getId())
                        .tenantContract(contract)
                        .invoiceType(TenantInvoiceType.RENT)
                        .propertyName(contract.getProperty().getPropertyName())
                        .roomNumber(contract.getRoom() != null ? contract.getRoom().getRoomNumber() : contract.getProperty().getPropertyName())
                        .billingMonth(currentMonth.getMonthValue())
                        .billingYear(currentMonth.getYear())
                        .billingPeriod("Tiền nhà tháng " + String.format("%02d/%d", currentMonth.getMonthValue(), currentMonth.getYear()))
                        .totalAmount(amount)
                        .lateFee(BigDecimal.ZERO)
                        .grandTotal(amount)
                        .status(TenantInvoiceStatus.PENDING)
                        .dueDate(dueDate)
                        .createdAt(LocalDateTime.now())
                        .autoIssued(true)
                        .build());
                
                String period = "tháng " + String.format("%02d/%d", currentMonth.getMonthValue(), currentMonth.getYear());
                String title = "🧾 Hoá đơn tiền phòng đã có — cần thanh toán";
                String content = String.format("Tiền phòng %s %sđ vừa được phát hành. Hạn thanh toán %s. Mở app để thanh toán ngay, tránh để quá hạn.",
                        period, formatCurrency(amount), dueDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                sendNotification(invoice, "RENT_ISSUED", title, content);
            } catch (Exception e) {
                log.error("Error generating monthly rent invoice for contract {}", contract.getId(), e);
            }
        }
    }

    private BigDecimal calculateProratedRent(TenantContract contract, YearMonth billingMonth) {
        LocalDate startOfMonth = billingMonth.atDay(1);
        LocalDate endOfMonth = billingMonth.atEndOfMonth();
        LocalDate billStart = contract.getStartDate().isAfter(startOfMonth) ? contract.getStartDate() : startOfMonth;
        LocalDate billEnd = contract.getEndDate() != null && contract.getEndDate().isBefore(endOfMonth) ? contract.getEndDate() : endOfMonth;

        if (billStart.isAfter(billEnd)) {
            return BigDecimal.ZERO;
        }

        long days = ChronoUnit.DAYS.between(billStart, billEnd) + 1;
        long daysInMonth = billingMonth.lengthOfMonth();
        BigDecimal amount = contract.getRentAmount();
        if (days < daysInMonth) {
            amount = amount.multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(daysInMonth), 0, RoundingMode.HALF_UP);
        }
        return amount;
    }

    @Scheduled(cron = "0 10 0 28 * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void remindUpcomingRentOn28th() {
        log.info("Starting remindUpcomingRentOn28th...");
        List<TenantContract> activeContracts = tenantContractRepository.findByStatus(ContractStatus.ACTIVE);
        YearMonth nextMonth = YearMonth.now().plusMonths(1);
        String period = "tháng " + String.format("%02d/%d", nextMonth.getMonthValue(), nextMonth.getYear());
        
        for (TenantContract contract : activeContracts) {
            BigDecimal amount = calculateProratedRent(contract, nextMonth);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) continue;
            
            String title = "🔔 Nhắc trước: ngày 1 tới hạn đóng tiền phòng";
            String content = String.format("Tiền phòng %s %sđ sẽ được phát hành vào ngày 1, hạn thanh toán chậm nhất ngày 5. Bạn chuẩn bị trước giúp nhé.",
                    period, formatCurrency(amount));
            sendPushNotificationOnly(contract.getTenant().getId(), title, content, "RENT_REMINDER_PRE", "InvoiceList");
        }
    }
}
