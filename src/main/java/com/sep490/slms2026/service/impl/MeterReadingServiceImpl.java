package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.request.CreateMeterReadingRequest;
import com.sep490.slms2026.dto.request.SaveMeterReadingRequest;
import com.sep490.slms2026.dto.response.MeterReadingResponse;
import com.sep490.slms2026.dto.response.PendingMeterReadingItem;
import com.sep490.slms2026.dto.response.SavedMeterReadingItem;
import com.sep490.slms2026.dto.response.SavedMeterReadingListResponse;
import com.sep490.slms2026.entity.MeterReading;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.UtilityBill;
import com.sep490.slms2026.entity.UtilityInvoice;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.RoomStatus;
import com.sep490.slms2026.enums.UtilityBillStatus;
import com.sep490.slms2026.enums.UtilityInvoiceStatus;
import com.sep490.slms2026.enums.UtilityType;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.MeterReadingRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.UtilityBillRepository;
import com.sep490.slms2026.repository.UtilityInvoiceRepository;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.MeterOverrideService;
import com.sep490.slms2026.service.MeterReadingService;
import com.sep490.slms2026.service.PropertyAccessService;
import com.sep490.slms2026.service.UtilityInvoiceService;
import com.sep490.slms2026.util.ContractBillingCalendar;
import com.sep490.slms2026.util.UtilityTypeMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class MeterReadingServiceImpl implements MeterReadingService {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final PropertyRepository propertyRepository;
    private final RoomRepository roomRepository;
    private final MeterReadingRepository meterReadingRepository;
    private final UtilityInvoiceRepository utilityInvoiceRepository;
    private final TenantContractRepository tenantContractRepository;
    private final UtilityBillRepository utilityBillRepository;
    private final PropertyAccessService propertyAccessService;
    private final MeterOverrideService meterOverrideService;
    private final UtilityInvoiceService utilityInvoiceService;

    public MeterReadingServiceImpl(
            PropertyRepository propertyRepository,
            RoomRepository roomRepository,
            MeterReadingRepository meterReadingRepository,
            UtilityInvoiceRepository utilityInvoiceRepository,
            TenantContractRepository tenantContractRepository,
            UtilityBillRepository utilityBillRepository,
            PropertyAccessService propertyAccessService,
            MeterOverrideService meterOverrideService,
            @Lazy UtilityInvoiceService utilityInvoiceService) {
        this.propertyRepository = propertyRepository;
        this.roomRepository = roomRepository;
        this.meterReadingRepository = meterReadingRepository;
        this.utilityInvoiceRepository = utilityInvoiceRepository;
        this.tenantContractRepository = tenantContractRepository;
        this.utilityBillRepository = utilityBillRepository;
        this.propertyAccessService = propertyAccessService;
        this.meterOverrideService = meterOverrideService;
        this.utilityInvoiceService = utilityInvoiceService;
    }

    @Override
    @Transactional(readOnly = true)
    public MeterReadingResponse getLatestReading(Long propertyId, Long roomId, String type) {
        propertyAccessService.assertCanManageProperty(propertyId);
        UtilityType utilityType = UtilityTypeMapper.fromApi(type);
        loadProperty(propertyId);
        if (roomId != null) {
            loadRoom(propertyId, roomId);
        }

        Optional<MeterReading> latestReading = roomId == null
                ? meterReadingRepository.findTopByPropertyIdAndRoomIsNullAndUtilityTypeOrderByRecordedAtDesc(
                        propertyId, utilityType)
                : meterReadingRepository.findTopByPropertyIdAndRoomIdAndUtilityTypeOrderByRecordedAtDesc(
                        propertyId, roomId, utilityType);

        if (latestReading.isPresent()) {
            return toResponse(latestReading.get());
        }

        Optional<BigDecimal> fromInvoice = roomId == null
                ? utilityInvoiceRepository.findTopByPropertyIdAndRoomIsNullAndUtilityTypeOrderByCreatedAtDesc(
                        propertyId, utilityType).map(UtilityInvoice::getNewReading)
                : utilityInvoiceRepository.findTopByPropertyIdAndRoomIdAndUtilityTypeOrderByCreatedAtDesc(
                        propertyId, roomId, utilityType).map(UtilityInvoice::getNewReading);

        if (fromInvoice.isPresent()) {
            return MeterReadingResponse.builder()
                    .reading(fromInvoice.get())
                    .period("")
                    .recordedAt("")
                    .type(UtilityTypeMapper.toApi(utilityType))
                    .build();
        }

        BigDecimal initial = resolveInitialReading(propertyId, roomId, utilityType);
        return MeterReadingResponse.builder()
                .reading(initial)
                .period("")
                .recordedAt("")
                .type(UtilityTypeMapper.toApi(utilityType))
                .build();
    }

    @Override
    @Transactional
    public MeterReadingResponse recordReading(Long propertyId, Long roomId, CreateMeterReadingRequest request) {
        propertyAccessService.assertCanManageProperty(propertyId);
        UtilityType utilityType = UtilityTypeMapper.fromApi(request.getType());
        Property property = loadProperty(propertyId);
        Room room = roomId == null ? null : loadRoom(propertyId, roomId);

        if (room != null && room.getStatus() == RoomStatus.DISABLED) {
            throw new BusinessException("Phòng đang ngưng khai thác — không ghi chỉ số");
        }

        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        String normalizedPeriod = ContractBillingCalendar.parsePeriod(request.getPeriod())
                .map(ContractBillingCalendar::normalizePeriod)
                .orElse(request.getPeriod());

        MeterReading existing = findReading(propertyId, roomId, utilityType, normalizedPeriod).orElse(null);
        MeterReading saved;
        if (existing != null) {
            if (existing.getUtilityInvoiceId() != null) {
                throw new BusinessException("READING_ALREADY_ISSUED",
                        "Chỉ số kỳ này đã phát hành hoá đơn — không sửa trực tiếp được.");
            }
            existing.setReading(request.getReading());
            existing.setImageUrl(request.getImageUrl());
            existing.setPeriod(normalizedPeriod);
            existing.setRecordedAt(LocalDateTime.now());
            existing.setRecordedBy(user.getId());
            saved = meterReadingRepository.save(existing);
        } else {
            saved = meterReadingRepository.save(MeterReading.builder()
                    .property(property)
                    .room(room)
                    .utilityType(utilityType)
                    .period(normalizedPeriod)
                    .reading(request.getReading())
                    .imageUrl(request.getImageUrl())
                    .recordedAt(LocalDateTime.now())
                    .recordedBy(user.getId())
                    .build());
        }

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SavedMeterReadingListResponse listSavedForPeriod(Long propertyId, String period, String utilityTypeApi) {
        propertyAccessService.assertCanManageProperty(propertyId);
        UtilityType utilityType = UtilityTypeMapper.fromApi(utilityTypeApi);
        YearMonth month = ContractBillingCalendar.parsePeriod(period)
                .orElseThrow(() -> new BusinessException("INVALID_PERIOD",
                        "Kỳ phải dạng yyyy-MM"));
        String normalized = ContractBillingCalendar.normalizePeriod(month);
        Property property = loadProperty(propertyId);

        List<SavedMeterReadingItem> items = new ArrayList<>();
        for (TenantContract contract : tenantContractRepository.findActiveWithTenantByPropertyId(propertyId)) {
            if (Boolean.TRUE.equals(property.getWholeHouse())) {
                if (contract.getRoom() != null) {
                    continue;
                }
            } else if (contract.getRoom() == null) {
                continue;
            }
            items.add(toSavedItem(property, contract, utilityType, month, normalized));
        }
        return SavedMeterReadingListResponse.builder().items(items).build();
    }

    @Override
    @Transactional
    public SavedMeterReadingItem saveLockedReading(SaveMeterReadingRequest request) {
        Long propertyId = request.getPropertyId();
        propertyAccessService.assertCanManageProperty(propertyId);
        UtilityType utilityType = UtilityTypeMapper.fromApi(request.getUtilityType());
        YearMonth month = ContractBillingCalendar.parsePeriod(request.getPeriod())
                .orElseThrow(() -> new BusinessException("INVALID_PERIOD",
                        "Kỳ phải dạng yyyy-MM"));
        String normalized = ContractBillingCalendar.normalizePeriod(month);

        Property property = loadProperty(propertyId);
        Long roomId = request.getRoomId();
        Room room = roomId == null ? null : loadRoom(propertyId, roomId);
        if (room != null && room.getStatus() == RoomStatus.DISABLED) {
            throw new BusinessException("Phòng đang ngưng khai thác — không ghi chỉ số");
        }

        TenantContract contract = resolveActiveContract(propertyId, roomId);
        if (contract == null) {
            throw new BusinessException("NO_ACTIVE_CONTRACT",
                    "Không có hợp đồng ACTIVE cho phòng/nhà này.");
        }

        if (request.getNewReading().compareTo(request.getPrevReading()) <= 0) {
            throw new BusinessException("INVALID_READING",
                    "Chỉ số mới phải lớn hơn chỉ số cũ.");
        }

        boolean hasPhoto = request.getMeterImageUrl() != null && !request.getMeterImageUrl().isBlank();
        if (!hasPhoto) {
            if (request.getOverrideToken() == null) {
                throw new BusinessException("METER_PHOTO_REQUIRED",
                        "Cần ảnh công tơ hoặc mã override của admin.");
            }
            String kind = utilityType == UtilityType.ELECTRIC ? "ELEC" : "WATER";
            CustomUserDetails user = SecurityUtils.requireCurrentUser();
            boolean ok = meterOverrideService.consumeOverrideIfPresent(
                    user.getId(), contract.getId(), kind,
                    request.getOverrideToken(), request.getNewReading(), request.getOverrideReason());
            if (!ok) {
                throw new BusinessException("METER_PHOTO_REQUIRED",
                        "Cần ảnh công tơ hoặc mã override của admin.");
            }
        }

        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        MeterReading existing = findReading(propertyId, roomId, utilityType, normalized).orElse(null);
        if (existing != null && existing.getUtilityInvoiceId() != null) {
            throw new BusinessException("READING_ALREADY_ISSUED",
                    "Chỉ số kỳ này đã phát hành hoá đơn — không sửa trực tiếp được.");
        }

        LocalDateTime now = LocalDateTime.now();
        MeterReading saved;
        if (existing != null) {
            existing.setPrevReading(request.getPrevReading());
            existing.setReading(request.getNewReading());
            existing.setImageUrl(hasPhoto ? request.getMeterImageUrl() : existing.getImageUrl());
            existing.setPeriod(normalized);
            existing.setRecordedAt(now);
            existing.setRecordedBy(user.getId());
            if (hasPhoto) {
                existing.setImageUrl(request.getMeterImageUrl());
            }
            saved = meterReadingRepository.save(existing);
        } else {
            saved = meterReadingRepository.save(MeterReading.builder()
                    .property(property)
                    .room(room)
                    .utilityType(utilityType)
                    .period(normalized)
                    .prevReading(request.getPrevReading())
                    .reading(request.getNewReading())
                    .imageUrl(hasPhoto ? request.getMeterImageUrl() : null)
                    .recordedAt(now)
                    .recordedBy(user.getId())
                    .build());
        }

        // Điện / nước: nếu admin đã publish giấy → phát hành ngay cho phòng vừa chốt.
        if (saved.getUtilityInvoiceId() == null && !Boolean.TRUE.equals(property.getWholeHouse())) {
            final Long readingId = saved.getId();
            if (utilityType == UtilityType.ELECTRIC) {
                utilityBillRepository
                        .findByPropertyIdAndMonthAndYearAndTypeAndStatus(
                                propertyId, month.getMonthValue(), month.getYear(),
                                UtilityType.ELECTRIC, UtilityBillStatus.PUBLISHED)
                        .ifPresent(bill -> utilityInvoiceService.issueElectricFromSavedReading(bill, readingId));
            } else if (utilityType == UtilityType.WATER) {
                // Nước: không ghép theo period — lấy hoá đơn PUBLISHED mới nhất còn dùng được.
                utilityBillRepository
                        .findByPropertyIdAndTypeAndStatusOrderByCreatedAtDesc(
                                propertyId, UtilityType.WATER, UtilityBillStatus.PUBLISHED)
                        .stream()
                        .findFirst()
                        .ifPresent(bill -> utilityInvoiceService.issueWaterFromSavedReading(bill, readingId));
            }
            saved = meterReadingRepository.findById(readingId).orElse(saved);
        }

        PrevSnapshot prev = resolvePrevSnapshot(propertyId, roomId, utilityType, month, contract);
        return toSavedItemFromEntities(contract, saved, prev);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingMeterReadingItem> listPending(String period) {
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        boolean admin = user.getAuthorities().stream()
                .anyMatch(a -> Role.ROLE_ADMIN.name().equals(a.getAuthority()));
        YearMonth month = ContractBillingCalendar.parsePeriod(period)
                .orElse(YearMonth.now(VN));
        String normalized = ContractBillingCalendar.normalizePeriod(month);

        List<PendingMeterReadingItem> items = new ArrayList<>();

        // NƯỚC: sau khi admin đã phát hành hoá đơn — liệt kê phòng chưa chốt (không cần readingDeadline).
        List<UtilityBill> waterBills = utilityBillRepository.findPublishedSharedHouseByPeriodAndType(
                month.getMonthValue(), month.getYear(), UtilityType.WATER, UtilityBillStatus.PUBLISHED);
        for (UtilityBill bill : waterBills) {
            Property property = bill.getProperty();
            if (property == null) {
                continue;
            }
            if (!admin && !user.getId().equals(property.getOperationManagerId())) {
                continue;
            }
            items.addAll(collectPendingForWaterBill(bill, property, normalized, true));
        }

        // ĐIỆN: ngày cuối tháng, không cần hoá đơn EVN; hasReading = đã có bản chốt.
        List<Property> electricProps = admin
                ? propertyRepository.findAll()
                : propertyRepository.findByOperationManagerId(user.getId());
        LocalDate meterDue = month.atEndOfMonth();
        for (Property property : electricProps) {
            if (Boolean.TRUE.equals(property.getWholeHouse())) {
                continue;
            }
            items.addAll(collectElectricPending(property, normalized, meterDue, true));
        }
        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingMeterReadingItem> listPendingFor(Long propertyId, String period, UtilityType type) {
        return collectForPropertyPeriod(propertyId, period, type, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingMeterReadingItem> listEligibleForPeriod(Long propertyId, String period, UtilityType type) {
        return collectForPropertyPeriod(propertyId, period, type, false);
    }

    private List<PendingMeterReadingItem> collectForPropertyPeriod(
            Long propertyId, String period, UtilityType type, boolean onlyMissing) {
        YearMonth month = ContractBillingCalendar.parsePeriod(period)
                .orElse(YearMonth.now(VN));
        String normalized = ContractBillingCalendar.normalizePeriod(month);
        UtilityType resolvedType = type != null ? type : UtilityType.ELECTRIC;

        Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property == null) {
            return List.of();
        }

        if (resolvedType == UtilityType.ELECTRIC) {
            return collectElectricPending(property, normalized, month.atEndOfMonth(), onlyMissing);
        }

        Optional<UtilityBill> billOpt = utilityBillRepository
                .findByPropertyIdAndMonthAndYearAndTypeAndStatus(
                        propertyId,
                        month.getMonthValue(),
                        month.getYear(),
                        resolvedType,
                        UtilityBillStatus.PUBLISHED);
        if (billOpt.isEmpty()) {
            return List.of();
        }
        UtilityBill bill = billOpt.get();
        Property billProperty = bill.getProperty();
        if (billProperty == null) {
            return List.of();
        }
        if (resolvedType == UtilityType.WATER) {
            return collectPendingForWaterBill(bill, billProperty, normalized, onlyMissing);
        }
        return collectPendingForBill(bill, billProperty, resolvedType, normalized, onlyMissing);
    }

    private List<PendingMeterReadingItem> collectElectricPending(
            Property property,
            String normalizedPeriod,
            LocalDate meterDueDate,
            boolean onlyMissingReading) {
        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            return List.of();
        }
        List<PendingMeterReadingItem> items = new ArrayList<>();
        for (TenantContract contract : tenantContractRepository.findActiveWithTenantByPropertyId(property.getId())) {
            if (contract.getRoom() == null) {
                continue;
            }
            if (contract.getStartDate() != null && contract.getStartDate().isAfter(meterDueDate)) {
                continue;
            }
            Optional<MeterReading> reading = findReading(
                    property.getId(), contract.getRoom().getId(), UtilityType.ELECTRIC, normalizedPeriod);
            boolean hasReading = reading.isPresent();
            boolean hasPhoto = reading.filter(r -> r.getImageUrl() != null && !r.getImageUrl().isBlank()).isPresent();
            if (onlyMissingReading && hasReading) {
                continue;
            }
            int billingDay = ContractBillingCalendar.billingDayOfMonth(contract);
            items.add(PendingMeterReadingItem.builder()
                    .propertyId(property.getId())
                    .propertyName(property.getPropertyName())
                    .roomId(contract.getRoom().getId())
                    .roomNumber(contract.getRoom().getRoomNumber())
                    .contractId(contract.getId())
                    .utilityType(UtilityTypeMapper.toApi(UtilityType.ELECTRIC))
                    .period(normalizedPeriod)
                    .billingDay(billingDay)
                    .meterDueDate(meterDueDate)
                    .hasReading(hasReading)
                    .hasPhoto(hasPhoto)
                    .build());
        }
        return items;
    }

    /**
     * Nước sau publish: phòng chưa chốt = chưa có bản WATER chưa phát hành và chưa có hoá đơn kỳ bill.
     * Không phụ thuộc readingDeadline / không nhắc trước khi chưa có giấy.
     */
    private List<PendingMeterReadingItem> collectPendingForWaterBill(
            UtilityBill bill,
            Property property,
            String normalizedPeriod,
            boolean onlyMissingReading) {
        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            return List.of();
        }
        List<PendingMeterReadingItem> items = new ArrayList<>();
        for (TenantContract contract : tenantContractRepository.findActiveWithTenantByPropertyId(property.getId())) {
            if (contract.getRoom() == null) {
                continue;
            }
            Long roomId = contract.getRoom().getId();
            boolean hasUnissued = !meterReadingRepository
                    .findByPropertyIdAndRoomIdAndUtilityTypeAndUtilityInvoiceIdIsNull(
                            property.getId(), roomId, UtilityType.WATER)
                    .isEmpty();
            boolean hasInvoice = !utilityInvoiceRepository.findByFilters(
                    property.getId(), bill.getBillingPeriod(), UtilityType.WATER).stream()
                    .filter(i -> i.getRoom() != null && roomId.equals(i.getRoom().getId()))
                    .filter(i -> i.getStatus() != UtilityInvoiceStatus.CANCELLED)
                    .toList()
                    .isEmpty();
            boolean hasReading = hasUnissued || hasInvoice;
            if (onlyMissingReading && hasReading) {
                continue;
            }
            int billingDay = ContractBillingCalendar.billingDayOfMonth(contract);
            items.add(PendingMeterReadingItem.builder()
                    .propertyId(property.getId())
                    .propertyName(property.getPropertyName())
                    .roomId(roomId)
                    .roomNumber(contract.getRoom().getRoomNumber())
                    .contractId(contract.getId())
                    .utilityType(UtilityTypeMapper.toApi(UtilityType.WATER))
                    .period(normalizedPeriod)
                    .billingDay(billingDay)
                    .meterDueDate(null)
                    .hasReading(hasReading)
                    .hasPhoto(hasUnissued || hasInvoice)
                    .build());
        }
        return items;
    }

    /**
     * Nước (và tương thích cũ): HĐ ACTIVE có phòng, bỏ qua khách bắt đầu sau readingDeadline.
     * {@code onlyMissing=true} → bỏ qua phòng đã có ảnh.
     */
    private List<PendingMeterReadingItem> collectPendingForBill(
            UtilityBill bill,
            Property property,
            UtilityType type,
            String normalizedPeriod,
            boolean onlyMissingPhoto) {
        LocalDate readingDeadline = bill.getReadingDeadline();
        if (readingDeadline == null) {
            return List.of();
        }
        List<PendingMeterReadingItem> items = new ArrayList<>();
        for (TenantContract contract : tenantContractRepository.findActiveWithTenantByPropertyId(property.getId())) {
            if (contract.getRoom() == null) {
                continue;
            }
            if (contract.getStartDate() != null && contract.getStartDate().isAfter(readingDeadline)) {
                continue;
            }
            Optional<MeterReading> reading = findReading(
                    property.getId(),
                    contract.getRoom().getId(),
                    type,
                    normalizedPeriod);
            boolean hasReading = reading.isPresent();
            boolean hasPhoto = reading.filter(r -> r.getImageUrl() != null && !r.getImageUrl().isBlank()).isPresent();
            if (onlyMissingPhoto && hasPhoto) {
                continue;
            }
            int billingDay = ContractBillingCalendar.billingDayOfMonth(contract);
            items.add(PendingMeterReadingItem.builder()
                    .propertyId(property.getId())
                    .propertyName(property.getPropertyName())
                    .roomId(contract.getRoom().getId())
                    .roomNumber(contract.getRoom().getRoomNumber())
                    .contractId(contract.getId())
                    .utilityType(UtilityTypeMapper.toApi(type))
                    .period(normalizedPeriod)
                    .billingDay(billingDay)
                    .meterDueDate(readingDeadline)
                    .hasReading(hasReading)
                    .hasPhoto(hasPhoto)
                    .build());
        }
        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPhoto(Long propertyId, Long roomId, UtilityType type, String period) {
        return findReading(propertyId, roomId, type, period)
                .filter(r -> r.getImageUrl() != null && !r.getImageUrl().isBlank())
                .isPresent();
    }

    Optional<MeterReading> findReading(Long propertyId, Long roomId, UtilityType type, String period) {
        for (String candidate : periodAliases(period)) {
            Optional<MeterReading> found = roomId == null
                    ? meterReadingRepository.findTopByPropertyIdAndRoomIsNullAndUtilityTypeAndPeriodOrderByRecordedAtDesc(
                            propertyId, type, candidate)
                    : meterReadingRepository.findTopByPropertyIdAndRoomIdAndUtilityTypeAndPeriodOrderByRecordedAtDesc(
                            propertyId, roomId, type, candidate);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private SavedMeterReadingItem toSavedItem(
            Property property, TenantContract contract, UtilityType type, YearMonth month, String normalized) {
        Long roomId = contract.getRoom() != null ? contract.getRoom().getId() : null;
        PrevSnapshot prev = resolvePrevSnapshot(property.getId(), roomId, type, month, contract);
        Optional<MeterReading> reading = findReading(property.getId(), roomId, type, normalized);
        if (reading.isEmpty()) {
            return SavedMeterReadingItem.builder()
                    .roomId(roomId)
                    .roomNumber(contract.getRoom() != null ? contract.getRoom().getRoomNumber() : null)
                    .contractId(contract.getId())
                    .tenantName(tenantName(contract))
                    .prevReading(prev.reading())
                    .prevSource(prev.source())
                    .newReading(null)
                    .meterImageUrl(null)
                    .capturedAt(null)
                    .invoiceId(null)
                    .invoiceStatus(null)
                    .build();
        }
        return toSavedItemFromEntities(contract, reading.get(), prev);
    }

    private SavedMeterReadingItem toSavedItemFromEntities(
            TenantContract contract, MeterReading reading, PrevSnapshot prev) {
        Long invoiceId = reading.getUtilityInvoiceId();
        String invoiceStatus = null;
        if (invoiceId != null) {
            invoiceStatus = utilityInvoiceRepository.findById(invoiceId)
                    .map(inv -> inv.getStatus() != null ? inv.getStatus().name() : null)
                    .orElse(null);
        }
        Long roomId = reading.getRoom() != null ? reading.getRoom().getId()
                : (contract.getRoom() != null ? contract.getRoom().getId() : null);
        String roomNumber = reading.getRoom() != null ? reading.getRoom().getRoomNumber()
                : (contract.getRoom() != null ? contract.getRoom().getRoomNumber() : null);
        return SavedMeterReadingItem.builder()
                .roomId(roomId)
                .roomNumber(roomNumber)
                .contractId(contract.getId())
                .tenantName(tenantName(contract))
                .prevReading(reading.getPrevReading() != null ? reading.getPrevReading() : prev.reading())
                .prevSource(prev.source())
                .newReading(reading.getReading())
                .meterImageUrl(reading.getImageUrl())
                .capturedAt(formatCapturedAt(reading.getRecordedAt()))
                .invoiceId(invoiceId)
                .invoiceStatus(invoiceStatus)
                .build();
    }

    private PrevSnapshot resolvePrevSnapshot(
            Long propertyId, Long roomId, UtilityType type, YearMonth month, TenantContract contract) {
        YearMonth prevMonth = month.minusMonths(1);
        String prevNormalized = ContractBillingCalendar.normalizePeriod(prevMonth);

        Optional<UtilityInvoice> prevInvoice = findInvoiceForMonth(propertyId, roomId, type, prevMonth);
        if (prevInvoice.isPresent()) {
            return new PrevSnapshot(prevInvoice.get().getNewReading(), "LAST_INVOICE");
        }

        Optional<MeterReading> prevReading = findReading(propertyId, roomId, type, prevNormalized);
        if (prevReading.isPresent()) {
            String source = prevReading.get().getUtilityInvoiceId() != null ? "LAST_INVOICE" : "LAST_READING";
            return new PrevSnapshot(prevReading.get().getReading(), source);
        }

        BigDecimal handover = readingFromContract(contract, type);
        return new PrevSnapshot(handover, "HANDOVER");
    }

    private Optional<UtilityInvoice> findInvoiceForMonth(
            Long propertyId, Long roomId, UtilityType type, YearMonth month) {
        for (String alias : periodAliases(ContractBillingCalendar.normalizePeriod(month))) {
            List<UtilityInvoice> found = utilityInvoiceRepository.findByFilters(propertyId, alias, type);
            for (UtilityInvoice inv : found) {
                Long invRoomId = inv.getRoom() != null ? inv.getRoom().getId() : null;
                boolean roomMatch = roomId == null ? invRoomId == null : roomId.equals(invRoomId);
                if (!roomMatch) {
                    continue;
                }
                if (inv.getStatus() == com.sep490.slms2026.enums.UtilityInvoiceStatus.CANCELLED) {
                    continue;
                }
                return Optional.of(inv);
            }
        }
        return Optional.empty();
    }

    private static List<String> periodAliases(String period) {
        List<String> aliases = new ArrayList<>();
        if (period != null && !period.isBlank()) {
            aliases.add(period.trim());
        }
        ContractBillingCalendar.parsePeriod(period).ifPresent(ym -> {
            String iso = ym.toString();
            String vn = String.format("%02d/%d", ym.getMonthValue(), ym.getYear());
            String vnShort = String.format("%d/%d", ym.getMonthValue(), ym.getYear());
            if (!aliases.contains(iso)) {
                aliases.add(iso);
            }
            if (!aliases.contains(vn)) {
                aliases.add(vn);
            }
            if (!aliases.contains(vnShort)) {
                aliases.add(vnShort);
            }
        });
        return aliases;
    }

    private BigDecimal resolveInitialReading(Long propertyId, Long roomId, UtilityType utilityType) {
        Optional<TenantContract> activeContract = roomId != null
                ? tenantContractRepository.findByRoomIdAndStatus(roomId, ContractStatus.ACTIVE)
                : tenantContractRepository.findByPropertyIdAndRoomIsNullAndStatus(propertyId, ContractStatus.ACTIVE);

        if (activeContract.isEmpty()) {
            return BigDecimal.ZERO;
        }

        TenantContract contract = activeContract.get();

        Optional<UtilityInvoice> lastCheckout = roomId != null
                ? utilityInvoiceRepository.findTopByPropertyIdAndRoomIdAndUtilityTypeAndBillingPeriodLikeOrderByCreatedAtDesc(
                        propertyId, roomId, utilityType, "%chốt trả phòng%")
                : utilityInvoiceRepository.findTopByPropertyIdAndRoomIsNullAndUtilityTypeAndBillingPeriodLikeOrderByCreatedAtDesc(
                        propertyId, utilityType, "%chốt trả phòng%");

        if (lastCheckout.isPresent() && !contract.getId().equals(lastCheckout.get().getTenantContract().getId())) {
            return lastCheckout.get().getNewReading();
        }

        return readingFromContract(contract, utilityType);
    }

    private TenantContract resolveActiveContract(Long propertyId, Long roomId) {
        if (roomId != null) {
            return tenantContractRepository.findByRoomIdAndStatus(roomId, ContractStatus.ACTIVE).orElse(null);
        }
        return tenantContractRepository.findByPropertyIdAndRoomIsNullAndStatus(propertyId, ContractStatus.ACTIVE)
                .orElse(null);
    }

    private BigDecimal readingFromContract(TenantContract contract, UtilityType utilityType) {
        BigDecimal reading = utilityType == UtilityType.ELECTRIC
                ? contract.getInitialElectricReading()
                : contract.getInitialWaterReading();
        return reading != null ? reading : BigDecimal.ZERO;
    }

    private static String tenantName(TenantContract contract) {
        if (contract.getTenant() == null || contract.getTenant().getUser() == null) {
            return null;
        }
        return contract.getTenant().getUser().getFullName();
    }

    private static String formatCapturedAt(LocalDateTime recordedAt) {
        if (recordedAt == null) {
            return null;
        }
        return recordedAt.atZone(VN).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
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

    private MeterReadingResponse toResponse(MeterReading reading) {
        return MeterReadingResponse.builder()
                .reading(reading.getReading())
                .period(reading.getPeriod())
                .recordedAt(reading.getRecordedAt() != null ? reading.getRecordedAt().format(ISO_FORMAT) : "")
                .type(UtilityTypeMapper.toApi(reading.getUtilityType()))
                .imageUrl(reading.getImageUrl())
                .build();
    }

    private record PrevSnapshot(BigDecimal reading, String source) {}
}
