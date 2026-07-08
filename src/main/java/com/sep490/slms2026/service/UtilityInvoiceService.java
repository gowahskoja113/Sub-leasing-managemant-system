package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.request.CreateUtilityInvoiceRequest;
import com.sep490.slms2026.dto.response.UtilityInvoiceHistoryResponse;
import com.sep490.slms2026.dto.response.UtilityInvoiceResponse;

public interface UtilityInvoiceService {

    UtilityInvoiceResponse createRoomInvoice(Long propertyId, Long roomId, CreateUtilityInvoiceRequest request);

    UtilityInvoiceResponse createPropertyInvoice(Long propertyId, CreateUtilityInvoiceRequest request);

    UtilityInvoiceHistoryResponse listInvoices(Long propertyId, String period, String type);
}
