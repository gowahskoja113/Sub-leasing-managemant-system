package com.sep490.slms2026.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.slms2026.dto.request.RentScheduleItemRequest;
import com.sep490.slms2026.dto.request.UpdateUnitPriceRequest;
import com.sep490.slms2026.dto.response.PropertyResponse;
import com.sep490.slms2026.dto.response.RoomPriceHistoryResponse;
import com.sep490.slms2026.dto.response.RoomResponse;
import com.sep490.slms2026.entity.HostNotification;
import com.sep490.slms2026.entity.Notification;
import com.sep490.slms2026.entity.Property;
import com.sep490.slms2026.entity.Room;
import com.sep490.slms2026.entity.RoomPriceHistory;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.ContractStatus;
import com.sep490.slms2026.enums.HostNotificationType;
import com.sep490.slms2026.enums.RentEscalationType;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.RoomPriceChangeType;
import com.sep490.slms2026.enums.UserStatus;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.mapper.RoomMapper;
import com.sep490.slms2026.repository.HostNotificationRepository;
import com.sep490.slms2026.repository.NotificationRepository;
import com.sep490.slms2026.repository.PropertyRepository;
import com.sep490.slms2026.repository.RoomPriceHistoryRepository;
import com.sep490.slms2026.repository.RoomRepository;
import com.sep490.slms2026.repository.TenantContractRepository;
import com.sep490.slms2026.repository.UserRepository;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.PropertyService;
import com.sep490.slms2026.service.UnitPriceService;
import com.sep490.slms2026.service.UserPushTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnitPriceServiceImpl implements UnitPriceService {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<ContractStatus> OCCUPYING_STATUSES =
            List.of(ContractStatus.ACTIVE, ContractStatus.EXPIRED);

    private final RoomRepository roomRepository;
    private final PropertyRepository propertyRepository;
    private final TenantContractRepository tenantContractRepository;
    private final RoomPriceHistoryRepository roomPriceHistoryRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final HostNotificationRepository hostNotificationRepository;
    private final UserPushTokenService userPushTokenService;
    private final RoomMapper roomMapper;
    private final PropertyService propertyService;
    /** Spring Boot 4 không expose ObjectMapper Jackson 2 thành bean. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public RoomResponse updateRoomListedPrice(Long propertyId, Long roomId, UpdateUnitPriceRequest request) {
        Property property = requireProperty(propertyId);
        if (Boolean.TRUE.equals(property.getWholeHouse())) {
            throw new BusinessException("Nhà nguyên căn đổi giá qua PATCH /properties/{id}/price");
        }
        Room room = roomRepository.findByIdAndPropertyIdAndDeletedIsFalse(roomId, propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy phòng ID=" + roomId + " trong tòa nhà ID=" + propertyId));
        if (isUnitOccupied(propertyId, roomId)) {
            throw new BusinessException("Phòng đang có khách — không được đổi giá niêm yết");
        }
        BigDecimal newPrice = requirePositive(request.getPrice());
        String reason = requireReason(request.getReason());
        BigDecimal oldListed = room.getPrice();
        Actor actor = currentActor();

        room.setPrice(newPrice);
        if (room.getAppliedPrice() == null || pricesEqual(room.getAppliedPrice(), oldListed)) {
            room.setAppliedPrice(newPrice);
        }
        roomRepository.save(room);
        recordHistory(propertyId, roomId, RoomPriceChangeType.HOST_DOI, oldListed, newPrice,
                null, reason, actor);
        RoomResponse response = roomMapper.toResponse(room);
        response.setPriceLocked(false);
        return response;
    }

    @Override
    @Transactional
    public PropertyResponse updatePropertyListedPrice(Long propertyId, UpdateUnitPriceRequest request) {
        Property property = requireProperty(propertyId);
        if (!Boolean.TRUE.equals(property.getWholeHouse())) {
            throw new BusinessException("Nhà chia phòng đổi giá từng phòng qua PATCH .../rooms/{roomId}/price");
        }
        if (isUnitOccupied(propertyId, null)) {
            throw new BusinessException("Căn đang có khách — không được đổi giá niêm yết");
        }
        BigDecimal newPrice = requirePositive(request.getPrice());
        String reason = requireReason(request.getReason());
        BigDecimal oldListed = property.getPrice();
        Actor actor = currentActor();

        property.setPrice(newPrice);
        if (property.getAppliedPrice() == null || pricesEqual(property.getAppliedPrice(), oldListed)) {
            property.setAppliedPrice(newPrice);
        }
        propertyRepository.save(property);
        recordHistory(propertyId, null, RoomPriceChangeType.HOST_DOI, oldListed, newPrice,
                null, reason, actor);
        return propertyService.getPropertyById(propertyId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomPriceHistoryResponse> getPriceHistory(Long propertyId, Long roomId) {
        requireProperty(propertyId);
        List<RoomPriceHistory> rows = roomId == null
                ? roomPriceHistoryRepository.findByPropertyIdOrderByChangedAtDescIdDesc(propertyId)
                : roomPriceHistoryRepository.findByPropertyIdAndRoomIdOrderByChangedAtDescIdDesc(propertyId, roomId);
        Map<Long, String> roomNumbers = new HashMap<>();
        roomRepository.findByPropertyIdAndDeletedIsFalse(propertyId)
                .forEach(r -> roomNumbers.put(r.getId(), r.getRoomNumber()));
        return rows.stream().map(h -> toHistoryResponse(h, roomNumbers)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUnitOccupied(Long propertyId, Long roomId) {
        if (roomId != null) {
            return tenantContractRepository.existsByRoomIdAndStatus(roomId, ContractStatus.ACTIVE)
                    || tenantContractRepository.existsByRoomIdAndStatus(roomId, ContractStatus.EXPIRED);
        }
        return tenantContractRepository.existsByPropertyIdAndRoomIsNullAndStatus(propertyId, ContractStatus.ACTIVE)
                || tenantContractRepository.existsByPropertyIdAndRoomIsNullAndStatus(propertyId, ContractStatus.EXPIRED);
    }

    @Override
    @Transactional
    public void applyContractRent(TenantContract contract, RoomPriceChangeType changeType, String reason) {
        if (contract == null || contract.getRentAmount() == null) {
            return;
        }
        BigDecimal newPrice = requirePositive(contract.getRentAmount());
        if (contract.getBaseRentAmount() == null) {
            contract.setBaseRentAmount(newPrice);
        }
        Room room = contract.getRoom();
        Property property = contract.getProperty();
        Actor actor = currentActor();
        BigDecimal oldApplied;
        if (room != null) {
            oldApplied = effectiveApplied(room.getAppliedPrice(), room.getPrice());
            if (pricesEqual(oldApplied, newPrice)) {
                return;
            }
            room.setAppliedPrice(newPrice);
            roomRepository.save(room);
            recordHistory(property.getId(), room.getId(), changeType, oldApplied, newPrice,
                    contract.getId(), reason, actor);
        } else {
            oldApplied = effectiveApplied(property.getAppliedPrice(), property.getPrice());
            if (pricesEqual(oldApplied, newPrice)) {
                return;
            }
            property.setAppliedPrice(newPrice);
            propertyRepository.save(property);
            recordHistory(property.getId(), null, changeType, oldApplied, newPrice,
                    contract.getId(), reason, actor);
        }
        if (changeType == RoomPriceChangeType.HOP_DONG || changeType == RoomPriceChangeType.DIEU_KHOAN_HD) {
            notifyAppliedChanged(contract, oldApplied, newPrice, changeType, actor);
        }
    }

    @Override
    @Transactional
    public void revertToListedPrice(TenantContract contract) {
        if (contract == null || contract.getProperty() == null) {
            return;
        }
        Room room = contract.getRoom();
        Property property = contract.getProperty();
        Actor actor = Actor.system();
        if (room != null) {
            if (isOccupiedByOther(property.getId(), room.getId(), contract.getId())) {
                return;
            }
            BigDecimal listed = room.getPrice();
            BigDecimal oldApplied = effectiveApplied(room.getAppliedPrice(), listed);
            if (listed == null || pricesEqual(oldApplied, listed)) {
                room.setAppliedPrice(listed);
                roomRepository.save(room);
                return;
            }
            room.setAppliedPrice(listed);
            roomRepository.save(room);
            String listedAt = listedSetLabel(property.getId(), room.getId());
            String reason = "Khách trả phòng, về giá niêm yết" + listedAt;
            recordHistory(property.getId(), room.getId(), RoomPriceChangeType.TU_DONG,
                    oldApplied, listed, contract.getId(), reason, actor);
            notifyVacantRestore(contract, room.getRoomNumber(), oldApplied, listed, listedAt);
        } else {
            if (isOccupiedByOther(property.getId(), null, contract.getId())) {
                return;
            }
            BigDecimal listed = property.getPrice();
            BigDecimal oldApplied = effectiveApplied(property.getAppliedPrice(), listed);
            if (listed == null || pricesEqual(oldApplied, listed)) {
                property.setAppliedPrice(listed);
                propertyRepository.save(property);
                return;
            }
            property.setAppliedPrice(listed);
            propertyRepository.save(property);
            String listedAt = listedSetLabel(property.getId(), null);
            String reason = "Khách trả nhà, về giá niêm yết" + listedAt;
            recordHistory(property.getId(), null, RoomPriceChangeType.TU_DONG,
                    oldApplied, listed, contract.getId(), reason, actor);
            notifyVacantRestore(contract, null, oldApplied, listed, listedAt);
        }
    }

    @Override
    @Transactional
    public int applyDueEscalations() {
        LocalDate today = LocalDate.now(VN);
        int applied = 0;
        List<TenantContract> active = tenantContractRepository.findByStatus(ContractStatus.ACTIVE);
        for (TenantContract contract : active) {
            try {
                if (applyEscalationIfDue(contract, today)) {
                    applied++;
                }
            } catch (Exception e) {
                log.warn("Không áp được điều khoản tăng giá HĐ {}: {}", contract.getId(), e.getMessage());
            }
        }
        return applied;
    }

    @Override
    public BigDecimal resolveListedPrice(TenantContract contract) {
        if (contract == null) {
            return null;
        }
        if (contract.getRoom() != null) {
            return contract.getRoom().getPrice();
        }
        return contract.getProperty() != null ? contract.getProperty().getPrice() : null;
    }

    @Override
    public BigDecimal deltaPercent(BigDecimal oldPrice, BigDecimal newPrice) {
        if (oldPrice == null || newPrice == null || oldPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return newPrice.subtract(oldPrice)
                .multiply(BigDecimal.valueOf(100))
                .divide(oldPrice, 1, RoundingMode.HALF_UP);
    }

    private boolean applyEscalationIfDue(TenantContract contract, LocalDate today) {
        RentEscalationType type = contract.getRentEscalationType();
        if (type == null || type == RentEscalationType.NONE || contract.getStartDate() == null) {
            return false;
        }
        int monthIndex = (int) ChronoUnit.MONTHS.between(contract.getStartDate(), today) + 1;
        if (monthIndex < 1) {
            return false;
        }
        Integer last = contract.getRentEscalationLastFromMonth();
        int lastFrom = last == null ? 0 : last;

        if (type == RentEscalationType.PERCENT) {
            if (contract.getRentEscalationPercent() == null
                    || contract.getRentEscalationPercent().compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }
            int years = (monthIndex - 1) / 12;
            if (years <= 0) {
                return false;
            }
            int dueFromMonth = years * 12 + 1;
            if (dueFromMonth <= lastFrom) {
                return false;
            }
            BigDecimal base = contract.getBaseRentAmount() != null
                    ? contract.getBaseRentAmount()
                    : contract.getRentAmount();
            BigDecimal factor = BigDecimal.ONE.add(
                    contract.getRentEscalationPercent().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
            BigDecimal target = base;
            for (int i = 0; i < years; i++) {
                target = target.multiply(factor).setScale(0, RoundingMode.HALF_UP);
            }
            if (pricesEqual(contract.getRentAmount(), target)) {
                contract.setRentEscalationLastFromMonth(dueFromMonth);
                tenantContractRepository.save(contract);
                return false;
            }
            contract.setRentAmount(target);
            contract.setRentEscalationLastFromMonth(dueFromMonth);
            tenantContractRepository.save(contract);
            applyContractRent(contract, RoomPriceChangeType.DIEU_KHOAN_HD,
                    "+" + strip(contract.getRentEscalationPercent()) + "% năm "
                            + (years + 1) + " · " + contract.getContractCode());
            return true;
        }

        List<RentScheduleItemRequest> schedule = parseSchedule(contract.getRentScheduleJson());
        RentScheduleItemRequest due = schedule.stream()
                .filter(item -> item.getFromMonth() != null && item.getAmount() != null)
                .filter(item -> item.getFromMonth() <= monthIndex && item.getFromMonth() > lastFrom)
                .max(Comparator.comparing(RentScheduleItemRequest::getFromMonth))
                .orElse(null);
        if (due == null) {
            return false;
        }
        BigDecimal target = requirePositive(due.getAmount());
        contract.setRentAmount(target);
        contract.setRentEscalationLastFromMonth(due.getFromMonth());
        tenantContractRepository.save(contract);
        applyContractRent(contract, RoomPriceChangeType.DIEU_KHOAN_HD,
                "Lịch HĐ từ tháng " + due.getFromMonth() + " · " + contract.getContractCode());
        return true;
    }

    private List<RentScheduleItemRequest> parseSchedule(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("rent_schedule_json không đọc được: {}", e.getMessage());
            return List.of();
        }
    }

    private boolean isOccupiedByOther(Long propertyId, Long roomId, Long excludeContractId) {
        List<TenantContract> contracts = tenantContractRepository.findByPropertyIdAndStatusIn(
                propertyId, OCCUPYING_STATUSES);
        return contracts.stream().anyMatch(c -> {
            if (excludeContractId != null && excludeContractId.equals(c.getId())) {
                return false;
            }
            if (roomId != null) {
                return c.getRoom() != null && roomId.equals(c.getRoom().getId());
            }
            return c.getRoom() == null;
        });
    }

    private void notifyAppliedChanged(TenantContract contract, BigDecimal oldPrice, BigDecimal newPrice,
                                      RoomPriceChangeType changeType, Actor actor) {
        BigDecimal pct = deltaPercent(oldPrice, newPrice);
        String unit = contract.getRoom() != null
                ? "phòng " + contract.getRoom().getRoomNumber()
                : "nguyên căn";
        String pctText = pct == null ? "" : "   (" + signedPct(pct) + ")";
        String tenantName = tenantName(contract);
        String importer = actor.name() != null ? actor.name() : "hệ thống";
        String title = "Giá " + unit + " vừa đổi";
        String body = "⚠ Giá " + unit + " vừa đổi:  "
                + formatVnd(oldPrice) + "  →  " + formatVnd(newPrice) + pctText
                + "\n   Theo HĐ " + contract.getContractCode()
                + " · " + tenantName
                + " · " + (changeType == RoomPriceChangeType.HOP_DONG ? "import/tạo HĐ bởi " : "điều khoản HĐ · ")
                + importer;
        Map<String, Object> data = new HashMap<>();
        data.put("screen", "ContractDetail");
        data.put("params", Map.of("contractId", contract.getId()));
        data.put("type", "UNIT_PRICE_CHANGED");
        notifyHosts(title, body, data,
                "price-change:" + contract.getId() + ":" + newPrice.toPlainString());
    }

    private void notifyVacantRestore(TenantContract contract, String roomNumber,
                                     BigDecimal oldPrice, BigDecimal listed, String listedAt) {
        String unit = roomNumber != null ? "Phòng " + roomNumber : "Căn";
        String title = unit + " đã trống";
        String body = unit + " đã trống, giá quay về " + formatVnd(listed)
                + listedAt + " — kiểm tra lại giá trước khi đăng.";
        Map<String, Object> data = new HashMap<>();
        data.put("screen", "PropertyDetail");
        data.put("params", Map.of("propertyId", contract.getProperty().getId()));
        data.put("type", "LISTED_PRICE_RESTORED");
        notifyHosts(title, body, data, "listed-restore:" + contract.getId());
    }

    private void notifyHosts(String title, String body, Map<String, Object> data, String dedupeKey) {
        List<User> hosts = userRepository.findByRoleAndStatus(Role.ROLE_OWNER, UserStatus.ACTIVE);
        for (User host : hosts) {
            notificationRepository.save(Notification.builder()
                    .userId(host.getId())
                    .title(title)
                    .content(body)
                    .type("UNIT_PRICE_CHANGED")
                    .screen((String) data.get("screen"))
                    .paramsJson(writeJson(data.get("params")))
                    .build());
            try {
                hostNotificationRepository.save(HostNotification.builder()
                        .userId(host.getId())
                        .dedupeKey(dedupeKey)
                        .type(HostNotificationType.OCCUPANCY_ALERT.name())
                        .title(title)
                        .message(body)
                        .priority("HIGH")
                        .build());
            } catch (Exception e) {
                log.debug("Bỏ qua host_notification trùng: {}", e.getMessage());
            }
            userPushTokenService.sendToUser(host.getId(), title, body, data);
        }
    }

    private String listedSetLabel(Long propertyId, Long roomId) {
        List<RoomPriceHistory> rows = roomId == null
                ? roomPriceHistoryRepository.findByPropertyIdAndRoomIdIsNullOrderByChangedAtDescIdDesc(propertyId)
                : roomPriceHistoryRepository.findByPropertyIdAndRoomIdOrderByChangedAtDescIdDesc(propertyId, roomId);
        return rows.stream()
                .filter(h -> h.getChangeType() == RoomPriceChangeType.HOST_DOI)
                .findFirst()
                .map(h -> " (đặt từ " + h.getChangedAt().toLocalDate().format(
                        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ")")
                .orElse("");
    }

    private void recordHistory(Long propertyId, Long roomId, RoomPriceChangeType type,
                               BigDecimal oldPrice, BigDecimal newPrice, Long contractId,
                               String reason, Actor actor) {
        roomPriceHistoryRepository.save(RoomPriceHistory.builder()
                .propertyId(propertyId)
                .roomId(roomId)
                .changeType(type)
                .oldPrice(oldPrice)
                .newPrice(newPrice)
                .contractId(contractId)
                .reason(reason)
                .changedBy(actor.id())
                .changedByName(actor.name())
                .changedAt(LocalDateTime.now())
                .build());
    }

    private RoomPriceHistoryResponse toHistoryResponse(RoomPriceHistory h, Map<Long, String> roomNumbers) {
        return RoomPriceHistoryResponse.builder()
                .id(h.getId())
                .propertyId(h.getPropertyId())
                .roomId(h.getRoomId())
                .roomNumber(h.getRoomId() != null ? roomNumbers.get(h.getRoomId()) : null)
                .changeType(h.getChangeType())
                .changeTypeLabel(labelOf(h.getChangeType()))
                .oldPrice(h.getOldPrice())
                .newPrice(h.getNewPrice())
                .contractId(h.getContractId())
                .reason(h.getReason())
                .changedBy(h.getChangedBy())
                .changedByName(h.getChangedByName())
                .changedAt(h.getChangedAt())
                .build();
    }

    private static String labelOf(RoomPriceChangeType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case HOP_DONG -> "HỢP ĐỒNG";
            case DIEU_KHOAN_HD -> "ĐIỀU KHOẢN HĐ";
            case TU_DONG -> "TỰ ĐỘNG";
            case HOST_DOI -> "HOST ĐỔI";
        };
    }

    private Property requireProperty(Long propertyId) {
        return propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tòa nhà với ID: " + propertyId));
    }

    private static BigDecimal requirePositive(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Giá phải lớn hơn 0");
        }
        return price;
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Lý do đổi giá niêm yết là bắt buộc");
        }
        return reason.trim();
    }

    private static BigDecimal effectiveApplied(BigDecimal applied, BigDecimal listed) {
        return applied != null ? applied : listed;
    }

    private static boolean pricesEqual(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.compareTo(b) == 0;
    }

    private static String formatVnd(BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("vi-VN"));
        DecimalFormat df = new DecimalFormat("#,###", symbols);
        return df.format(amount.setScale(0, RoundingMode.HALF_UP)) + "đ";
    }

    private static String signedPct(BigDecimal pct) {
        if (pct.compareTo(BigDecimal.ZERO) > 0) {
            return "+" + pct.toPlainString() + "%";
        }
        return pct.toPlainString() + "%";
    }

    private static String strip(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String tenantName(TenantContract contract) {
        if (contract.getTenant() != null && contract.getTenant().getUser() != null
                && contract.getTenant().getUser().getFullName() != null) {
            return contract.getTenant().getUser().getFullName();
        }
        return contract.getDraftTenantName() != null ? contract.getDraftTenantName() : "khách thuê";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Actor currentActor() {
        try {
            CustomUserDetails user = SecurityUtils.requireCurrentUser();
            String name = user.getUsername();
            if (name == null || name.isBlank()) {
                name = user.getFullName();
            }
            return new Actor(user.getId(), name);
        } catch (Exception e) {
            return Actor.system();
        }
    }

    private record Actor(UUID id, String name) {
        static Actor system() {
            return new Actor(null, "hệ thống");
        }
    }
}
