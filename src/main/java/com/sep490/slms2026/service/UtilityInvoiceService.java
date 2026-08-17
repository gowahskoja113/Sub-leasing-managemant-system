package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.CreateUtilityInvoiceRequest;
import com.sep490.slms2026.dto.response.UtilityInvoiceHistoryResponse;
import com.sep490.slms2026.dto.response.UtilityInvoiceResponse;
import com.sep490.slms2026.entity.UtilityBill;

import java.math.BigDecimal;

public interface UtilityInvoiceService {

    UtilityInvoiceResponse createRoomInvoice(Long propertyId, Long roomId, CreateUtilityInvoiceRequest request);

    UtilityInvoiceResponse createPropertyInvoice(Long propertyId, CreateUtilityInvoiceRequest request);

    /**
     * Phát hành hoá đơn tiện ích từ hoá đơn tổng nhà nguyên căn — cùng transaction với publish bill.
     * Bỏ bắt ảnh công tơ vì giấy EVN/nước đã là chứng từ.
     */
    UtilityInvoiceResponse createFromWholeHouseBill(UtilityBill bill, BigDecimal prevReading, BigDecimal newReading);

    UtilityInvoiceHistoryResponse listInvoices(Long propertyId, String period, String type);
}
