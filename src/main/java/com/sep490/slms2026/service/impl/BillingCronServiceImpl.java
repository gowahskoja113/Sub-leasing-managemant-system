package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.entity.MeterReading;
import com.sep490.slms2026.entity.Notification;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.TenantInvoice;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.RentCycleType;
import com.sep490.slms2026.enums.TenantInvoiceStatus;
import com.sep490.slms2026.enums.TenantInvoiceType;
import com.sep490.slms2026.enums.UtilityType;
import java.time.YearMonth;
import java.util.UUID;
import com.sep490.slms2026.repository.HostNotificationRepository;
import com.sep490.slms2026.repository.MeterReadingRepository;
import com.sep490.slms2026.repository.NotificationRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.TenantInvoiceRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.service.BillingCronService;
import com.sep490.slms2026.service.InvoiceDisputeService;
import com.sep490.slms2026.service.UnitPriceService;
import com.sep490.slms2026.service.UserPushTokenService;
import com.sep490.slms2026.util.ContractBillingCalendar;
import com.sep490.slms2026.util.RentFirstCycleCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingCronServiceImpl implements BillingCronService {

    private final TenantInvoiceRepository tenantInvoiceRepository;
    private final NotificationRepository notificationRepository;
    private final HostNotificationRepository hostNotificationRepository;
    private final UserPushTokenService userPushTokenService;
    private final TenantContractRepository tenantContractRepository;
    private final UserRepository userRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final UnitPriceService unitPriceService;
    private final InvoiceDisputeService invoiceDisputeService;

    @Value("${billing.rent.reminder-lead-days:3}")
    private int rentReminderLeadDaysValue;

    @Value("${billing.meter.reminder-lead-days:1}")
    private int meterReminderLeadDaysValue;

    @Value("${billing.reminder-days-before:3}")
    private int reminderDaysBefore;

    @Value("${billing.late-fee-percent:2}")
    private int lateFeePercent;

    @Value("${billing.overdue-renotify-days:7}")
    private int overdueRenotifyDays;

    @Value("${billing.rent.due-day:5}")
    private int rentDueDay;

    @Value("${billing.rent.final-reminder-day:7}")
    private int finalReminderDay;

    @Value("${billing.rent.termination-after-days:3}")
    private int terminationAfterDays;

    @Value("${billing.rent.issue-reminder-lead-days:2}")
    private int issueReminderLeadDays;

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ISO_LOCAL_DATE;

    private LocalDate todayVn() {
        return LocalDate.now(VN);
    }

    /**
     * Catch-up khi restart / đổi giờ VPS: cron 00:05 và 08:00 không chạy lại nếu app start sau mốc đó.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void runSweepOnStartup() {
        log.info("Startup billing sweep (catch-up after restart / clock change)...");
        Map<String, Integer> result = runDailySweep();
        log.info("Startup billing sweep done: {}", result);
    }

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

        LocalDate today = todayVn();
        int rentReminderLeadDays = rentReminderLeadDaysValue;
        long finalOverdueDaysThreshold = (long) finalReminderDay - rentDueDay;
        if (finalOverdueDaysThreshold <= 0) {
            log.warn("Cấu hình vô lý: billing.rent.final-reminder-day ({}) <= billing.rent.due-day ({}) — nhắc lần cuối có thể bị bỏ",
                    finalReminderDay, rentDueDay);
        }

        int escalated = unitPriceService.applyDueEscalations();
        int escalationNotices = unitPriceService.notifyUpcomingAnnualEscalations();
        int issued = generateDueRentInvoices(today);
        int meterReminded = remindPendingMeterReadings(today);
        int issueReminded = remindUpcomingRent(today);

        List<TenantInvoiceStatus> statuses = List.of(
                TenantInvoiceStatus.PENDING,
                TenantInvoiceStatus.PARTIAL,
                TenantInvoiceStatus.OVERDUE
        );
        List<TenantInvoice> invoices = tenantInvoiceRepository.findByStatusInAndDueDateIsNotNull(statuses);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        Set<Long> frozenInvoiceIds = invoiceDisputeService.openDisputeTenantInvoiceIds();
        // Gộp tin quản lý ngày 5–7: 1 tin/manager/ngày (không spam, không lộ số tiền)
        Map<UUID, List<ManagerUnpaidLine>> managerUnpaidDigest = new LinkedHashMap<>();

        for (TenantInvoice invoice : invoices) {
            if (frozenInvoiceIds.contains(invoice.getId())) {
                continue;
            }
            // Check idempotency: if we already reminded today, skip
            if (today.equals(invoice.getLastReminderDate())) {
                continue;
            }

            // FIRST đã thu trong HD-ONBOARD — huỷ bản PENDING/OVERDUE trùng, không nhắc / không đề nghị chấm dứt.
            if (invoice.getCycleType() == RentCycleType.FIRST
                    && invoice.getInvoiceType() == TenantInvoiceType.RENT
                    && firstRentAlreadyCollectedViaOnboard(invoice)) {
                invoice.setStatus(TenantInvoiceStatus.CANCELLED);
                tenantInvoiceRepository.save(invoice);
                log.info("Huỷ hoá đơn FIRST trùng QR onboard: {}", invoice.getCode());
                continue;
            }

            long daysUntilDue = ChronoUnit.DAYS.between(today, invoice.getDueDate());
            boolean stateChanged = false;

            if (invoice.getInvoiceType() == TenantInvoiceType.RENT) {
                String period = "tháng " + String.format("%02d/%d", invoice.getBillingMonth(), invoice.getBillingYear());
                String formattedAmount = formatCurrency(invoice.getGrandTotal());
                String formattedDueDate = invoice.getDueDate().format(formatter);
                
                if (daysUntilDue > 0 && daysUntilDue <= rentReminderLeadDays) {
                    sendNotification(invoice, "BILLING_REMINDER",
                        String.format("⏰ Còn %d ngày tới hạn đóng tiền phòng", daysUntilDue),
                        String.format("Tiền phòng %s %sđ chưa được thanh toán. Hạn cuối là %s. Thanh toán sớm để không bị ghi nhận quá hạn.",
                            period, formattedAmount, formattedDueDate));
                    invoice.setLastReminderDate(today);
                    stateChanged = true;
                    reminded++;
                } else if (daysUntilDue == 0) {
                    sendNotification(invoice, "BILLING_REMINDER",
                        "⚠️ Hôm nay là hạn cuối đóng tiền phòng",
                        String.format("Tiền phòng %s %sđ đến hạn hôm nay (%s). Vui lòng thanh toán trong hôm nay; sau hôm nay hoá đơn sẽ bị ghi nhận quá hạn.",
                            period, formattedAmount, formattedDueDate));
                    collectManagerUnpaid(managerUnpaidDigest, invoice, period, 0);
                    invoice.setLastReminderDate(today);
                    stateChanged = true;
                    reminded++;
                } else if (daysUntilDue < 0) {
                    long overdueDays = ChronoUnit.DAYS.between(invoice.getDueDate(), today);

                    if (invoice.getStatus() != TenantInvoiceStatus.OVERDUE) {
                        invoice.setStatus(TenantInvoiceStatus.OVERDUE);
                        if (overdueDays >= terminationAfterDays
                                && !Boolean.TRUE.equals(invoice.getTenantContract().getTerminationProposed())) {
                            sendNotification(invoice, "BILLING_OVERDUE",
                                "🔴 Hợp đồng có thể bị chấm dứt",
                                String.format("Tiền phòng %s %sđ đã quá hạn %d ngày. Quản lý đã được quyền đề nghị chấm dứt hợp đồng. Thanh toán ngay để giữ hợp đồng.",
                                    period, formattedAmount, overdueDays));
                            notifyManagerAndHostsOverdue(invoice, period, formattedAmount, overdueDays);
                            invoice.getTenantContract().setTerminationProposed(true);
                            tenantContractRepository.save(invoice.getTenantContract());
                        } else if (finalOverdueDaysThreshold > 0
                                && overdueDays >= finalOverdueDaysThreshold) {
                            sendNotification(invoice, "BILLING_OVERDUE",
                                "🔴 Nhắc lần cuối — tiền phòng quá hạn",
                                String.format("Tiền phòng %s %sđ đã quá hạn %d ngày. Từ ngày mai, quản lý được quyền chấm dứt hợp đồng vì không thanh toán. Vui lòng thanh toán ngay hôm nay.",
                                    period, formattedAmount, overdueDays));
                            collectManagerUnpaid(managerUnpaidDigest, invoice, period, overdueDays);
                        } else {
                            sendNotification(invoice, "BILLING_OVERDUE",
                                "Hóa đơn tiền phòng quá hạn",
                                String.format("Tiền phòng %s %sđ đã quá hạn thanh toán. Vui lòng thanh toán ngay.",
                                    period, formattedAmount));
                            collectManagerUnpaid(managerUnpaidDigest, invoice, period, overdueDays);
                        }
                        invoice.setLastReminderDate(today);
                        stateChanged = true;
                        overdueMarked++;
                    } else if (overdueDays > 0
                            && overdueDays < finalOverdueDaysThreshold
                            && overdueDays < terminationAfterDays) {
                        // Ngày quá hạn trước mốc nhắc lần cuối (vd. ngày 6) khi đã OVERDUE
                        sendNotification(invoice, "BILLING_OVERDUE",
                            "🔴 Tiền phòng quá hạn",
                            String.format("Tiền phòng %s %sđ đã quá hạn %d ngày. Vui lòng thanh toán ngay.",
                                period, formattedAmount, overdueDays));
                        collectManagerUnpaid(managerUnpaidDigest, invoice, period, overdueDays);
                        invoice.setLastReminderDate(today);
                        stateChanged = true;
                        reminded++;
                    } else if (finalOverdueDaysThreshold > 0
                            && overdueDays >= finalOverdueDaysThreshold
                            && overdueDays < terminationAfterDays) {
                        sendNotification(invoice, "BILLING_OVERDUE",
                            "🔴 Nhắc lần cuối — tiền phòng quá hạn",
                            String.format("Tiền phòng %s %sđ đã quá hạn %d ngày. Từ ngày mai, quản lý được quyền chấm dứt hợp đồng vì không thanh toán. Vui lòng thanh toán ngay hôm nay.",
                                period, formattedAmount, overdueDays));
                        collectManagerUnpaid(managerUnpaidDigest, invoice, period, overdueDays);
                        invoice.setLastReminderDate(today);
                        stateChanged = true;
                        reminded++;
                    } else if (overdueDays >= terminationAfterDays
                            && !Boolean.TRUE.equals(invoice.getTenantContract().getTerminationProposed())) {
                        sendNotification(invoice, "BILLING_OVERDUE",
                            "🔴 Hợp đồng có thể bị chấm dứt",
                            String.format("Tiền phòng %s %sđ đã quá hạn %d ngày. Quản lý đã được quyền đề nghị chấm dứt hợp đồng. Thanh toán ngay để giữ hợp đồng.",
                                period, formattedAmount, overdueDays));
                        notifyManagerAndHostsOverdue(invoice, period, formattedAmount, overdueDays);
                        invoice.getTenantContract().setTerminationProposed(true);
                        tenantContractRepository.save(invoice.getTenantContract());
                        invoice.setLastReminderDate(today);
                        stateChanged = true;
                        reminded++;
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

        int managerUnpaidNotified = flushManagerUnpaidDigest(managerUnpaidDigest, today);

        Map<String, Integer> stats = new HashMap<>();
        stats.put("reminded", reminded);
        stats.put("overdueMarked", overdueMarked);
        stats.put("renotified", renotified);
        stats.put("rentIssued", issued);
        stats.put("rentEscalated", escalated);
        stats.put("escalationNotices", escalationNotices);
        stats.put("meterReminded", meterReminded);
        stats.put("issueReminded", issueReminded);
        stats.put("managerUnpaidNotified", managerUnpaidNotified);
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

        String dedupeKey = type + ":" + invoice.getId() + ":" + todayVn().format(DAY_KEY);
        sendUserNotification(invoice.getTenantUserId(), type, title, content, screen, paramsJson, dedupeKey,
                Map.of(
                        "invoiceId", invoice.getId(),
                        "type", type != null ? type : "BILLING",
                        "screen", screen));
    }

    private void sendPushNotificationOnly(UUID userId, String title, String content, String type, String screen) {
        sendPushNotificationOnly(userId, title, content, type, screen, null, null);
    }

    private boolean sendPushNotificationOnly(UUID userId, String title, String content, String type, String screen,
                                          String dedupeKey, Map<String, Object> extraData) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", type != null ? type : "BILLING");
        data.put("screen", screen != null ? screen : "InvoiceList");
        if (extraData != null) {
            data.putAll(extraData);
        }
        return sendUserNotification(userId, type, title, content, screen, null, dedupeKey, data);
    }

    /**
     * Lưu Notification → {@link com.sep490.slms2026.entity.NotificationListener} đẩy SSE realtime;
     * đồng thời FCM/Expo push. Có dedupeKey thì chống gửi trùng trong cùng ngày.
     */
    private boolean sendUserNotification(UUID userId, String type, String title, String content,
                                         String screen, String paramsJson, String dedupeKey,
                                         Map<String, Object> pushData) {
        if (userId == null) {
            return false;
        }
        if (dedupeKey != null && notificationRepository.existsByUserIdAndDedupeKey(userId, dedupeKey)) {
            return false;
        }
        try {
            notificationRepository.save(Notification.builder()
                    .userId(userId)
                    .title(title)
                    .content(content)
                    .type(type)
                    .screen(screen)
                    .paramsJson(paramsJson)
                    .dedupeKey(dedupeKey)
                    .read(false)
                    .build());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.debug("Bỏ qua notification trùng {}: {}", dedupeKey, e.getMessage());
            return false;
        }
        userPushTokenService.sendToUser(userId, title, content, pushData != null ? pushData : Map.of());
        return true;
    }

    private record ManagerUnpaidLine(String tenantName, String roomLabel, String period, long overdueDays) {}

    private UUID resolvePropertyManagerId(TenantInvoice invoice) {
        if (invoice.getTenantContract() == null || invoice.getTenantContract().getProperty() == null) {
            return null;
        }
        return invoice.getTenantContract().getProperty().getOperationManagerId();
    }

    private void collectManagerUnpaid(Map<UUID, List<ManagerUnpaidLine>> digest,
                                      TenantInvoice invoice, String period, long overdueDays) {
        UUID managerId = resolvePropertyManagerId(invoice);
        if (managerId == null) {
            return;
        }
        String tenantName = "Khách";
        if (invoice.getTenantContract().getTenant() != null
                && invoice.getTenantContract().getTenant().getUser() != null
                && invoice.getTenantContract().getTenant().getUser().getFullName() != null) {
            tenantName = invoice.getTenantContract().getTenant().getUser().getFullName();
        } else if (invoice.getTenantContract().getDraftTenantName() != null) {
            tenantName = invoice.getTenantContract().getDraftTenantName();
        }
        String roomStr = invoice.getTenantContract().getRoom() != null
                ? invoice.getTenantContract().getRoom().getRoomNumber()
                : "Nguyên căn";
        digest.computeIfAbsent(managerId, k -> new ArrayList<>())
                .add(new ManagerUnpaidLine(tenantName, roomStr, period, overdueDays));
    }

    private int flushManagerUnpaidDigest(Map<UUID, List<ManagerUnpaidLine>> digest, LocalDate today) {
        if (digest == null || digest.isEmpty()) {
            return 0;
        }
        int sent = 0;
        String dayKey = today.format(DAY_KEY);
        for (Map.Entry<UUID, List<ManagerUnpaidLine>> entry : digest.entrySet()) {
            UUID managerId = entry.getKey();
            List<ManagerUnpaidLine> lines = entry.getValue();
            if (lines == null || lines.isEmpty()) {
                continue;
            }
            String dedupeKey = "RENT_UNPAID_MANAGER:" + managerId + ":" + dayKey;
            String title;
            String content;
            if (lines.size() == 1) {
                ManagerUnpaidLine line = lines.get(0);
                title = managerUnpaidTitle(line.overdueDays());
                content = managerUnpaidLineContent(line);
            } else {
                title = String.format("📣 %d khách chưa thanh toán tiền phòng", lines.size());
                StringBuilder body = new StringBuilder();
                for (ManagerUnpaidLine line : lines) {
                    if (body.length() > 0) {
                        body.append('\n');
                    }
                    body.append("• ").append(managerUnpaidLineContent(line));
                }
                content = body.toString();
            }
            // Không đưa số tiền — cùng chính sách PAYMENT_RECEIVED_MANAGER
            if (sendPushNotificationOnly(managerId, title, content, "RENT_UNPAID_MANAGER", "RentInvoice",
                    dedupeKey, Map.of("count", lines.size()))) {
                sent++;
            }
        }
        return sent;
    }

    private static String managerUnpaidTitle(long overdueDays) {
        if (overdueDays <= 0) {
            return "📣 Hôm nay hạn cuối — còn khách chưa đóng tiền phòng";
        }
        if (overdueDays == 1) {
            return "📣 Quá hạn 1 ngày — cần liên hệ khách";
        }
        return String.format("📣 Quá hạn %d ngày — sắp đủ điều kiện chấm dứt HĐ", overdueDays);
    }

    private static String managerUnpaidLineContent(ManagerUnpaidLine line) {
        String who = line.tenantName() + " · Phòng " + line.roomLabel();
        if (line.overdueDays() <= 0) {
            return "Hôm nay là hạn cuối, " + who + " chưa thanh toán tiền phòng " + line.period() + ".";
        }
        if (line.overdueDays() == 1) {
            return "Quá hạn 1 ngày — " + who + " (" + line.period() + "). Liên hệ khách nhắc thanh toán.";
        }
        // Ngày trước mốc chấm dứt (mặc định ngày 7 → quá hạn 2 ngày)
        return "Quá hạn " + line.overdueDays() + " ngày — " + who + " (" + line.period()
                + "). Ngày mai đủ điều kiện đề nghị chấm dứt hợp đồng.";
    }

    /** true nếu HD-ONBOARD đã PAID và có tiền nhà kỳ đầu — không được đòi FIRST lần nữa. */
    private boolean firstRentAlreadyCollectedViaOnboard(TenantInvoice invoice) {
        if (invoice.getTenantContract() == null || invoice.getTenantContract().getId() == null) {
            return false;
        }
        if (invoice.getNote() != null && invoice.getNote().contains("onboardPaid=true")) {
            return false;
        }
        return tenantInvoiceRepository.findByCode("HD-ONBOARD-" + invoice.getTenantContract().getId())
                .filter(onboard -> onboard.getStatus() == TenantInvoiceStatus.PAID)
                .map(BillingCronServiceImpl::onboardCollectedFirstRent)
                .filter(amount -> amount.compareTo(BigDecimal.ZERO) > 0)
                .isPresent();
    }

    private static BigDecimal onboardCollectedFirstRent(TenantInvoice onboard) {
        BigDecimal fromNote = parseNoteDecimal(onboard.getNote(), "firstRentAmount");
        if (fromNote != null && fromNote.compareTo(BigDecimal.ZERO) > 0) {
            return fromNote;
        }
        BigDecimal deposit = parseNoteDecimal(onboard.getNote(), "depositAmount");
        if (deposit != null && onboard.getGrandTotal() != null) {
            BigDecimal inferred = onboard.getGrandTotal().subtract(deposit);
            if (inferred.compareTo(BigDecimal.ZERO) > 0) {
                return inferred;
            }
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal parseNoteDecimal(String note, String key) {
        if (note == null) {
            return null;
        }
        String prefix = key + "=";
        for (String part : note.split("\\|")) {
            if (part.startsWith(prefix)) {
                try {
                    return new BigDecimal(part.substring(prefix.length()).trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private void notifyManagerAndHostsOverdue(TenantInvoice invoice, String period, String formattedAmount, long overdueDays) {
        UUID managerId = resolvePropertyManagerId(invoice);
        if (managerId != null) {
            String tenantName = invoice.getTenantContract().getTenant() != null
                    && invoice.getTenantContract().getTenant().getUser() != null
                    ? invoice.getTenantContract().getTenant().getUser().getFullName()
                    : "Khách";
            String roomStr = invoice.getTenantContract().getRoom() != null
                    ? invoice.getTenantContract().getRoom().getRoomNumber()
                    : "Nguyên căn";
            String title = String.format("⛔ Quá hạn tiền phòng %d ngày — được quyền chấm dứt hợp đồng", overdueDays);
            // Không lộ số tiền cho quản lý (cùng chính sách PAYMENT_RECEIVED_MANAGER)
            String content = String.format(
                    "%s · Phòng %s chưa thanh toán tiền phòng %s. Hợp đồng đã bị gắn cờ đề nghị chấm dứt.",
                    tenantName, roomStr, period);
            String dedupeKey = "RENT_OVERDUE_MANAGER:" + invoice.getId() + ":" + todayVn().format(DAY_KEY);
            sendPushNotificationOnly(managerId, title, content, "RENT_OVERDUE_MANAGER", "RentInvoice",
                    dedupeKey, Map.of("invoiceId", invoice.getId()));
        }
        
        List<com.sep490.slms2026.entity.User> hosts = userRepository.findByRoleAndStatus(com.sep490.slms2026.enums.Role.ROLE_OWNER, com.sep490.slms2026.enums.UserStatus.ACTIVE);
        for (com.sep490.slms2026.entity.User host : hosts) {
            String tenantName = invoice.getTenantContract().getTenant() != null
                    && invoice.getTenantContract().getTenant().getUser() != null
                    ? invoice.getTenantContract().getTenant().getUser().getFullName()
                    : "Khách";
            String roomStr = invoice.getTenantContract().getRoom() != null ? invoice.getTenantContract().getRoom().getRoomNumber() : "nguyên căn";
            String propertyName = invoice.getTenantContract().getProperty().getPropertyName();
            String title = String.format("⛔ Khách thuê quá hạn tiền phòng %d ngày", overdueDays);
            String content = String.format("Khách %s (Phòng %s, nhà %s) quá hạn thanh toán tiền phòng %s. Quản lý đã nhận được thông báo đề nghị chấm dứt hợp đồng.", 
                    tenantName, roomStr, propertyName, period);
            sendPushNotificationOnly(host.getId(), title, content, "RENT_OVERDUE_HOST", "RentInvoice",
                    "RENT_OVERDUE_HOST:" + invoice.getId() + ":" + host.getId(),
                    Map.of("invoiceId", invoice.getId()));
            
            try {
                hostNotificationRepository.insertIfAbsent(
                    host.getId(), 
                    "rent-overdue:" + invoice.getId(), 
                    "RENT_OVERDUE_HOST", 
                    title, 
                    content, 
                    "HIGH"
                );
            } catch (Exception e) {
                log.error("Failed to insert host notification for rent overdue", e);
            }
        }
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void generateMonthlyRentInvoices() {
        log.info("Starting generateDueRentInvoices...");
        generateDueRentInvoices(todayVn());
    }

    int generateDueRentInvoices(LocalDate today) {
        int issued = 0;
        YearMonth currentMonth = YearMonth.from(today);
        List<TenantContract> activeContracts =
                tenantContractRepository.findByStatusWithPropertyAndTenant(ContractStatus.ACTIVE);

        for (TenantContract contract : activeContracts) {
            try {
                if (!ContractBillingCalendar.shouldIssueRegularRent(today, currentMonth, contract)) {
                    continue;
                }
                var existing = tenantInvoiceRepository.findByTenantContractIdAndInvoiceTypeAndBillingYearAndBillingMonth(
                        contract.getId(), TenantInvoiceType.RENT, currentMonth.getYear(), currentMonth.getMonthValue());
                if (existing.isPresent()) {
                    continue;
                }
                if (contract.getTenant() == null) {
                    continue;
                }

                BigDecimal amount = RentFirstCycleCalculator.regularRentAmount(contract, currentMonth);
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                LocalDate dueDate = ContractBillingCalendar.regularDueDate(currentMonth);

                TenantInvoice invoice = tenantInvoiceRepository.save(TenantInvoice.builder()
                        .code("HD-RENT-" + contract.getId() + "-" + currentMonth)
                        .tenantUserId(contract.getTenant().getId())
                        .tenantContract(contract)
                        .invoiceType(TenantInvoiceType.RENT)
                        .cycleType(com.sep490.slms2026.enums.RentCycleType.REGULAR)
                        .propertyName(contract.getProperty().getPropertyName())
                        .roomNumber(contract.getRoom() != null
                                ? contract.getRoom().getRoomNumber()
                                : contract.getProperty().getPropertyName())
                        .billingMonth(currentMonth.getMonthValue())
                        .billingYear(currentMonth.getYear())
                        .billingPeriod("Tiền nhà tháng " + String.format("%02d/%d",
                                currentMonth.getMonthValue(), currentMonth.getYear()))
                        .totalAmount(amount)
                        .lateFee(BigDecimal.ZERO)
                        .grandTotal(amount)
                        .status(TenantInvoiceStatus.PENDING)
                        .dueDate(dueDate)
                        .createdAt(LocalDateTime.now())
                        .autoIssued(true)
                        .build());

                String period = "tháng " + String.format("%02d/%d",
                        currentMonth.getMonthValue(), currentMonth.getYear());
                String title = "🧾 Hoá đơn tiền phòng đã có — cần thanh toán";
                String content = String.format(
                        "Tiền phòng %s %sđ vừa được phát hành. Hạn thanh toán %s. Mở app để thanh toán ngay, tránh để quá hạn.",
                        period, formatCurrency(amount), dueDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                sendNotification(invoice, "RENT_ISSUED", title, content);
                issued++;
            } catch (Exception e) {
                log.error("Error generating monthly rent invoice for contract {}", contract.getId(), e);
            }
        }
        return issued;
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void remindUpcomingRent() {
        log.info("Starting remindUpcomingRent...");
        int count = remindUpcomingRent(todayVn());
        log.info("Finished remindUpcomingRent. reminded={}", count);
    }

    /**
     * Nhắc trước phát hành hoá đơn trong khoảng D-N … D-1 (mặc định N=2 → ngày 30 và 31).
     * Idempotent qua {@code tenant_contracts.last_issue_reminder_date} + dedupeKey notification (SSE).
     */
    int remindUpcomingRent(LocalDate today) {
        int lead = Math.max(issueReminderLeadDays, 1);
        LocalDate nextIssue = YearMonth.from(today).plusMonths(1)
                .atDay(ContractBillingCalendar.REGULAR_RENT_ISSUE_DAY);
        long daysUntilIssue = ChronoUnit.DAYS.between(today, nextIssue);
        if (daysUntilIssue < 1 || daysUntilIssue > lead) {
            return 0;
        }
        YearMonth issueMonth = YearMonth.from(nextIssue);
        List<TenantContract> activeContracts =
                tenantContractRepository.findByStatusWithPropertyAndTenant(ContractStatus.ACTIVE);
        int reminded = 0;

        for (TenantContract contract : activeContracts) {
            if (contract.getTenant() == null) {
                continue;
            }
            if (today.equals(contract.getLastIssueReminderDate())) {
                continue;
            }
            // Truyền nextIssue (ngày phát hành) — shouldIssue yêu cầu today >= issue date
            if (!ContractBillingCalendar.shouldIssueRegularRent(nextIssue, issueMonth, contract)) {
                continue;
            }
            var existing = tenantInvoiceRepository.findByTenantContractIdAndInvoiceTypeAndBillingYearAndBillingMonth(
                    contract.getId(), TenantInvoiceType.RENT, issueMonth.getYear(), issueMonth.getMonthValue());
            if (existing.isPresent()) {
                continue;
            }
            BigDecimal amount = RentFirstCycleCalculator.regularRentAmount(contract, issueMonth);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            LocalDate dueDate = ContractBillingCalendar.regularDueDate(issueMonth);
            String period = "tháng " + String.format("%02d/%d",
                    issueMonth.getMonthValue(), issueMonth.getYear());
            String title = daysUntilIssue == 1
                    ? "🔔 Nhắc trước: ngày mai phát hành hoá đơn tiền phòng"
                    : String.format("🔔 Nhắc trước: %d ngày nữa có hoá đơn tiền phòng", daysUntilIssue);
            String whenPhrase = daysUntilIssue == 1
                    ? "ngày mai"
                    : (daysUntilIssue + " ngày nữa");
            String content = String.format(
                    "Tiền phòng %s %sđ sẽ được phát hành vào %s, hạn thanh toán chậm nhất %s. Bạn chuẩn bị trước giúp nhé.",
                    period, formatCurrency(amount), whenPhrase,
                    dueDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            String dedupeKey = "RENT_REMINDER_PRE:" + contract.getId() + ":" + today.format(DAY_KEY);
            boolean sent = sendPushNotificationOnly(
                    contract.getTenant().getId(), title, content, "RENT_REMINDER_PRE", "InvoiceList",
                    dedupeKey, null);
            // Luôn chốt mốc ngày dù dedupe chặn (cron chạy 2 lần / catch-up sweep)
            if (sent || notificationRepository.existsByUserIdAndDedupeKey(contract.getTenant().getId(), dedupeKey)) {
                contract.setLastIssueReminderDate(today);
                tenantContractRepository.save(contract);
            }
            if (sent) {
                reminded++;
            }
        }
        return reminded;
    }

    int remindPendingMeterReadings(LocalDate today) {
        YearMonth currentMonth = YearMonth.from(today);
        String period = ContractBillingCalendar.normalizePeriod(currentMonth);
        List<TenantContract> activeContracts =
                tenantContractRepository.findByStatusWithPropertyAndTenant(ContractStatus.ACTIVE);
        
        Map<UUID, List<TenantContract>> managerMissing = new HashMap<>();
        Map<UUID, LocalDate> managerMeterDue = new HashMap<>();

        for (TenantContract contract : activeContracts) {
            if (contract.getProperty() == null) {
                continue;
            }
            int billingDay = ContractBillingCalendar.billingDayOfMonth(contract);
            LocalDate remindDate = ContractBillingCalendar.meterRemindDate(
                    currentMonth, billingDay,
                    rentReminderLeadDaysValue,
                    meterReminderLeadDaysValue);
            LocalDate meterDue = ContractBillingCalendar.meterDueDate(
                    currentMonth, billingDay, rentReminderLeadDaysValue);
            if (today.isBefore(remindDate) || today.isAfter(meterDue)) {
                continue;
            }
            if (!hasMissingMeterPhoto(contract, period)) {
                continue;
            }
            UUID managerId = contract.getProperty().getOperationManagerId();
            if (managerId != null) {
                managerMissing.computeIfAbsent(managerId, k -> new ArrayList<>()).add(contract);
                managerMeterDue.put(managerId, meterDue);
            }
        }

        int reminded = 0;
        for (Map.Entry<UUID, List<TenantContract>> entry : managerMissing.entrySet()) {
            UUID managerId = entry.getKey();
            LocalDateTime startOfDay = today.atStartOfDay();
            if (notificationRepository.existsByUserIdAndTypeAndCreatedAtGreaterThanEqual(
                    managerId, "METER_READING_DUE", startOfDay)) {
                continue;
            }

            List<TenantContract> contracts = entry.getValue();
            LocalDate meterDue = managerMeterDue.get(managerId);

            int totalMissingMeters = 0;
            Map<String, Integer> propertyRoomsCount = new LinkedHashMap<>();
            Map<String, Boolean> propertyWholeHouse = new HashMap<>();

            for (TenantContract c : contracts) {
                Long pId = c.getProperty().getId();
                Long rId = c.getRoom() != null ? c.getRoom().getId() : null;
                boolean missingElec = !hasMeterPhoto(pId, rId, UtilityType.ELECTRIC, period);
                boolean missingWater = !hasMeterPhoto(pId, rId, UtilityType.WATER, period);
                
                if (missingElec) totalMissingMeters++;
                if (missingWater) totalMissingMeters++;

                String propName = c.getProperty().getPropertyName();
                propertyRoomsCount.put(propName, propertyRoomsCount.getOrDefault(propName, 0) + 1);
                if (rId == null) {
                    propertyWholeHouse.put(propName, true);
                }
            }

            StringBuilder summary = new StringBuilder();
            int i = 0;
            for (Map.Entry<String, Integer> pEntry : propertyRoomsCount.entrySet()) {
                if (i > 0) summary.append(", ");
                String propName = pEntry.getKey();
                int count = pEntry.getValue();
                boolean isWholeHouse = propertyWholeHouse.getOrDefault(propName, false);
                
                if (isWholeHouse) {
                    summary.append(propName).append(" (nguyên căn)");
                } else {
                    summary.append(propName).append(" (").append(count).append(" phòng)");
                }
                i++;
            }

            String title = String.format("📸 Còn %d công tơ chưa chụp", totalMissingMeters);
            String content = String.format("%s. Hạn ghi điện: %s.",
                    summary.toString(), meterDue.format(DateTimeFormatter.ofPattern("dd/MM")));
            String paramsJson = "{\"period\":\"" + period + "\"}";
            
            notificationRepository.save(Notification.builder()
                    .userId(managerId)
                    .title(title)
                    .content(content)
                    .type("METER_READING_DUE")
                    .screen("MeterReadingPending")
                    .paramsJson(paramsJson)
                    .read(false)
                    .build());
            
            userPushTokenService.sendToUser(managerId, title, content, Map.of(
                    "type", "METER_READING_DUE",
                    "screen", "MeterReadingPending",
                    "period", period));
            reminded++;
        }
        return reminded;
    }

    private boolean hasMissingMeterPhoto(TenantContract contract, String period) {
        Long propertyId = contract.getProperty().getId();
        Long roomId = contract.getRoom() != null ? contract.getRoom().getId() : null;
        return !hasMeterPhoto(propertyId, roomId, UtilityType.ELECTRIC, period)
                || !hasMeterPhoto(propertyId, roomId, UtilityType.WATER, period);
    }

    private boolean hasMeterPhoto(Long propertyId, Long roomId, UtilityType type, String period) {
        Optional<MeterReading> reading = roomId == null
                ? meterReadingRepository.findTopByPropertyIdAndRoomIsNullAndUtilityTypeAndPeriodOrderByRecordedAtDesc(
                        propertyId, type, period)
                : meterReadingRepository.findTopByPropertyIdAndRoomIdAndUtilityTypeAndPeriodOrderByRecordedAtDesc(
                        propertyId, roomId, type, period);
        return reading.filter(r -> r.getImageUrl() != null && !r.getImageUrl().isBlank()).isPresent();
    }
}

