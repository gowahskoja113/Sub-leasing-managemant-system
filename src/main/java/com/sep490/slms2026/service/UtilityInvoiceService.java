package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.CreateUtilityInvoiceRequest;
import com.sep490.slms2026.dto.response.RoomUtilitySumSnapshot;
import com.sep490.slms2026.dto.response.UtilityInvoiceHistoryResponse;
import com.sep490.slms2026.dto.response.UtilityInvoiceResponse;
import com.sep490.slms2026.entity.UtilityBill;
import com.sep490.slms2026.enums.UtilityType;

import java.math.BigDecimal;

public interface UtilityInvoiceService {

    UtilityInvoiceResponse createRoomInvoice(Long propertyId, Long roomId, CreateUtilityInvoiceRequest request);

    UtilityInvoiceResponse createPropertyInvoice(Long propertyId, CreateUtilityInvoiceRequest request);

    /**
     * Phát hành hoá đơn tiện ích từ hoá đơn tổng nhà nguyên căn — cùng transaction với publish bill.
     * Bỏ bắt ảnh công tơ vì giấy EVN/nước đã là chứng từ.
     */
    UtilityInvoiceResponse createFromWholeHouseBill(UtilityBill bill, BigDecimal prevReading, BigDecimal newReading);

    /**
     * Nhà chia phòng — điện: phát hành từ mọi bản chốt chỉ số chưa có invoiceId.
     * Vượt ROOM_SUM_EXCEEDS_BILL → không phát hành phòng nào.
     * Phòng chưa chốt → bỏ qua, không chặn các phòng khác.
     */
    int issueElectricFromSavedReadings(UtilityBill bill);

    /**
     * Phát hành một phòng từ bản chốt đã lưu (sau khi admin đã có EVN, quản lý chốt muộn).
     */
    UtilityInvoiceResponse issueElectricFromSavedReading(UtilityBill bill, Long meterReadingId);

    /**
     * Nhà chia phòng — nước: phát hành từ mọi bản chốt WATER chưa có invoiceId (không lọc theo period).
     */
    int issueWaterFromSavedReadings(UtilityBill bill);

    /**
     * Phát hành một phòng nước từ bản chốt đã lưu (chốt muộn sau khi admin đã publish).
     */
    UtilityInvoiceResponse issueWaterFromSavedReading(UtilityBill bill, Long meterReadingId);

    UtilityInvoiceHistoryResponse listInvoices(Long propertyId, String period, String type);

    /**
     * Tổng consumption hoá đơn phòng còn hiệu lực trong kỳ (bỏ CANCELLED).
     * {@code excludeRoomId} — bỏ phòng đang ghi lại để không cộng dồn bản cũ.
     */
    RoomUtilitySumSnapshot sumActiveRoomConsumptions(
            Long propertyId, String period, UtilityType type, Long excludeRoomId);
}
