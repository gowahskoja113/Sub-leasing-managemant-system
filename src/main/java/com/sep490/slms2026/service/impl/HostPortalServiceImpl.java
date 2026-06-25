package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.host.*;
import com.sep490.slms2026.entity.*;
import com.sep490.slms2026.enums.*;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.*;
import com.sep490.slms2026.service.HostPortalService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HostPortalServiceImpl implements HostPortalService {

    private static final int EXPIRING_DAYS = 60;

    private final HostNotificationRepository hostNotificationRepository;
    private final HostExpenseRepository hostExpenseRepository;
    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final TenantContractRepository tenantContractRepository;
    private final InboundContractRepository inboundContractRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Page<HostNotificationDto> listNotifications(UUID userId, Boolean unreadOnly, Pageable pageable) {
        syncSystemNotifications(userId);
        Page<HostNotification> page = Boolean.TRUE.equals(unreadOnly)
                ? hostNotificationRepository.findByUserIdAndRead(userId, false, pageable)
                : hostNotificationRepository.findByUserId(userId, pageable);
        return page.map(this::toNotificationDto);
    }

    @Override
    @Transactional
    public void markNotificationRead(UUID userId, Long notificationId) {
        HostNotification notification = hostNotificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông báo ID=" + notificationId));
        notification.setRead(true);
        hostNotificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void markAllNotificationsRead(UUID userId) {
        hostNotificationRepository.markAllRead(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public HostDashboardSummaryResponse getDashboardSummary(String month) {
        YearMonth ym = parseMonth(month, YearMonth.now());
        YearMonth prev = ym.minusMonths(1);

        BigDecimal revenue = sumTenantRevenue(ym);
        BigDecimal expense = sumTotalExpense(ym);
        BigDecimal prevRevenue = sumTenantRevenue(prev);
        BigDecimal prevExpense = sumTotalExpense(prev);
        BigDecimal net = revenue.subtract(expense);
        BigDecimal prevNet = prevRevenue.subtract(prevExpense);

        long totalRooms = roomRepository.countByDeletedIsFalse();
        long occupied = countRoomsByStatus(RoomStatus.RENTED);
        long maintenance = countRoomsByStatus(RoomStatus.MAINTENANCE);
        long vacant = countRoomsByStatus(RoomStatus.AVAILABLE);
        BigDecimal occupancyRate = rate(occupied, totalRooms);

        long pendingContracts = tenantContractRepository.findByStatus(ContractStatus.PENDING).size();
        long expiringLeases = countExpiringMasterLeases();
        long activeManagers = userRepository.findByRoleAndStatus(Role.ROLE_MANAGER, UserStatus.ACTIVE).size();
        List<HostInvoiceDto> outstanding = buildInvoices(ym, "UNPAID");
        outstanding.addAll(buildInvoices(ym, "OVERDUE"));
        BigDecimal outstandingAmount = outstanding.stream()
                .map(HostInvoiceDto::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return HostDashboardSummaryResponse.builder()
                .month(ym.toString())
                .finance(HostDashboardSummaryResponse.Finance.builder()
                        .revenue(revenue)
                        .expense(expense)
                        .netProfit(net)
                        .revenueChangePct(changePct(revenue, prevRevenue))
                        .expenseChangePct(changePct(expense, prevExpense))
                        .netProfitChangePct(changePct(net, prevNet))
                        .build())
                .occupancy(HostDashboardSummaryResponse.Occupancy.builder()
                        .totalRooms(totalRooms)
                        .occupiedRooms(occupied)
                        .vacantRooms(vacant)
                        .maintenanceRooms(maintenance)
                        .occupancyRate(occupancyRate)
                        .build())
                .counts(HostDashboardSummaryResponse.Counts.builder()
                        .pendingContracts(pendingContracts)
                        .openMaintenance(maintenance)
                        .activeManagers(activeManagers)
                        .expiringMasterLeases(expiringLeases)
                        .outstandingInvoices(outstanding.size())
                        .outstandingAmount(outstandingAmount)
                        .build())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public HostCashflowResponse getCashflow(String from, String to) {
        YearMonth start = parseMonth(from, YearMonth.now().minusMonths(11));
        YearMonth end = parseMonth(to, YearMonth.now());
        if (end.isBefore(start)) {
            throw new BusinessException("Tham số to phải sau hoặc bằng from");
        }
        List<HostCashflowResponse.CashflowPoint> series = new ArrayList<>();
        for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
            series.add(HostCashflowResponse.CashflowPoint.builder()
                    .month(ym.toString())
                    .revenue(sumTenantRevenue(ym))
                    .expense(sumTotalExpense(ym))
                    .build());
        }
        return HostCashflowResponse.builder().series(series).build();
    }

    @Override
    @Transactional(readOnly = true)
    public HostExpenseBreakdownResponse getExpenseBreakdown(String month) {
        YearMonth ym = parseMonth(month, YearMonth.now());
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        byCategory.put("LEASE", sumLeaseExpense(ym));
        for (Object[] row : hostExpenseRepository.sumAmountByCategoryForMonth(ym.toString())) {
            byCategory.merge(row[0].toString(), (BigDecimal) row[1], BigDecimal::add);
        }
        List<HostExpenseBreakdownResponse.ExpenseBreakdownItem> breakdown = byCategory.entrySet().stream()
                .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(e -> HostExpenseBreakdownResponse.ExpenseBreakdownItem.builder()
                        .category(e.getKey())
                        .amount(e.getValue())
                        .build())
                .toList();
        return HostExpenseBreakdownResponse.builder()
                .month(ym.toString())
                .breakdown(breakdown)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public HostPropertyPnlResponse getPropertyPnl(String month) {
        YearMonth ym = parseMonth(month, YearMonth.now());
        List<HostPropertyPnlResponse.PropertyPnlRow> rows = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalLease = BigDecimal.ZERO;
        BigDecimal totalOther = BigDecimal.ZERO;

        for (Property property : propertyRepository.findAll()) {
            BigDecimal revenue = sumTenantRevenueForProperty(property.getId(), ym);
            BigDecimal leaseCost = sumLeaseExpenseForProperty(property.getId(), ym);
            BigDecimal otherExpense = hostExpenseRepository.sumAmountByPropertyAndMonth(property.getId(), ym.toString());
            BigDecimal totalExpense = leaseCost.add(otherExpense);
            BigDecimal net = revenue.subtract(totalExpense);

            rows.add(HostPropertyPnlResponse.PropertyPnlRow.builder()
                    .propertyId(String.valueOf(property.getId()))
                    .propertyName(property.getPropertyName())
                    .revenue(revenue)
                    .leaseCost(leaseCost)
                    .otherExpense(otherExpense)
                    .totalExpense(totalExpense)
                    .net(net)
                    .marginPct(marginPct(net, revenue))
                    .build());

            totalRevenue = totalRevenue.add(revenue);
            totalLease = totalLease.add(leaseCost);
            totalOther = totalOther.add(otherExpense);
        }

        BigDecimal totalExpense = totalLease.add(totalOther);
        BigDecimal totalNet = totalRevenue.subtract(totalExpense);
        return HostPropertyPnlResponse.builder()
                .month(ym.toString())
                .rows(rows)
                .totals(HostPropertyPnlResponse.PropertyPnlTotals.builder()
                        .revenue(totalRevenue)
                        .leaseCost(totalLease)
                        .otherExpense(totalOther)
                        .totalExpense(totalExpense)
                        .net(totalNet)
                        .marginPct(marginPct(totalNet, totalRevenue))
                        .build())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public HostReceivablesAgingResponse getReceivablesAging() {
        YearMonth ym = YearMonth.now();
        List<HostInvoiceDto> invoices = new ArrayList<>(buildInvoices(ym, null));
        LocalDate today = LocalDate.now();

        Map<String, List<HostInvoiceDto>> buckets = new LinkedHashMap<>();
        buckets.put("0-30 ngày", new ArrayList<>());
        buckets.put("31-60 ngày", new ArrayList<>());
        buckets.put("61-90 ngày", new ArrayList<>());
        buckets.put(">90 ngày", new ArrayList<>());

        for (HostInvoiceDto invoice : invoices) {
            if ("PAID".equals(invoice.status())) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(invoice.dueDate(), today);
            if (days <= 30) {
                buckets.get("0-30 ngày").add(invoice);
            } else if (days <= 60) {
                buckets.get("31-60 ngày").add(invoice);
            } else if (days <= 90) {
                buckets.get("61-90 ngày").add(invoice);
            } else {
                buckets.get(">90 ngày").add(invoice);
            }
        }

        List<HostReceivablesAgingResponse.AgingBucket> agingBuckets = buckets.entrySet().stream()
                .map(e -> HostReceivablesAgingResponse.AgingBucket.builder()
                        .label(e.getKey())
                        .amount(e.getValue().stream().map(HostInvoiceDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                        .count(e.getValue().size())
                        .build())
                .toList();

        List<HostReceivablesAgingResponse.TopDebtor> topDebtors = invoices.stream()
                .filter(i -> !"PAID".equals(i.status()))
                .sorted(Comparator.comparing(HostInvoiceDto::amount).reversed())
                .limit(10)
                .map(i -> HostReceivablesAgingResponse.TopDebtor.builder()
                        .tenantName(i.tenantName())
                        .propertyName(i.propertyName())
                        .roomCode(i.roomCode())
                        .amount(i.amount())
                        .overdueDays(Math.max(0, ChronoUnit.DAYS.between(i.dueDate(), today)))
                        .build())
                .toList();

        return HostReceivablesAgingResponse.builder()
                .buckets(agingBuckets)
                .topDebtors(topDebtors)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public HostDepositsResponse getDeposits(String status) {
        List<TenantContract> contracts = tenantContractRepository.findByStatus(ContractStatus.ACTIVE);
        List<HostDepositsResponse.DepositItem> items = contracts.stream()
                .filter(c -> c.getDeposit() != null && c.getDeposit().compareTo(BigDecimal.ZERO) > 0)
                .map(c -> HostDepositsResponse.DepositItem.builder()
                        .tenantName(c.getTenant().getUser().getFullName())
                        .propertyName(c.getProperty().getPropertyName())
                        .roomCode(c.getRoom() != null ? c.getRoom().getRoomNumber() : "NGUYEN_CAN")
                        .amount(c.getDeposit())
                        .heldSince(c.getStartDate())
                        .status(mapDepositStatus(c.getStatus()))
                        .build())
                .filter(item -> status == null || status.isBlank() || status.equalsIgnoreCase(item.status()))
                .toList();

        BigDecimal totalHeld = items.stream()
                .filter(i -> "HELD".equals(i.status()))
                .map(HostDepositsResponse.DepositItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return HostDepositsResponse.builder().totalHeld(totalHeld).items(items).build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<HostInvoiceDto> listInvoices(String month, String status, Pageable pageable) {
        YearMonth ym = month != null && !month.isBlank() ? parseMonth(month, YearMonth.now()) : YearMonth.now();
        List<HostInvoiceDto> all = buildInvoices(ym, status);
        return slicePage(all, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<HostExpenseDto> listExpenses(Long propertyId, String category, String month, Pageable pageable) {
        HostExpenseCategory cat = parseExpenseCategory(category);
        return hostExpenseRepository.search(propertyId, cat, blankToNull(month), pageable)
                .map(this::toExpenseDto);
    }

    @Override
    @Transactional
    public HostExpenseDto createExpense(HostExpenseUpsertRequest request) {
        Property property = propertyRepository.findById(Long.parseLong(request.getPropertyId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà"));
        HostExpense expense = HostExpense.builder()
                .property(property)
                .category(parseExpenseCategoryRequired(request.getCategory()))
                .amount(request.getAmount())
                .month(request.getMonth())
                .note(request.getNote())
                .build();
        return toExpenseDto(hostExpenseRepository.save(expense));
    }

    @Override
    @Transactional
    public HostExpenseDto updateExpense(Long id, Map<String, Object> patch) {
        HostExpense expense = hostExpenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chi phí ID=" + id));
        if (patch.containsKey("propertyId")) {
            Long propertyId = Long.parseLong(patch.get("propertyId").toString());
            expense.setProperty(propertyRepository.findById(propertyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà")));
        }
        if (patch.containsKey("category")) {
            expense.setCategory(parseExpenseCategoryRequired(patch.get("category").toString()));
        }
        if (patch.containsKey("amount")) {
            expense.setAmount(new BigDecimal(patch.get("amount").toString()));
        }
        if (patch.containsKey("month")) {
            expense.setMonth(patch.get("month").toString());
        }
        if (patch.containsKey("note")) {
            expense.setNote(patch.get("note") != null ? patch.get("note").toString() : null);
        }
        return toExpenseDto(hostExpenseRepository.save(expense));
    }

    @Override
    @Transactional
    public void deleteExpense(Long id) {
        if (!hostExpenseRepository.existsById(id)) {
            throw new ResourceNotFoundException("Không tìm thấy chi phí ID=" + id);
        }
        hostExpenseRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HostFinancialSummaryRow> getFinancialSummary(String from, String to) {
        YearMonth start = parseMonth(from, YearMonth.now().minusMonths(11));
        YearMonth end = parseMonth(to, YearMonth.now());
        List<HostFinancialSummaryRow> rows = new ArrayList<>();
        long totalRooms = roomRepository.countByDeletedIsFalse();
        for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
            BigDecimal revenue = sumTenantRevenue(ym);
            BigDecimal expense = sumTotalExpense(ym);
            BigDecimal net = revenue.subtract(expense);
            long occupied = countRoomsByStatus(RoomStatus.RENTED);
            rows.add(HostFinancialSummaryRow.builder()
                    .month(ym.toString())
                    .revenue(revenue)
                    .expense(expense)
                    .netProfit(net)
                    .marginPct(marginPct(net, revenue))
                    .occupancyRate(rate(occupied, totalRooms))
                    .build());
        }
        return rows;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HostManagerPerformanceRow> getManagerPerformance(String month) {
        parseMonth(month, YearMonth.now());
        List<User> managers = userRepository.findByRoleAndStatus(Role.ROLE_MANAGER, UserStatus.ACTIVE);
        List<HostManagerPerformanceRow> rows = new ArrayList<>();
        for (User manager : managers) {
            Page<Property> properties = propertyRepository.findAllByManagerZones(manager.getId(), Pageable.unpaged());
            long propertyCount = properties.getTotalElements();
            long activeTenants = properties.getContent().stream()
                    .mapToLong(p -> tenantContractRepository.findByPropertyId(p.getId()).stream()
                            .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                            .count())
                    .sum();
            long totalRooms = properties.getContent().stream()
                    .mapToLong(p -> roomRepository.countByPropertyIdAndDeletedIsFalse(p.getId()))
                    .sum();
            long occupied = properties.getContent().stream()
                    .mapToLong(p -> roomRepository.countByPropertyIdAndStatusAndDeletedIsFalse(p.getId(), RoomStatus.RENTED))
                    .sum();
            long maintenance = properties.getContent().stream()
                    .mapToLong(p -> roomRepository.countByPropertyIdAndStatusAndDeletedIsFalse(p.getId(), RoomStatus.MAINTENANCE))
                    .sum();
            rows.add(HostManagerPerformanceRow.builder()
                    .managerId(manager.getId().toString())
                    .managerName(manager.getFullName())
                    .phone(manager.getPhoneNumber())
                    .propertyCount(propertyCount)
                    .activeTenants(activeTenants)
                    .occupancyRate(rate(occupied, totalRooms))
                    .resolvedMaintenance(0)
                    .openMaintenance(maintenance)
                    .build());
        }
        return rows;
    }

    @Override
    @Transactional(readOnly = true)
    public List<HostPropertyPerformanceRow> getPropertyPerformance(String month) {
        parseMonth(month, YearMonth.now());
        YearMonth ym = YearMonth.now();
        List<HostPropertyPerformanceRow> rows = new ArrayList<>();
        for (Property property : propertyRepository.findAll()) {
            long totalRooms = roomRepository.countByPropertyIdAndDeletedIsFalse(property.getId());
            long occupied = roomRepository.countByPropertyIdAndStatusAndDeletedIsFalse(property.getId(), RoomStatus.RENTED);
            long maintenance = roomRepository.countByPropertyIdAndStatusAndDeletedIsFalse(property.getId(), RoomStatus.MAINTENANCE);
            rows.add(HostPropertyPerformanceRow.builder()
                    .propertyId(String.valueOf(property.getId()))
                    .propertyName(property.getPropertyName())
                    .address(property.getAddress())
                    .occupancyRate(rate(occupied, totalRooms))
                    .occupiedRooms(occupied)
                    .totalRooms(totalRooms)
                    .monthlyRevenue(sumTenantRevenueForProperty(property.getId(), ym))
                    .openMaintenance(maintenance)
                    .build());
        }
        return rows;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<HostContractDto> listContracts(String status, Pageable pageable) {
        if (status == null || status.isBlank()) {
            return tenantContractRepository.findAll(pageable).map(this::toContractDto);
        }
        ContractStatus contractStatus = ContractStatus.valueOf(status.toUpperCase());
        return tenantContractRepository.findByStatus(contractStatus, pageable).map(this::toContractDto);
    }

    @Override
    @Transactional
    public HostContractDto approveContract(Long contractId) {
        TenantContract contract = tenantContractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng ID=" + contractId));
        if (contract.getStatus() != ContractStatus.PENDING) {
            throw new BusinessException("Chỉ duyệt hợp đồng ở trạng thái PENDING");
        }
        contract.setStatus(ContractStatus.ACTIVE);
        return toContractDto(tenantContractRepository.save(contract));
    }

    @Override
    @Transactional
    public HostContractDto rejectContract(Long contractId, String reason) {
        TenantContract contract = tenantContractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hợp đồng ID=" + contractId));
        if (contract.getStatus() != ContractStatus.PENDING) {
            throw new BusinessException("Chỉ từ chối hợp đồng ở trạng thái PENDING");
        }
        contract.setStatus(ContractStatus.TERMINATED);
        if (contract.getRoomConditionNote() == null || contract.getRoomConditionNote().isBlank()) {
            contract.setRoomConditionNote("Host từ chối: " + reason);
        } else {
            contract.setRoomConditionNote(contract.getRoomConditionNote() + " | Host từ chối: " + reason);
        }
        return toContractDto(tenantContractRepository.save(contract));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MasterLeaseDto> listMasterLeases(String status, Long propertyId) {
        ContractStatus filter = mapMasterLeaseStatusFilter(status);
        return inboundContractRepository.searchMasterLeases(filter, propertyId).stream()
                .map(this::toMasterLeaseDto)
                .filter(dto -> status == null || status.isBlank() || status.equalsIgnoreCase(dto.status()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MasterLeaseDto getMasterLease(Long id) {
        InboundContract contract = inboundContractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy master lease ID=" + id));
        return toMasterLeaseDto(contract);
    }

    @Override
    @Transactional
    public MasterLeaseDto createMasterLease(MasterLeaseUpsertRequest request) {
        Long propertyId = Long.parseLong(request.getPropertyId());
        if (inboundContractRepository.existsByPropertyId(propertyId)) {
            throw new BusinessException("Tòa nhà đã có hợp đồng thuê gốc (master lease)");
        }
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà"));
        int months = contractMonths(request.getStartDate(), request.getEndDate());
        InboundContract contract = InboundContract.builder()
                .property(property)
                .contractCode("ML-" + propertyId + "-" + System.currentTimeMillis())
                .ownerName(request.getOwnerName())
                .totalRentAmount(request.getMonthlyRent().multiply(BigDecimal.valueOf(months)))
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(ContractStatus.ACTIVE)
                .build();
        return toMasterLeaseDto(inboundContractRepository.save(contract));
    }

    @Override
    @Transactional
    public MasterLeaseDto updateMasterLease(Long id, Map<String, Object> patch) {
        InboundContract contract = inboundContractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy master lease ID=" + id));
        if (patch.containsKey("ownerName")) {
            contract.setOwnerName(patch.get("ownerName").toString());
        }
        if (patch.containsKey("monthlyRent")) {
            BigDecimal monthly = new BigDecimal(patch.get("monthlyRent").toString());
            int months = contractMonths(contract.getStartDate(), contract.getEndDate());
            contract.setTotalRentAmount(monthly.multiply(BigDecimal.valueOf(months)));
        }
        if (patch.containsKey("startDate")) {
            contract.setStartDate(LocalDate.parse(patch.get("startDate").toString()));
        }
        if (patch.containsKey("endDate")) {
            contract.setEndDate(LocalDate.parse(patch.get("endDate").toString()));
        }
        return toMasterLeaseDto(inboundContractRepository.save(contract));
    }

    @Override
    @Transactional
    public MasterLeaseDto terminateMasterLease(Long id) {
        InboundContract contract = inboundContractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy master lease ID=" + id));
        contract.setStatus(ContractStatus.TERMINATED);
        return toMasterLeaseDto(inboundContractRepository.save(contract));
    }

    private void syncSystemNotifications(UUID userId) {
        for (Property property : propertyRepository.findByStatus(PropertyStatus.PENDING_HOST_REVIEW)) {
            ensureNotification(userId, "property-review:" + property.getId(),
                    "PROPERTY_REVIEW",
                    "Căn chờ duyệt giá",
                    "Căn \"" + property.getPropertyName() + "\" đang chờ Host xác nhận giá.",
                    "HIGH");
        }
        for (TenantContract contract : tenantContractRepository.findByStatus(ContractStatus.PENDING)) {
            ensureNotification(userId, "tenant-contract:" + contract.getId(),
                    "CONTRACT_PENDING",
                    "Hợp đồng chờ duyệt",
                    "HĐ " + contract.getContractCode() + " — "
                            + contract.getTenant().getUser().getFullName() + " chờ Host duyệt.",
                    "MEDIUM");
        }
        LocalDate threshold = LocalDate.now().plusDays(EXPIRING_DAYS);
        for (InboundContract lease : inboundContractRepository.findAll()) {
            if (lease.getStatus() == ContractStatus.ACTIVE
                    && !lease.getEndDate().isAfter(threshold)
                    && lease.getEndDate().isAfter(LocalDate.now())) {
                ensureNotification(userId, "master-lease-expiring:" + lease.getId(),
                        "MASTER_LEASE_EXPIRING",
                        "Master lease sắp hết hạn",
                        "HĐ thuê gốc " + lease.getContractCode() + " hết hạn " + lease.getEndDate(),
                        "MEDIUM");
            }
        }
    }

    private void ensureNotification(UUID userId, String dedupeKey, String type,
                                    String title, String message, String priority) {
        if (hostNotificationRepository.existsByUserIdAndDedupeKey(userId, dedupeKey)) {
            return;
        }
        hostNotificationRepository.save(HostNotification.builder()
                .userId(userId)
                .dedupeKey(dedupeKey)
                .type(type)
                .title(title)
                .message(message)
                .priority(priority)
                .read(false)
                .build());
    }

    private List<HostInvoiceDto> buildInvoices(YearMonth ym, String statusFilter) {
        LocalDate dueDate = ym.atEndOfMonth().plusDays(5);
        LocalDate today = LocalDate.now();
        List<HostInvoiceDto> invoices = new ArrayList<>();
        for (TenantContract contract : tenantContractRepository.findByStatus(ContractStatus.ACTIVE)) {
            if (!isContractActiveInMonth(contract, ym)) {
                continue;
            }
            String status;
            if (contract.getPaymentStatus() == PaymentStatus.PAID
                    && contract.getPaidAt() != null
                    && ym.equals(YearMonth.from(contract.getPaidAt()))) {
                status = "PAID";
            } else if (dueDate.isBefore(today)) {
                status = "OVERDUE";
            } else {
                status = "UNPAID";
            }
            if (statusFilter != null && !statusFilter.isBlank() && !statusFilter.equalsIgnoreCase(status)) {
                continue;
            }
            invoices.add(HostInvoiceDto.builder()
                    .id(contract.getId() + "-" + ym)
                    .tenantName(contract.getTenant().getUser().getFullName())
                    .roomCode(contract.getRoom() != null ? contract.getRoom().getRoomNumber() : "NGUYEN_CAN")
                    .propertyName(contract.getProperty().getPropertyName())
                    .amount(contract.getRentAmount())
                    .dueDate(dueDate)
                    .status(status)
                    .build());
        }
        return invoices;
    }

    private boolean isContractActiveInMonth(TenantContract contract, YearMonth ym) {
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        if (contract.getStartDate().isAfter(monthEnd)) {
            return false;
        }
        return contract.getEndDate() == null || !contract.getEndDate().isBefore(monthStart);
    }

    private BigDecimal sumTenantRevenue(YearMonth ym) {
        return tenantContractRepository.findByStatus(ContractStatus.ACTIVE).stream()
                .filter(c -> isContractActiveInMonth(c, ym))
                .map(TenantContract::getRentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumTenantRevenueForProperty(Long propertyId, YearMonth ym) {
        return tenantContractRepository.findByPropertyId(propertyId).stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                .filter(c -> isContractActiveInMonth(c, ym))
                .map(TenantContract::getRentAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumLeaseExpense(YearMonth ym) {
        return inboundContractRepository.findAll().stream()
                .filter(c -> c.getStatus() != ContractStatus.TERMINATED)
                .map(c -> monthlyLeaseCost(c, ym))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumLeaseExpenseForProperty(Long propertyId, YearMonth ym) {
        return inboundContractRepository.findFirstByPropertyIdOrderByIdDesc(propertyId)
                .map(c -> monthlyLeaseCost(c, ym))
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal sumTotalExpense(YearMonth ym) {
        return sumLeaseExpense(ym).add(
                hostExpenseRepository.sumAmountByMonth(ym.toString()) != null
                        ? hostExpenseRepository.sumAmountByMonth(ym.toString())
                        : BigDecimal.ZERO);
    }

    private BigDecimal monthlyLeaseCost(InboundContract contract, YearMonth ym) {
        if (contract.getStartDate().isAfter(ym.atEndOfMonth())
                || contract.getEndDate().isBefore(ym.atDay(1))) {
            return BigDecimal.ZERO;
        }
        int months = contractMonths(contract.getStartDate(), contract.getEndDate());
        if (months <= 0) {
            return BigDecimal.ZERO;
        }
        return contract.getTotalRentAmount()
                .divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    }

    private int contractMonths(LocalDate start, LocalDate end) {
        long months = ChronoUnit.MONTHS.between(start, end);
        return (int) Math.max(months, 1);
    }

    private long countRoomsByStatus(RoomStatus status) {
        return propertyRepository.findAll().stream()
                .mapToLong(p -> roomRepository.countByPropertyIdAndStatusAndDeletedIsFalse(p.getId(), status))
                .sum();
    }

    private long countExpiringMasterLeases() {
        LocalDate threshold = LocalDate.now().plusDays(EXPIRING_DAYS);
        return inboundContractRepository.findAll().stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                .filter(c -> !c.getEndDate().isAfter(threshold) && c.getEndDate().isAfter(LocalDate.now()))
                .count();
    }

    private HostNotificationDto toNotificationDto(HostNotification n) {
        return HostNotificationDto.builder()
                .id(String.valueOf(n.getId()))
                .type(n.getType())
                .title(n.getTitle())
                .message(n.getMessage())
                .isRead(n.isRead())
                .priority(n.getPriority())
                .createdAt(n.getCreatedAt())
                .build();
    }

    private HostExpenseDto toExpenseDto(HostExpense e) {
        return HostExpenseDto.builder()
                .id(String.valueOf(e.getId()))
                .propertyId(String.valueOf(e.getProperty().getId()))
                .propertyName(e.getProperty().getPropertyName())
                .category(e.getCategory().name())
                .amount(e.getAmount())
                .month(e.getMonth())
                .note(e.getNote())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private HostContractDto toContractDto(TenantContract c) {
        return HostContractDto.builder()
                .id(String.valueOf(c.getId()))
                .code(c.getContractCode())
                .lesseeName(c.getTenant().getUser().getFullName())
                .propertyName(c.getProperty().getPropertyName())
                .roomCode(c.getRoom() != null ? c.getRoom().getRoomNumber() : null)
                .lessorName(c.getProperty().getPropertyName())
                .rentAmount(c.getRentAmount())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .status(c.getStatus().name())
                .build();
    }

    private MasterLeaseDto toMasterLeaseDto(InboundContract c) {
        int months = contractMonths(c.getStartDate(), c.getEndDate());
        BigDecimal monthly = c.getTotalRentAmount()
                .divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        return MasterLeaseDto.builder()
                .id(String.valueOf(c.getId()))
                .propertyId(String.valueOf(c.getProperty().getId()))
                .ownerName(c.getOwnerName())
                .ownerPhone(null)
                .monthlyRent(monthly)
                .deposit(BigDecimal.ZERO)
                .paymentDay(1)
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .escalationPct(null)
                .status(resolveMasterLeaseStatus(c))
                .build();
    }

    private String resolveMasterLeaseStatus(InboundContract c) {
        if (c.getStatus() == ContractStatus.TERMINATED) {
            return "TERMINATED";
        }
        LocalDate today = LocalDate.now();
        if (c.getEndDate().isBefore(today)) {
            return "EXPIRED";
        }
        if (!c.getEndDate().isAfter(today.plusDays(EXPIRING_DAYS))) {
            return "EXPIRING";
        }
        return "ACTIVE";
    }

    private ContractStatus mapMasterLeaseStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return switch (status.toUpperCase()) {
            case "TERMINATED" -> ContractStatus.TERMINATED;
            case "EXPIRED", "EXPIRING", "ACTIVE" -> ContractStatus.ACTIVE;
            default -> ContractStatus.valueOf(status.toUpperCase());
        };
    }

    private String mapDepositStatus(ContractStatus status) {
        return status == ContractStatus.TERMINATED ? "REFUNDED" : "HELD";
    }

    private HostExpenseCategory parseExpenseCategoryRequired(String raw) {
        HostExpenseCategory cat = parseExpenseCategory(raw);
        if (cat == null) {
            throw new BusinessException("category không hợp lệ: " + raw);
        }
        return cat;
    }

    private HostExpenseCategory parseExpenseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return HostExpenseCategory.valueOf(raw.trim().toUpperCase());
    }

    private YearMonth parseMonth(String raw, YearMonth fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return YearMonth.parse(raw.trim());
    }

    private String blankToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private BigDecimal rate(long part, long total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal marginPct(BigDecimal net, BigDecimal revenue) {
        if (revenue == null || revenue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return net.multiply(BigDecimal.valueOf(100))
                .divide(revenue, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal changePct(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    private <T> Page<T> slicePage(List<T> all, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        List<T> content = start >= all.size() ? List.of() : all.subList(start, end);
        return new org.springframework.data.domain.PageImpl<>(content, pageable, all.size());
    }
}
