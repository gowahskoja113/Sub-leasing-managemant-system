package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CreateUtilityInvoiceRequest;
import com.sep490.slms2026.dto.response.RoomUtilitySumSnapshot;
import com.sep490.slms2026.dto.response.UtilityInvoiceHistoryResponse;
import com.sep490.slms2026.dto.response.UtilityInvoiceResponse;
import com.sep490.slms2026.entity.MeterReading;
import com.sep490.slms2026.entity.Notification;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.entity.UtilityBill;
import com.sep490.slms2026.entity.UtilityInvoice;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.enums.UtilityBillStatus;
import com.sep490.slms2026.enums.UtilityInvoiceStatus;
import com.sep490.slms2026.enums.UtilityType;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.MeterReadingRepository;
import com.sep490.slms2026.repository.NotificationRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.repository.UtilityBillRepository;
import com.sep490.slms2026.repository.UtilityInvoiceRepository;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.InvoiceDisputeService;
import com.sep490.slms2026.service.MeterOverrideService;
import com.sep490.slms2026.service.MeterReadingService;
import com.sep490.slms2026.service.PropertyAccessService;
import com.sep490.slms2026.service.UserPushTokenService;
import com.sep490.slms2026.service.UtilityInvoiceService;
import com.sep490.slms2026.util.ContractBillingCalendar;
import com.sep490.slms2026.util.UtilityTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UtilityInvoiceServiceImpl implements UtilityInvoiceService {

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final UtilityInvoiceRepository utilityInvoiceRepository;
    private final UtilityBillRepository utilityBillRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final TenantContractRepository tenantContractRepository;
    private final PropertyAccessService propertyAccessService;
    private final com.sep490.slms2026.service.TenantBillingService tenantBillingService;
    private final NotificationRepository notificationRepository;
    private final UserPushTokenService userPushTokenService;
    private final com.sep490.slms2026.repository.TenantInvoiceRepository tenantInvoiceRepository;
    private final MeterReadingService meterReadingService;
    private final MeterOverrideService meterOverrideService;
    private final InvoiceDisputeService invoiceDisputeService;
    private final UserRepository userRepository;

    /** Hao hụt (companyBorn ÷ total) vượt ngưỡng này → cảnh báo quản lý + admin. */
    @Value("${utility.loss-alert-threshold-percent:15}")
    private int lossAlertThresholdPercent;

    /** Trần tổng phòng = giấy × (1 + % này). */
    @Value("${billing.utility.room-sum-tolerance-percent:10}")
    private int roomSumTolerancePercent;

    @Value("${billing.rent.grace-days:2}")
    private int graceDaysValue;

    @Override
    @Transactional
    public UtilityInvoiceResponse createRoomInvoice(Long propertyId, Long roomId, CreateUtilityInvoiceRequest request) {
        propertyAccessService.assertCanManageProperty(propertyId);
        Property property = loadProperty(propertyId);
        Room room = loadRoom(propertyId, roomId);
        validateRoomBillable(room);
        UtilityType utilityType = UtilityTypeMapper.fromApi(request.getType());
        validateInvoiceAmounts(request);
        assertRoomSumWithinBill(propertyId, roomId, request.getBillingPeriod(), utilityType, request.getConsumption());

        TenantContract contract = tenantContractRepository
                .findByRoomIdAndStatus(roomId, ContractStatus.ACTIVE)
                .orElse(null);
        if (contract == null) {
            contract = tenantContractRepository.findTopByRoomIdAndStatusInOrderByEndDateDesc(roomId, java.util.List.of(ContractStatus.EXPIRED, ContractStatus.TERMINATED)).orElse(null);
        }
        if (contract != null && contract.getStatus() != ContractStatus.ACTIVE && contract.getEndDate() != null) {
            java.time.LocalDate maxDate = contract.getEndDate().plusMonths(1).withDayOfMonth(7);
            if (java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).isAfter(maxDate)) {
                throw new BusinessException("NO_ACTIVE_CONTRACT", "Căn này đã trả phòng ngày " + contract.getEndDate() + ", không thể chốt số liệu tiện ích. Ghi nhận là chi phí công ty.");
            }
        }
        validateBillingPeriodLock(propertyId, roomId, request.getBillingPeriod(), utilityType, contract);

        UtilityInvoiceResponse response = createAndSend(property, room, contract, utilityType, request, true, false, null);
        reconcileIfComplete(property.getId(), request.getBillingPeriod(), utilityType);
        return response;
    }

    @Override
    @Transactional
    public UtilityInvoiceResponse createPropertyInvoice(Long propertyId, CreateUtilityInvoiceRequest request) {
        propertyAccessService.assertCanManageProperty(propertyId);
        Property property = loadProperty(propertyId);
        if (!Boolean.TRUE.equals(property.getWholeHouse())) {
            throw new BusinessException("API nguyên căn chỉ dùng cho nhà whole-house");
        }
        UtilityType utilityType = UtilityTypeMapper.fromApi(request.getType());
        validateInvoiceAmounts(request);

        TenantContract contract = tenantContractRepository
                .findByPropertyIdAndRoomIsNullAndStatus(propertyId, ContractStatus.ACTIVE)
                .orElse(null);
        if (contract == null) {
            contract = tenantContractRepository.findTopByPropertyIdAndRoomIsNullAndStatusInOrderByEndDateDesc(propertyId, java.util.List.of(ContractStatus.EXPIRED, ContractStatus.TERMINATED)).orElse(null);
        }
        if (contract != null && contract.getStatus() != ContractStatus.ACTIVE && contract.getEndDate() != null) {
            java.time.LocalDate maxDate = contract.getEndDate().plusMonths(1).withDayOfMonth(7);
            if (java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).isAfter(maxDate)) {
                throw new BusinessException("NO_ACTIVE_CONTRACT", "Căn này đã trả phòng ngày " + contract.getEndDate() + ", không thể chốt số liệu tiện ích. Ghi nhận là chi phí công ty.");
            }
        }
        validateBillingPeriodLock(propertyId, null, request.getBillingPeriod(), utilityType, contract);

        return createAndSend(property, null, contract, utilityType, request, true, false, null);
    }

    @Override
    @Transactional
    public UtilityInvoiceResponse createFromWholeHouseBill(UtilityBill bill, BigDecimal prevReading, BigDecimal newReading) {
        Property property = bill.getProperty();
        if (!Boolean.TRUE.equals(property.getWholeHouse())) {
            throw new BusinessException("WHOLE_HOUSE_ONLY",
                    "Chỉ phát hành tự động cho nhà nguyên căn.");
        }
        TenantContract contract = tenantContractRepository
                .findByPropertyIdAndRoomIsNullAndStatus(property.getId(), ContractStatus.ACTIVE)
                .orElseGet(() -> {
                    java.util.Optional<TenantContract> lastTerminated = tenantContractRepository.findTopByPropertyIdAndRoomIsNullOrderByEndDateDesc(property.getId());
                    if (lastTerminated.isPresent() && lastTerminated.get().getStatus() == ContractStatus.TERMINATED) {
                        String msg = String.format("Căn này đã trả phòng ngày %s và đã chốt điện/nước theo chỉ số đồng hồ. Hoá đơn EVN kỳ này ghi nhận là chi phí công ty, không phát hành cho khách.", 
                                lastTerminated.get().getEndDate() != null ? lastTerminated.get().getEndDate().toString() : "gần đây");
                        throw new BusinessException("NO_ACTIVE_CONTRACT", msg);
                    }
                    throw new BusinessException("NO_ACTIVE_CONTRACT", "Nhà nguyên căn chưa có hợp đồng ACTIVE — không phát hành được hoá đơn cho khách.");
                });

        // Đơn giá giữ theo giấy (tổng tiền ÷ tổng kWh cả tháng) — không tính lại theo phần khách.
        BigDecimal unitPrice = bill.getUnitPrice();

        // Mốc bắt đầu tính cho khách: muộn hơn giữa "chốt kỳ trước" và "đồng hồ lúc đón khách".
        BigDecimal startReading = resolveTenantStartReading(bill, contract, prevReading, newReading);
        boolean midPeriodMoveIn = startReading.compareTo(prevReading) != 0;

        BigDecimal consumption;
        BigDecimal amount;
        if (midPeriodMoveIn) {
            consumption = newReading.subtract(startReading);
            amount = consumption.multiply(unitPrice).setScale(0, RoundingMode.HALF_UP);
        } else {
            // Kỳ đủ / khách cũ: giữ đúng số trên giấy, tránh lệch làm tròn đơn giá.
            consumption = BigDecimal.valueOf(bill.getTotalQuantity());
            amount = bill.getTotalAmount();
        }

        BigDecimal billQty = BigDecimal.valueOf(bill.getTotalQuantity());
        BigDecimal companyBorn = billQty.subtract(consumption);
        if (companyBorn.compareTo(BigDecimal.ZERO) < 0) {
            companyBorn = BigDecimal.ZERO;
        }
        bill.setBilledToTenantQuantity(consumption);
        bill.setCompanyBornQuantity(companyBorn);
        utilityBillRepository.save(bill);

        CreateUtilityInvoiceRequest request = CreateUtilityInvoiceRequest.builder()
                .type(UtilityTypeMapper.toApi(bill.getType()))
                .billingPeriod(bill.getBillingPeriod())
                .prevReading(startReading)
                .newReading(newReading)
                .consumption(consumption)
                .unitPrice(unitPrice)
                .amount(amount)
                .meterImageUrl(bill.getImageUrl())
                .build();
        validateInvoiceAmounts(request);
        validateBillingPeriodLock(property.getId(), null, request.getBillingPeriod(), bill.getType(), contract);
        return createAndSend(property, null, contract, bill.getType(), request, false, false, null);
    }

    @Override
    @Transactional
    public int issueElectricFromSavedReadings(UtilityBill bill) {
        Property property = bill.getProperty();
        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            return 0;
        }
        if (bill.getType() != UtilityType.ELECTRIC) {
            return 0;
        }
        YearMonth month = YearMonth.of(bill.getYear(), bill.getMonth());
        String normalized = ContractBillingCalendar.normalizePeriod(month);

        List<MeterReading> pending = loadUnissuedReadings(property.getId(), normalized);
        if (pending.isEmpty()) {
            notifyManagerAutoIssued(property, bill, 0, eligibleElectricRoomCount(property.getId(), month));
            return 0;
        }

        BigDecimal unitPrice = bill.getUnitPrice();
        LocalDate periodEnd = ContractBillingCalendar.periodEnd(bill.getYear(), bill.getMonth());
        List<PendingIssue> toIssue = new java.util.ArrayList<>();
        for (MeterReading reading : pending) {
            Long roomId = reading.getRoom() != null ? reading.getRoom().getId() : null;
            if (roomId == null) {
                continue;
            }
            TenantContract contract = tenantContractRepository
                    .findByRoomIdAndStatus(roomId, ContractStatus.ACTIVE)
                    .orElse(null);
            if (!ContractBillingCalendar.isContractInPeriod(contract, periodEnd)) {
                continue;
            }
            BigDecimal prev = reading.getPrevReading() != null ? reading.getPrevReading() : BigDecimal.ZERO;
            BigDecimal consumption = reading.getReading().subtract(prev);
            if (consumption.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            toIssue.add(new PendingIssue(reading, consumption));
        }

        assertBatchRoomSumWithinBill(property.getId(), bill, normalized, toIssue);

        int issued = 0;
        for (PendingIssue item : toIssue) {
            issueOneFromReading(bill, property, item.reading(), item.consumption(), unitPrice, UtilityType.ELECTRIC);
            issued++;
        }

        reconcileIfComplete(property.getId(), bill.getBillingPeriod(), UtilityType.ELECTRIC);
        int eligible = eligibleElectricRoomCount(property.getId(), month);
        notifyManagerAutoIssued(property, bill, issued, eligible);
        return issued;
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public UtilityInvoiceResponse issueElectricFromSavedReading(UtilityBill bill, Long meterReadingId) {
        Property property = bill.getProperty();
        MeterReading reading = meterReadingRepository.findById(meterReadingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bản chốt chỉ số ID=" + meterReadingId));
        if (reading.getUtilityInvoiceId() != null) {
            return toResponse(utilityInvoiceRepository.findById(reading.getUtilityInvoiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hoá đơn liên kết")));
        }
        if (reading.getRoom() == null) {
            throw new BusinessException("INVALID_READING", "Bản chốt không gắn phòng.");
        }
        LocalDate periodEnd = ContractBillingCalendar.periodEnd(bill.getYear(), bill.getMonth());
        TenantContract active = tenantContractRepository
                .findByRoomIdAndStatus(reading.getRoom().getId(), ContractStatus.ACTIVE)
                .orElse(null);
        if (!ContractBillingCalendar.isContractInPeriod(active, periodEnd)) {
            throw new BusinessException("CONTRACT_NOT_IN_PERIOD",
                    "Hợp đồng bắt đầu sau kỳ hoá đơn — không phát hành.");
        }
        BigDecimal prev = reading.getPrevReading() != null ? reading.getPrevReading() : BigDecimal.ZERO;
        BigDecimal consumption = reading.getReading().subtract(prev);
        if (consumption.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_READING", "Chỉ số mới phải lớn hơn chỉ số cũ.");
        }

        YearMonth month = YearMonth.of(bill.getYear(), bill.getMonth());
        String normalized = ContractBillingCalendar.normalizePeriod(month);
        assertRoomSumWithinBill(property.getId(), reading.getRoom().getId(), normalized,
                UtilityType.ELECTRIC, consumption);

        UtilityInvoiceResponse response = issueOneFromReading(
                bill, property, reading, consumption, bill.getUnitPrice(), UtilityType.ELECTRIC);
        reconcileIfComplete(property.getId(), bill.getBillingPeriod(), UtilityType.ELECTRIC);

        int eligible = eligibleElectricRoomCount(property.getId(), month);
        long done = utilityInvoiceRepository.countDistinctRoomsInvoiced(
                property.getId(), bill.getBillingPeriod(), UtilityType.ELECTRIC);
        notifyManagerAutoIssued(property, bill, (int) done, eligible);
        return response;
    }

    @Override
    @Transactional
    public int issueWaterFromSavedReadings(UtilityBill bill) {
        Property property = bill.getProperty();
        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            return 0;
        }
        if (bill.getType() != UtilityType.WATER) {
            return 0;
        }

        List<MeterReading> pending = loadUnissuedWaterReadings(property.getId());
        LocalDate periodEnd = ContractBillingCalendar.periodEnd(bill.getYear(), bill.getMonth());
        if (pending.isEmpty()) {
            notifyManagerAutoIssued(property, bill, 0, eligibleWaterRoomCount(property.getId(), periodEnd));
            return 0;
        }

        BigDecimal unitPrice = bill.getUnitPrice();
        List<PendingIssue> toIssue = new java.util.ArrayList<>();
        for (MeterReading reading : pending) {
            Long roomId = reading.getRoom() != null ? reading.getRoom().getId() : null;
            if (roomId == null) {
                continue;
            }
            TenantContract contract = tenantContractRepository
                    .findByRoomIdAndStatus(roomId, ContractStatus.ACTIVE)
                    .orElse(null);
            if (!ContractBillingCalendar.isContractInPeriod(contract, periodEnd)) {
                continue;
            }
            BigDecimal prev = reading.getPrevReading() != null ? reading.getPrevReading() : BigDecimal.ZERO;
            BigDecimal consumption = reading.getReading().subtract(prev);
            if (consumption.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            toIssue.add(new PendingIssue(reading, consumption));
        }

        assertBatchRoomSumWithinBill(property.getId(), bill, bill.getBillingPeriod(), toIssue, UtilityType.WATER);

        int issued = 0;
        for (PendingIssue item : toIssue) {
            issueOneFromReading(bill, property, item.reading(), item.consumption(), unitPrice, UtilityType.WATER);
            issued++;
        }

        reconcileIfComplete(property.getId(), bill.getBillingPeriod(), UtilityType.WATER);
        int eligible = eligibleWaterRoomCount(property.getId(), periodEnd);
        notifyManagerAutoIssued(property, bill, issued, eligible);
        return issued;
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public UtilityInvoiceResponse issueWaterFromSavedReading(UtilityBill bill, Long meterReadingId) {
        Property property = bill.getProperty();
        MeterReading reading = meterReadingRepository.findById(meterReadingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bản chốt chỉ số ID=" + meterReadingId));
        if (reading.getUtilityInvoiceId() != null) {
            return toResponse(utilityInvoiceRepository.findById(reading.getUtilityInvoiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hoá đơn liên kết")));
        }
        if (reading.getRoom() == null) {
            throw new BusinessException("INVALID_READING", "Bản chốt không gắn phòng.");
        }
        LocalDate periodEnd = ContractBillingCalendar.periodEnd(bill.getYear(), bill.getMonth());
        TenantContract active = tenantContractRepository
                .findByRoomIdAndStatus(reading.getRoom().getId(), ContractStatus.ACTIVE)
                .orElse(null);
        if (!ContractBillingCalendar.isContractInPeriod(active, periodEnd)) {
            throw new BusinessException("CONTRACT_NOT_IN_PERIOD",
                    "Hợp đồng bắt đầu sau kỳ hoá đơn — không phát hành.");
        }
        BigDecimal prev = reading.getPrevReading() != null ? reading.getPrevReading() : BigDecimal.ZERO;
        BigDecimal consumption = reading.getReading().subtract(prev);
        if (consumption.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_READING", "Chỉ số mới phải lớn hơn chỉ số cũ.");
        }

        assertRoomSumWithinBillForBill(property.getId(), reading.getRoom().getId(), bill,
                UtilityType.WATER, consumption);

        UtilityInvoiceResponse response = issueOneFromReading(
                bill, property, reading, consumption, bill.getUnitPrice(), UtilityType.WATER);
        reconcileIfComplete(property.getId(), bill.getBillingPeriod(), UtilityType.WATER);

        int eligible = eligibleWaterRoomCount(property.getId(), periodEnd);
        long done = utilityInvoiceRepository.countDistinctRoomsInvoiced(
                property.getId(), bill.getBillingPeriod(), UtilityType.WATER);
        notifyManagerAutoIssued(property, bill, (int) done, eligible);
        return response;
    }

    private UtilityInvoiceResponse issueOneFromReading(
            UtilityBill bill,
            Property property,
            MeterReading reading,
            BigDecimal consumption,
            BigDecimal unitPrice,
            UtilityType utilityType) {
        Room room = reading.getRoom();
        TenantContract contract = tenantContractRepository
                .findByRoomIdAndStatus(room.getId(), ContractStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException("NO_ACTIVE_CONTRACT",
                        "Phòng " + room.getRoomNumber() + " không có hợp đồng ACTIVE."));
        LocalDate periodEnd = ContractBillingCalendar.periodEnd(bill.getYear(), bill.getMonth());
        if (!ContractBillingCalendar.isContractInPeriod(contract, periodEnd)) {
            throw new BusinessException("CONTRACT_NOT_IN_PERIOD",
                    "Hợp đồng phòng " + room.getRoomNumber()
                            + " bắt đầu sau kỳ hoá đơn — không phát hành.");
        }

        BigDecimal prev = reading.getPrevReading() != null ? reading.getPrevReading() : BigDecimal.ZERO;
        BigDecimal amount = consumption.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

        CreateUtilityInvoiceRequest request = CreateUtilityInvoiceRequest.builder()
                .type(UtilityTypeMapper.toApi(utilityType))
                .billingPeriod(bill.getBillingPeriod())
                .prevReading(prev)
                .newReading(reading.getReading())
                .consumption(consumption)
                .unitPrice(unitPrice)
                .amount(amount)
                .meterImageUrl(reading.getImageUrl())
                .build();
        validateInvoiceAmounts(request);
        validateBillingPeriodLock(property.getId(), room.getId(), request.getBillingPeriod(),
                utilityType, contract);

        return createAndSend(property, room, contract, utilityType, request,
                false, true, reading.getId());
    }

    private void assertBatchRoomSumWithinBill(
            Long propertyId, UtilityBill bill, String normalizedPeriod, List<PendingIssue> toIssue) {
        assertBatchRoomSumWithinBill(propertyId, bill, normalizedPeriod, toIssue, UtilityType.ELECTRIC);
    }

    private void assertBatchRoomSumWithinBill(
            Long propertyId, UtilityBill bill, String normalizedPeriod, List<PendingIssue> toIssue,
            UtilityType type) {
        if (bill.getTotalQuantity() == null) {
            return;
        }
        RoomUtilitySumSnapshot existingSnap = sumActiveRoomConsumptions(
                propertyId, bill.getBillingPeriod(), type, null);
        if ((existingSnap.getSum() == null || existingSnap.getSum().compareTo(BigDecimal.ZERO) == 0)
                && !Objects.equals(bill.getBillingPeriod(), normalizedPeriod)) {
            RoomUtilitySumSnapshot alt = sumActiveRoomConsumptions(
                    propertyId, normalizedPeriod, type, null);
            if (alt.getSum() != null && alt.getSum().compareTo(BigDecimal.ZERO) > 0) {
                existingSnap = alt;
            }
        }

        BigDecimal existing = existingSnap.getSum() != null ? existingSnap.getSum() : BigDecimal.ZERO;
        BigDecimal incoming = toIssue.stream()
                .map(PendingIssue::consumption)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sum = existing.add(incoming);
        BigDecimal total = BigDecimal.valueOf(bill.getTotalQuantity());
        BigDecimal tolerance = BigDecimal.valueOf(roomSumTolerancePercent)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal cap = total.multiply(BigDecimal.ONE.add(tolerance));
        if (sum.compareTo(cap) <= 0) {
            return;
        }

        List<Map<String, Object>> roomDetails = new java.util.ArrayList<>();
        for (RoomUtilitySumSnapshot.RoomLine line : existingSnap.getRooms()) {
            Map<String, Object> row = new HashMap<>();
            row.put("roomId", line.getRoomId());
            row.put("roomNumber", line.getRoomNumber());
            row.put("consumption", line.getConsumption());
            roomDetails.add(row);
        }
        for (PendingIssue item : toIssue) {
            Map<String, Object> row = new HashMap<>();
            row.put("roomId", item.reading().getRoom() != null ? item.reading().getRoom().getId() : null);
            row.put("roomNumber", item.reading().getRoom() != null
                    ? item.reading().getRoom().getRoomNumber() : null);
            row.put("consumption", item.consumption());
            row.put("note", "bản chốt chưa phát hành");
            roomDetails.add(row);
        }
        Map<String, Object> details = new HashMap<>();
        details.put("sum", sum);
        details.put("billTotal", total);
        details.put("cap", cap);
        details.put("tolerancePercent", roomSumTolerancePercent);
        details.put("rooms", roomDetails);
        throw new BusinessException("ROOM_SUM_EXCEEDS_BILL", String.format(
                "Tổng tiêu thụ các phòng (%s) vượt quá giấy nhà nước (%s, trần cho phép %s). "
                        + "Không phát hành phòng nào — kiểm lại chỉ số các phòng nghi đọc nhầm.",
                fmtQty(sum), fmtQty(total), fmtQty(cap)),
                details);
    }

    private void assertRoomSumWithinBillForBill(Long propertyId, Long roomId, UtilityBill bill,
                                                UtilityType type, BigDecimal newConsumption) {
        if (bill == null || bill.getTotalQuantity() == null) {
            return;
        }
        RoomUtilitySumSnapshot othersSnap = sumActiveRoomConsumptions(
                propertyId, bill.getBillingPeriod(), type, roomId);
        BigDecimal others = othersSnap.getSum() != null ? othersSnap.getSum() : BigDecimal.ZERO;
        BigDecimal total = BigDecimal.valueOf(bill.getTotalQuantity());
        BigDecimal tolerance = BigDecimal.valueOf(roomSumTolerancePercent)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal cap = total.multiply(BigDecimal.ONE.add(tolerance));
        BigDecimal sum = others.add(newConsumption != null ? newConsumption : BigDecimal.ZERO);
        if (sum.compareTo(cap) <= 0) {
            return;
        }
        List<Map<String, Object>> roomDetails = new java.util.ArrayList<>();
        for (RoomUtilitySumSnapshot.RoomLine line : othersSnap.getRooms()) {
            Map<String, Object> row = new HashMap<>();
            row.put("roomId", line.getRoomId());
            row.put("roomNumber", line.getRoomNumber());
            row.put("consumption", line.getConsumption());
            roomDetails.add(row);
        }
        Map<String, Object> current = new HashMap<>();
        current.put("roomId", roomId);
        current.put("consumption", newConsumption);
        current.put("note", "phòng đang ghi (có thể không phải phòng sai)");
        roomDetails.add(current);
        Map<String, Object> details = new HashMap<>();
        details.put("sum", sum);
        details.put("billTotal", total);
        details.put("cap", cap);
        details.put("tolerancePercent", roomSumTolerancePercent);
        details.put("rooms", roomDetails);
        throw new BusinessException("ROOM_SUM_EXCEEDS_BILL", String.format(
                "Tổng tiêu thụ các phòng (%s) vượt quá giấy nhà nước (%s, trần cho phép %s). "
                        + "Lỗi thường nằm ở phòng đã ghi trước — kiểm lại chỉ số các phòng trong details.rooms, "
                        + "không chỉ sửa phòng đang bị chặn.",
                fmtQty(sum), fmtQty(total), fmtQty(cap)),
                details);
    }

    private List<MeterReading> loadUnissuedReadings(Long propertyId, String normalizedPeriod) {
        List<MeterReading> result = new java.util.ArrayList<>();
        Set<Long> seen = new HashSet<>();
        YearMonth ym = YearMonth.parse(normalizedPeriod);
        for (String alias : List.of(normalizedPeriod,
                String.format("%02d/%d", ym.getMonthValue(), ym.getYear()))) {
            for (MeterReading r : meterReadingRepository
                    .findByPropertyIdAndUtilityTypeAndPeriodAndUtilityInvoiceIdIsNull(
                            propertyId, UtilityType.ELECTRIC, alias)) {
                if (r.getId() != null && seen.add(r.getId())) {
                    result.add(r);
                }
            }
        }
        return result;
    }

    /** Nước: lấy mọi bản chốt chưa phát hành — không lọc theo period. */
    private List<MeterReading> loadUnissuedWaterReadings(Long propertyId) {
        return meterReadingRepository.findByPropertyIdAndUtilityTypeAndUtilityInvoiceIdIsNull(
                propertyId, UtilityType.WATER);
    }

    private int eligibleElectricRoomCount(Long propertyId, YearMonth month) {
        return meterReadingService.listEligibleForPeriod(
                propertyId, ContractBillingCalendar.normalizePeriod(month), UtilityType.ELECTRIC).size();
    }

    private int eligibleWaterRoomCount(Long propertyId, LocalDate periodEnd) {
        return (int) tenantContractRepository.findActiveWithTenantByPropertyId(propertyId).stream()
                .filter(c -> c.getRoom() != null)
                .filter(c -> ContractBillingCalendar.isContractInPeriod(c, periodEnd))
                .count();
    }

    private void notifyManagerAutoIssued(Property property, UtilityBill bill, int issued, int eligible) {
        UUID managerId = property.getOperationManagerId();
        if (managerId == null) {
            return;
        }
        int total = Math.max(eligible, issued);
        String periodLabel = String.format("%02d/%d", bill.getMonth(), bill.getYear());
        String isoPeriod = ContractBillingCalendar.normalizePeriod(YearMonth.of(bill.getYear(), bill.getMonth()));
        boolean water = bill.getType() == UtilityType.WATER;
        String typeLabel = water ? "nước" : "điện";
        String tab = water ? "water" : "electricity";
        String title = "Đã phát hành hoá đơn " + typeLabel;
        // Quản lý: không kèm số tiền
        String content = String.format("Đã phát hành hoá đơn %s cho %d/%d phòng nhà %s",
                typeLabel, issued, total,
                property.getPropertyName() != null ? property.getPropertyName() : ("#" + property.getId()));
        String paramsJson = "{\"propertyId\":" + property.getId()
                + ",\"tab\":\"" + tab + "\",\"period\":\"" + isoPeriod + "\"}";
        String dedupeKey = "utility-auto-issued-mgr:" + bill.getId() + ":" + issued;
        if (!notificationRepository.existsByUserIdAndDedupeKey(managerId, dedupeKey)) {
            notificationRepository.save(Notification.builder()
                    .userId(managerId)
                    .title(title)
                    .content(content)
                    .type("UTILITY_INVOICE_AUTO_ISSUED_MANAGER")
                    .screen("UtilityBilling")
                    .paramsJson(paramsJson)
                    .dedupeKey(dedupeKey)
                    .read(false)
                    .build());
        }
        Map<String, Object> data = new HashMap<>();
        data.put("type", "UTILITY_INVOICE_AUTO_ISSUED_MANAGER");
        data.put("screen", "UtilityBilling");
        data.put("propertyId", property.getId());
        data.put("period", isoPeriod);
        data.put("tab", tab);
        userPushTokenService.sendToUser(managerId, title, content, data);
    }

    private record PendingIssue(MeterReading reading, BigDecimal consumption) {}

    /**
     * Chỉ số cũ trên hoá đơn khách: dùng mốc đón khách nếu khách dọn vào trong kỳ giấy,
     * ngược lại giữ đầu kỳ trên giấy. Tránh tiêu thụ âm khi mốc đón nằm ngoài khoảng chỉ số.
     * <p>
     * Ưu tiên {@code handoverAt} khi có: chỉ áp dụng nếu thời điểm đón không sau cuối tháng kỳ bill
     * (đón kỳ sau → bỏ qua). Thiếu mốc thời gian → so theo số chỉ (công tơ chỉ chạy tiến).
     */
    private BigDecimal resolveTenantStartReading(
            UtilityBill bill, TenantContract contract, BigDecimal prevReading, BigDecimal newReading) {
        LocalDateTime handoverAt = bill.getType() == UtilityType.ELECTRIC
                ? contract.getElectricMeterCapturedAt()
                : contract.getWaterMeterCapturedAt();
        BigDecimal handoverReading = bill.getType() == UtilityType.ELECTRIC
                ? contract.getInitialElectricReading()
                : contract.getInitialWaterReading();

        if (handoverReading == null
                || handoverReading.compareTo(prevReading) <= 0
                || handoverReading.compareTo(newReading) > 0) {
            return prevReading;
        }

        // Có thời điểm đón: nếu đón sau hết tháng của kỳ bill thì không thuộc kỳ này.
        if (handoverAt != null && bill.getYear() != null && bill.getMonth() != null) {
            java.time.LocalDate periodEnd = java.time.YearMonth.of(bill.getYear(), bill.getMonth()).atEndOfMonth();
            if (handoverAt.toLocalDate().isAfter(periodEnd)) {
                return prevReading;
            }
        }

        return handoverReading;
    }

    /**
     * Chốt đối soát nhà chia phòng khi đã đọc đủ phòng (pending rỗng).
     * Idempotent: mỗi lần tính lại từ tổng hoá đơn phòng, không cộng dồn.
     * Không chặn phát hành khi có hao hụt.
     */
    private void reconcileIfComplete(Long propertyId, String period, UtilityType type) {
        YearMonth month = ContractBillingCalendar.parsePeriod(period).orElse(null);
        UtilityBill bill = null;
        if (month != null) {
            bill = utilityBillRepository
                    .findByPropertyIdAndMonthAndYearAndTypeAndStatus(
                            propertyId,
                            month.getMonthValue(),
                            month.getYear(),
                            type,
                            UtilityBillStatus.PUBLISHED)
                    .orElse(null);
        }
        if (bill == null || bill.getTotalQuantity() == null) {
            return;
        }
        // Nhà nguyên căn đã chốt trong createFromWholeHouseBill — không đụng.
        if (Boolean.TRUE.equals(bill.getProperty().getWholeHouse())) {
            return;
        }

        String eligiblePeriod = ContractBillingCalendar.normalizePeriod(
                YearMonth.of(bill.getYear(), bill.getMonth()));
        var eligible = meterReadingService.listEligibleForPeriod(propertyId, eligiblePeriod, type);
        if (eligible.isEmpty()) {
            return;
        }
        Set<Long> eligibleRoomIds = eligible.stream()
                .map(com.sep490.slms2026.dto.response.PendingMeterReadingItem::getRoomId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        RoomUtilitySumSnapshot snapshot = sumActiveRoomConsumptions(
                propertyId, bill.getBillingPeriod(), type, null);
        Set<Long> invoicedRoomIds = snapshot.getRooms().stream()
                .map(RoomUtilitySumSnapshot.RoomLine::getRoomId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (!invoicedRoomIds.containsAll(eligibleRoomIds)) {
            return;
        }

        BigDecimal billed = snapshot.getSum() != null ? snapshot.getSum() : BigDecimal.ZERO;
        BigDecimal total = BigDecimal.valueOf(bill.getTotalQuantity());
        BigDecimal companyBorn = total.subtract(billed).max(BigDecimal.ZERO);
        bill.setBilledToTenantQuantity(billed);
        bill.setCompanyBornQuantity(companyBorn);
        utilityBillRepository.save(bill);

        maybeAlertAbnormalLoss(bill, billed, companyBorn, total);
    }

    /**
     * Chặn tổng phòng vượt trần giấy × (1 + tolerance). Chưa có giấy kỳ này → cho qua.
     */
    private void assertRoomSumWithinBill(Long propertyId, Long roomId, String period,
                                         UtilityType type, BigDecimal newConsumption) {
        YearMonth month = ContractBillingCalendar.parsePeriod(period).orElse(null);
        if (month == null) {
            return;
        }
        UtilityBill bill = utilityBillRepository
                .findByPropertyIdAndMonthAndYearAndTypeAndStatus(
                        propertyId,
                        month.getMonthValue(),
                        month.getYear(),
                        type,
                        UtilityBillStatus.PUBLISHED)
                .orElse(null);
        if (bill == null || bill.getTotalQuantity() == null) {
            return;
        }

        RoomUtilitySumSnapshot othersSnap = sumActiveRoomConsumptions(
                propertyId, bill.getBillingPeriod(), type, roomId);
        BigDecimal others = othersSnap.getSum() != null ? othersSnap.getSum() : BigDecimal.ZERO;
        BigDecimal total = BigDecimal.valueOf(bill.getTotalQuantity());
        BigDecimal tolerance = BigDecimal.valueOf(roomSumTolerancePercent)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal cap = total.multiply(BigDecimal.ONE.add(tolerance));
        BigDecimal sum = others.add(newConsumption != null ? newConsumption : BigDecimal.ZERO);

        if (sum.compareTo(cap) <= 0) {
            return;
        }

        List<Map<String, Object>> roomDetails = new java.util.ArrayList<>();
        for (RoomUtilitySumSnapshot.RoomLine line : othersSnap.getRooms()) {
            Map<String, Object> row = new HashMap<>();
            row.put("roomId", line.getRoomId());
            row.put("roomNumber", line.getRoomNumber());
            row.put("consumption", line.getConsumption());
            roomDetails.add(row);
        }
        Map<String, Object> current = new HashMap<>();
        current.put("roomId", roomId);
        current.put("consumption", newConsumption);
        current.put("note", "phòng đang ghi (có thể không phải phòng sai)");
        roomDetails.add(current);

        Map<String, Object> details = new HashMap<>();
        details.put("sum", sum);
        details.put("billTotal", total);
        details.put("cap", cap);
        details.put("tolerancePercent", roomSumTolerancePercent);
        details.put("rooms", roomDetails);

        throw new BusinessException("ROOM_SUM_EXCEEDS_BILL", String.format(
                "Tổng tiêu thụ các phòng (%s) vượt quá giấy nhà nước (%s, trần cho phép %s). "
                        + "Lỗi thường nằm ở phòng đã ghi trước — kiểm lại chỉ số các phòng trong details.rooms, "
                        + "không chỉ sửa phòng đang bị chặn.",
                fmtQty(sum), fmtQty(total), fmtQty(cap)),
                details);
    }

    @Override
    @Transactional(readOnly = true)
    public RoomUtilitySumSnapshot sumActiveRoomConsumptions(
            Long propertyId, String period, UtilityType type, Long excludeRoomId) {
        List<RoomUtilitySumSnapshot.RoomLine> lines = new java.util.ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (UtilityInvoice i : utilityInvoiceRepository.findByFilters(propertyId, period, type)) {
            if (i.getRoom() == null || i.getConsumption() == null) {
                continue;
            }
            if (excludeRoomId != null && Objects.equals(i.getRoom().getId(), excludeRoomId)) {
                continue;
            }
            if (!isActiveUtilityInvoice(i)) {
                continue;
            }
            sum = sum.add(i.getConsumption());
            lines.add(RoomUtilitySumSnapshot.RoomLine.builder()
                    .roomId(i.getRoom().getId())
                    .roomNumber(i.getRoom().getRoomNumber())
                    .consumption(i.getConsumption())
                    .build());
        }
        return RoomUtilitySumSnapshot.builder().sum(sum).rooms(lines).build();
    }

    private static String fmtQty(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        return v.stripTrailingZeros().toPlainString();
    }

    /** Bỏ hoá đơn đã huỷ (tenant invoice CANCELLED) để đối soát không cộng dồn khi phát hành lại. */
    private boolean isActiveUtilityInvoice(UtilityInvoice ui) {
        return tenantInvoiceRepository.findByUtilityInvoiceId(ui.getId())
                .map(ti -> ti.getStatus() != com.sep490.slms2026.enums.TenantInvoiceStatus.CANCELLED)
                .orElse(true);
    }

    private void maybeAlertAbnormalLoss(
            UtilityBill bill, BigDecimal billed, BigDecimal companyBorn, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal lossRatio = companyBorn.divide(total, 4, RoundingMode.HALF_UP);
        BigDecimal threshold = BigDecimal.valueOf(lossAlertThresholdPercent)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        if (lossRatio.compareTo(threshold) < 0) {
            return;
        }

        // Baseline 3 kỳ gần nhất — chỉ để ghi “bình thường ~X%” trong nội dung cảnh báo.
        int normalPercent = 5;
        List<UtilityBill> previous = utilityBillRepository.findPreviousWithCompanyBorn(
                bill.getProperty().getId(), bill.getType(), bill.getYear(), bill.getMonth());
        BigDecimal histSum = BigDecimal.ZERO;
        int histCount = 0;
        for (UtilityBill prev : previous.stream().limit(3).toList()) {
            if (prev.getTotalQuantity() == null || prev.getTotalQuantity() <= 0
                    || prev.getCompanyBornQuantity() == null) {
                continue;
            }
            histSum = histSum.add(prev.getCompanyBornQuantity()
                    .divide(BigDecimal.valueOf(prev.getTotalQuantity()), 4, RoundingMode.HALF_UP));
            histCount++;
        }
        if (histCount > 0) {
            normalPercent = histSum.divide(BigDecimal.valueOf(histCount), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();
        }

        Property property = bill.getProperty();
        boolean water = bill.getType() == UtilityType.WATER;
        String kind = water ? "nước" : "điện";
        String unit = water ? "m³" : "kWh";
        int lossPercent = lossRatio.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
        String periodLabel = bill.getMonth() + "/" + bill.getYear();
        String title = "⚠️ Hao hụt " + kind + " bất thường — "
                + property.getPropertyName() + ", kỳ " + periodLabel;
        String content = String.format(
                "Giấy nhà nước %,d %s, tổng các phòng %s %s → hao hụt %s %s (%d%%). "
                        + "Bình thường ở mức ~%d%%. Kiểm tra rò / công tơ / phòng trống có ai dùng không.",
                total.intValue(),
                unit,
                billed.stripTrailingZeros().toPlainString(),
                unit,
                companyBorn.stripTrailingZeros().toPlainString(),
                unit,
                lossPercent,
                normalPercent);

        String paramsJson = "{\"propertyId\":" + property.getId() + ",\"billId\":" + bill.getId() + "}";
        String dedupeKey = "utility-loss-alert:" + bill.getId();

        UUID managerId = property.getOperationManagerId();
        notifyLossAlert(managerId, title, content, paramsJson, dedupeKey);
        for (User admin : userRepository.findByRoleAndStatus(Role.ROLE_ADMIN, UserStatus.ACTIVE)) {
            notifyLossAlert(admin.getId(), title, content, paramsJson, dedupeKey);
        }
    }

    private void notifyLossAlert(UUID userId, String title, String content,
                                 String paramsJson, String dedupeKey) {
        if (userId == null) {
            return;
        }
        if (notificationRepository.existsByUserIdAndDedupeKey(userId, dedupeKey)) {
            return;
        }
        try {
            notificationRepository.save(Notification.builder()
                    .userId(userId)
                    .title(title)
                    .content(content)
                    .type("UTILITY_LOSS_ALERT")
                    .screen("UtilityBilling")
                    .paramsJson(paramsJson)
                    .dedupeKey(dedupeKey)
                    .read(false)
                    .build());
        } catch (DataIntegrityViolationException e) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("type", "UTILITY_LOSS_ALERT");
        data.put("screen", "UtilityBilling");
        data.put("params", paramsJson);
        userPushTokenService.sendToUser(userId, title, content, data);
    }

    @Override
    @Transactional(readOnly = true)
    public UtilityInvoiceHistoryResponse listInvoices(Long propertyId, String period, String type) {
        propertyAccessService.assertCanManageProperty(propertyId);
        loadProperty(propertyId);

        UtilityType utilityType = type == null || type.isBlank() ? null : UtilityTypeMapper.fromApi(type);
        String periodFilter = period == null || period.isBlank() ? null : period.trim();

        List<UtilityInvoice> invoices = utilityInvoiceRepository.findByFilters(
                propertyId, periodFilter, utilityType);

        List<UtilityInvoiceResponse> items = invoices.stream().map(this::toResponse).toList();
        BigDecimal totalAmount = items.stream()
                .map(UtilityInvoiceResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Set<Long> roomIds = new HashSet<>();
        for (UtilityInvoice invoice : invoices) {
            if (invoice.getRoom() != null) {
                roomIds.add(invoice.getRoom().getId());
            }
        }

        return UtilityInvoiceHistoryResponse.builder()
                .items(items)
                .totalCount(items.size())
                .totalAmount(totalAmount)
                .roomCount(roomIds.isEmpty() && !items.isEmpty() ? 1 : roomIds.size())
                .build();
    }

    private UtilityInvoiceResponse createAndSend(
            Property property,
            Room room,
            TenantContract contract,
            UtilityType utilityType,
            CreateUtilityInvoiceRequest request,
            boolean requireMeterPhoto,
            boolean autoIssued,
            Long existingReadingId) {

        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        LocalDateTime now = LocalDateTime.now();
        if (requireMeterPhoto) {
            ensureMeterPhotoOrOverride(property, room, contract, utilityType, request, user);
        }

        UtilityInvoice invoice = utilityInvoiceRepository.save(UtilityInvoice.builder()
                .property(property)
                .room(room)
                .tenantContract(contract)
                .utilityType(utilityType)
                .billingPeriod(request.getBillingPeriod())
                .prevReading(request.getPrevReading())
                .newReading(request.getNewReading())
                .consumption(request.getConsumption())
                .unitPrice(request.getUnitPrice())
                .amount(request.getAmount())
                .meterImageUrl(request.getMeterImageUrl())
                .status(UtilityInvoiceStatus.SENT)
                .sentAt(now)
                .createdBy(user.getId())
                .createdAt(now)
                .build());

        if (existingReadingId != null) {
            meterReadingRepository.findById(existingReadingId).ifPresent(reading -> {
                reading.setUtilityInvoiceId(invoice.getId());
                reading.setPrevReading(request.getPrevReading());
                reading.setReading(request.getNewReading());
                if (request.getMeterImageUrl() != null && !request.getMeterImageUrl().isBlank()) {
                    reading.setImageUrl(request.getMeterImageUrl());
                }
                meterReadingRepository.save(reading);
            });
        } else {
            YearMonth ym = ContractBillingCalendar.parsePeriod(request.getBillingPeriod()).orElse(null);
            String normalizedPeriod = ym != null
                    ? ContractBillingCalendar.normalizePeriod(ym)
                    : request.getBillingPeriod();
            Long roomId = room != null ? room.getId() : null;
            MeterReading existing = findExistingReading(
                    property.getId(), roomId, utilityType, normalizedPeriod).orElse(null);
            if (existing != null) {
                existing.setReading(request.getNewReading());
                existing.setPrevReading(request.getPrevReading());
                existing.setImageUrl(request.getMeterImageUrl());
                existing.setPeriod(normalizedPeriod);
                existing.setUtilityInvoiceId(invoice.getId());
                existing.setRecordedAt(now);
                existing.setRecordedBy(user.getId());
                meterReadingRepository.save(existing);
            } else {
                meterReadingRepository.save(MeterReading.builder()
                        .property(property)
                        .room(room)
                        .utilityType(utilityType)
                        .period(normalizedPeriod)
                        .prevReading(request.getPrevReading())
                        .reading(request.getNewReading())
                        .imageUrl(request.getMeterImageUrl())
                        .recordedAt(now)
                        .recordedBy(user.getId())
                        .utilityInvoiceId(invoice.getId())
                        .build());
            }
        }

        if (contract != null) {
            tenantBillingService.createFromUtilityInvoice(invoice, contract);
            invoiceDisputeService.attachReplacementIfPresent(invoice);

            if (contract.getTenant() != null && contract.getTenant().getUser() != null) {
                java.util.UUID tenantId = contract.getTenant().getUser().getId();
                String typeStr = utilityType == UtilityType.ELECTRIC ? "Điện" : "Nước";
                String notifType = autoIssued ? "UTILITY_INVOICE_AUTO_ISSUED" : "UTILITY_INVOICE_CREATED";
                String title;
                String content;
                if (autoIssued) {
                    String periodLabel = request.getBillingPeriod();
                    YearMonth ym = ContractBillingCalendar.parsePeriod(request.getBillingPeriod()).orElse(null);
                    if (ym != null) {
                        periodLabel = String.format("%02d/%d", ym.getMonthValue(), ym.getYear());
                    }
                    String unit = utilityType == UtilityType.WATER ? "m³" : "kWh";
                    title = "Hoá đơn " + typeStr.toLowerCase() + " kỳ " + periodLabel;
                    content = String.format("Hoá đơn %s · %s %s · %sđ",
                            typeStr.toLowerCase(),
                            formatQty(request.getConsumption()),
                            unit,
                            formatCurrency(request.getAmount()));
                } else {
                    title = "Hoá đơn " + typeStr + " mới";
                    boolean adminIssued = Boolean.TRUE.equals(property.getWholeHouse()) && room == null;
                    content = adminIssued
                            ? String.format("Admin vừa phát hành hoá đơn %s kỳ %s. Số tiền: %,dđ.",
                                    typeStr, request.getBillingPeriod(), request.getAmount().longValue())
                            : String.format("Quản lý vừa chốt số và phát hành hoá đơn %s kỳ %s. Số tiền: %,dđ.",
                                    typeStr, request.getBillingPeriod(), request.getAmount().longValue());
                }

                String dedupeKey = "utility-invoice:" + invoice.getId() + ":created";
                if (!notificationRepository.existsByUserIdAndDedupeKey(tenantId, dedupeKey)) {
                    Notification notification = Notification.builder()
                            .userId(tenantId)
                            .title(title)
                            .content(content)
                            .type(notifType)
                            .screen("InvoiceList")
                            .paramsJson("{\"invoiceId\": " + invoice.getId() + "}")
                            .dedupeKey(dedupeKey)
                            .read(false)
                            .build();
                    notificationRepository.save(notification);
                }

                Map<String, Object> data = new HashMap<>();
                data.put("screen", "InvoiceList");
                data.put("type", notifType);
                data.put("invoiceId", invoice.getId());
                userPushTokenService.sendToUser(tenantId, title, content, data);
            }
        }

        UtilityInvoiceResponse response = toResponse(invoice);
        if (contract != null && contract.getTenant() != null && contract.getTenant().getUser() != null) {
            response.setTenantFullName(contract.getTenant().getUser().getFullName());
            response.setTenantPhone(contract.getTenant().getUser().getPhoneNumber());
        }
        return response;
    }

    private Optional<MeterReading> findExistingReading(
            Long propertyId, Long roomId, UtilityType type, String period) {
        if (roomId == null) {
            return meterReadingRepository
                    .findTopByPropertyIdAndRoomIsNullAndUtilityTypeAndPeriodOrderByRecordedAtDesc(
                            propertyId, type, period);
        }
        return meterReadingRepository
                .findTopByPropertyIdAndRoomIdAndUtilityTypeAndPeriodOrderByRecordedAtDesc(
                        propertyId, roomId, type, period);
    }

    private static String formatQty(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            return stripped.toBigInteger().toString();
        }
        return stripped.toPlainString();
    }

    private static String formatCurrency(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return String.format("%,d", value.setScale(0, RoundingMode.HALF_UP).longValue()).replace(',', '.');
    }

    private void ensureMeterPhotoOrOverride(
            Property property,
            Room room,
            TenantContract contract,
            UtilityType utilityType,
            CreateUtilityInvoiceRequest request,
            CustomUserDetails user) {
        boolean hasRequestPhoto = request.getMeterImageUrl() != null && !request.getMeterImageUrl().isBlank();
        boolean hasStoredPhoto = meterReadingService.hasPhoto(
                property.getId(),
                room != null ? room.getId() : null,
                utilityType,
                request.getBillingPeriod());
        if (hasRequestPhoto || hasStoredPhoto) {
            return;
        }

        Long contractId = contract != null ? contract.getId() : null;
        String kind = utilityType == UtilityType.ELECTRIC ? "ELEC" : "WATER";
        boolean overridden = meterOverrideService.consumeOverrideIfPresent(
                user.getId(), contractId, kind, request.getOverrideToken(),
                request.getNewReading(), request.getOverrideReason());
        if (overridden) {
            return;
        }

        notifyManagerMeterPhotoBlocked(property, room, request.getBillingPeriod());
        throw new BusinessException("METER_PHOTO_REQUIRED",
                "Chưa có ảnh công tơ kỳ này — không thể phát hành hoá đơn điện/nước. Chụp ảnh hoặc dùng mã override của admin.");
    }

    private void notifyManagerMeterPhotoBlocked(Property property, Room room, String period) {
        java.util.UUID managerId = property.getOperationManagerId();
        if (managerId == null) {
            managerId = property.getOperationManagerId();
        }
        if (managerId == null) {
            return;
        }
        String roomLabel = room != null ? room.getRoomNumber() : "nguyên căn";
        String title = "⛔ Không phát hành hoá đơn — thiếu ảnh công tơ";
        String content = "Nhà " + property.getPropertyName() + " · Phòng " + roomLabel
                + " kỳ " + period + " chưa có ảnh công tơ nên hoá đơn điện/nước bị chặn.";
        notificationRepository.save(Notification.builder()
                .userId(managerId)
                .title(title)
                .content(content)
                .type("METER_READING_DUE")
                .screen("MeterReadingPending")
                .paramsJson("{\"propertyId\":" + property.getId() + "}")
                .read(false)
                .build());
        userPushTokenService.sendToUser(managerId, title, content, Map.of(
                "type", "METER_READING_DUE",
                "screen", "MeterReadingPending",
                "propertyId", property.getId()));
    }

    private void validateBillingPeriodLock(Long propertyId, Long roomId, String billingPeriod,
                                          UtilityType utilityType, TenantContract contract) {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        java.time.YearMonth periodMonth = ContractBillingCalendar.parsePeriod(billingPeriod).orElse(null);
        if (periodMonth != null && java.time.YearMonth.from(today).equals(periodMonth)) {
            java.time.LocalDate deadline = contract != null
                    ? ContractBillingCalendar.dueDate(
                            periodMonth,
                            ContractBillingCalendar.billingDayOfMonth(contract),
                            graceDaysValue)
                    : periodMonth.atEndOfMonth();
            if (today.isAfter(deadline)) {
                throw new BusinessException("UTILITY_WINDOW_CLOSED",
                        "Đã quá hạn chót (" + deadline + "), không thể tạo mới hoá đơn điện/nước cho kỳ này.");
            }
        }

        List<UtilityInvoice> utilities = utilityInvoiceRepository.findByFilters(propertyId, billingPeriod, utilityType);
        if (roomId != null) {
            utilities = utilities.stream().filter(u -> u.getRoom() != null && u.getRoom().getId().equals(roomId)).toList();
        } else {
            utilities = utilities.stream().filter(u -> u.getRoom() == null).toList();
        }

        for (UtilityInvoice ui : utilities) {
            var ti = tenantInvoiceRepository.findByUtilityInvoiceId(ui.getId());
            if (ti.isPresent() && ti.get().getStatus() == com.sep490.slms2026.enums.TenantInvoiceStatus.CANCELLED) {
                continue;
            }
            String target = (roomId != null) ? ("Phòng " + ui.getRoom().getRoomNumber()) : "Nhà nguyên căn";
            String typeStr = (utilityType == UtilityType.ELECTRIC) ? "điện" : "nước";
            throw new BusinessException("INVOICE_ALREADY_EXISTS",
                    target + " đã nhận hoá đơn " + typeStr + " của kỳ " + billingPeriod + ".");
        }
    }

    private void validateRoomBillable(Room room) {
        if (room.getStatus() == RoomStatus.DISABLED) {
            throw new BusinessException("Phòng đang ngưng khai thác — không tạo hóa đơn");
        }
    }

    private void validateInvoiceAmounts(CreateUtilityInvoiceRequest request) {
        BigDecimal expectedConsumption = request.getNewReading().subtract(request.getPrevReading());
        // Cho phép ±1 đơn vị: prevReading cũ còn lẻ hoặc làm tròn đồng hồ (phần đỏ) trên FE.
        if (expectedConsumption.subtract(request.getConsumption()).abs().compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException("CONSUMPTION_MISMATCH",
                    "Tiêu thụ không khớp (chỉ số mới − chỉ số cũ)");
        }
        BigDecimal expectedAmount = request.getConsumption()
                .multiply(request.getUnitPrice())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal actualAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        // Cho phép ±1đ: đơn giá EVN/nước chưa làm tròn × tiêu thụ có thể lệch 1đ so với tổng hoá đơn nhà nước.
        if (expectedAmount.subtract(actualAmount).abs().compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException("AMOUNT_MISMATCH",
                    "Thành tiền không khớp (tiêu thụ × đơn giá)");
        }
        if (request.getNewReading().compareTo(request.getPrevReading()) < 0) {
            throw new BusinessException("READING_ORDER_INVALID",
                    "Chỉ số mới phải lớn hơn hoặc bằng chỉ số cũ");
        }
    }

    private Property loadProperty(Long propertyId) {
        return propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy tòa nhà với ID: " + propertyId));
    }

    private Room loadRoom(Long propertyId, Long roomId) {
        return roomRepository.findByIdAndPropertyIdAndDeletedIsFalse(roomId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phòng ID=" + roomId + " trong tòa nhà ID=" + propertyId));
    }

    private UtilityInvoiceResponse toResponse(UtilityInvoice invoice) {
        UtilityInvoiceResponse.UtilityInvoiceResponseBuilder builder = UtilityInvoiceResponse.builder()
                .id(invoice.getId())
                .propertyId(invoice.getProperty().getId())
                .propertyName(invoice.getProperty().getPropertyName())
                .type(UtilityTypeMapper.toApi(invoice.getUtilityType()))
                .billingPeriod(invoice.getBillingPeriod())
                .prevReading(invoice.getPrevReading())
                .newReading(invoice.getNewReading())
                .consumption(invoice.getConsumption())
                .unitPrice(invoice.getUnitPrice())
                .amount(invoice.getAmount())
                .meterImageUrl(invoice.getMeterImageUrl())
                .status(invoice.getStatus())
                .sentAt(invoice.getSentAt())
                .createdAt(invoice.getCreatedAt());

        if (invoice.getRoom() != null) {
            builder.roomId(invoice.getRoom().getId())
                    .roomNumber(invoice.getRoom().getRoomNumber());
        }

        if (invoice.getTenantContract() != null && invoice.getTenantContract().getTenant() != null) {
            var tenantUser = invoice.getTenantContract().getTenant().getUser();
            if (tenantUser != null) {
                builder.tenantFullName(tenantUser.getFullName())
                        .tenantPhone(tenantUser.getPhoneNumber());
            }
        }

        builder.tenantViewedAt(invoice.getTenantViewedAt());
        if (invoice.getTenantViewedAt() != null) {
            builder.tenantViewed(true);
        } else {
            builder.tenantViewed(notificationRepository
                    .findByDedupeKey("utility-invoice:" + invoice.getId() + ":created")
                    .map(Notification::isRead)
                    .orElse(null));
        }

        return builder.build();
    }
}

