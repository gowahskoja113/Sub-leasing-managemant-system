package com.sep490.slms2026.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.slms2026.dto.request.RentScheduleItemRequest;
import com.sep490.slms2026.entity.TenantContract;
import com.sep490.slms2026.enums.RentEscalationType;
import com.sep490.slms2026.exception.BusinessException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RentEscalationSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RentEscalationSupport() {
    }

    public static void apply(TenantContract contract, String typeRaw, BigDecimal percent,
                             List<RentScheduleItemRequest> schedule, String scheduleRaw) {
        RentEscalationType type = parseType(typeRaw);
        contract.setRentEscalationType(type);
        if (type == RentEscalationType.PERCENT) {
            if (percent == null || percent.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("% tăng/năm phải lớn hơn 0 khi Loại tăng giá = PERCENT");
            }
            contract.setRentEscalationPercent(percent);
            contract.setRentScheduleJson(null);
        } else if (type == RentEscalationType.SCHEDULE) {
            List<RentScheduleItemRequest> items = schedule != null && !schedule.isEmpty()
                    ? schedule
                    : parseScheduleRaw(scheduleRaw);
            if (items.isEmpty()) {
                throw new BusinessException("Lịch tăng giá không được trống khi Loại tăng giá = SCHEDULE");
            }
            for (RentScheduleItemRequest item : items) {
                if (item.getFromMonth() == null || item.getFromMonth() < 2) {
                    throw new BusinessException("fromMonth trong lịch tăng giá phải >= 2");
                }
                if (item.getAmount() == null || item.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException("Số tiền trong lịch tăng giá phải lớn hơn 0");
                }
            }
            try {
                contract.setRentScheduleJson(MAPPER.writeValueAsString(items));
            } catch (JsonProcessingException e) {
                throw new BusinessException("Không ghi được lịch tăng giá");
            }
            contract.setRentEscalationPercent(null);
        } else {
            contract.setRentEscalationPercent(null);
            contract.setRentScheduleJson(null);
        }
    }

    public static RentEscalationType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return RentEscalationType.NONE;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('%', ' ');
        if (normalized.contains("PERCENT") || normalized.contains("PHAN_TRAM")
                || normalized.contains("PHẦN") || normalized.equals("TANG_%")) {
            return RentEscalationType.PERCENT;
        }
        if (normalized.contains("SCHEDULE") || normalized.contains("LICH") || normalized.contains("LỊCH")) {
            return RentEscalationType.SCHEDULE;
        }
        if (normalized.equals("NONE") || normalized.equals("KHONG") || normalized.equals("KHÔNG")) {
            return RentEscalationType.NONE;
        }
        try {
            return RentEscalationType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Loại tăng giá không hợp lệ: " + raw);
        }
    }

    public static List<RentScheduleItemRequest> parseScheduleRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            try {
                return MAPPER.readValue(trimmed,
                        MAPPER.getTypeFactory().constructCollectionType(List.class, RentScheduleItemRequest.class));
            } catch (Exception e) {
                throw new BusinessException("Lịch tăng giá JSON không hợp lệ");
            }
        }
        List<RentScheduleItemRequest> items = new ArrayList<>();
        for (String part : trimmed.split("[;,]")) {
            String[] kv = part.split("[=:]");
            if (kv.length != 2) {
                throw new BusinessException("Lịch tăng giá phải dạng 13:11000000;25:12000000");
            }
            RentScheduleItemRequest item = new RentScheduleItemRequest();
            try {
                item.setFromMonth(Integer.parseInt(kv[0].trim()));
                String digits = kv[1].trim().replaceAll("[^0-9]", "");
                item.setAmount(new BigDecimal(digits));
            } catch (NumberFormatException e) {
                throw new BusinessException("Lịch tăng giá không hợp lệ: " + part);
            }
            items.add(item);
        }
        return items;
    }
}
