package com.sep490.slms2026.util;

import com.sep490.slms2026.dto.response.TenantInvoiceItemResponse;
import com.sep490.slms2026.entity.TenantInvoice;
import com.sep490.slms2026.enums.TenantInvoiceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvoiceItemBuilderTest {

    @Test
    void onboardItems_useProRataNotFullMonthRent() {
        TenantInvoice invoice = TenantInvoice.builder()
                .invoiceType(TenantInvoiceType.OTHER)
                .note("ONBOARD|depositAmount=5000000|depositMonths=1|rentAmount=5000000"
                        + "|firstRentAmount=3064516|billedDays=19|daysInMonth=31")
                .grandTotal(new BigDecimal("8064516"))
                .totalAmount(new BigDecimal("8064516"))
                .build();

        List<TenantInvoiceItemResponse> items = InvoiceItemBuilder.buildItems(invoice);
        assertEquals(2, items.size());
        assertEquals("Tiền nhà chu kỳ đầu", items.get(0).getLabel());
        assertEquals(0, new BigDecimal("3064516").compareTo(items.get(0).getAmount()));
        assertEquals("Tiền cọc (1 tháng)", items.get(1).getLabel());
        assertEquals(0, new BigDecimal("5000000").compareTo(items.get(1).getAmount()));
        assertEquals(0, invoice.getGrandTotal().compareTo(
                items.get(0).getAmount().add(items.get(1).getAmount())));
    }

    @Test
    void onboardItems_inferProRataWhenFirstRentMissingFromNote() {
        TenantInvoice invoice = TenantInvoice.builder()
                .invoiceType(TenantInvoiceType.OTHER)
                .note("ONBOARD|depositAmount=5000000|depositMonths=1|rentAmount=5000000")
                .grandTotal(new BigDecimal("8064516"))
                .totalAmount(new BigDecimal("8064516"))
                .build();

        List<TenantInvoiceItemResponse> items = InvoiceItemBuilder.buildItems(invoice);
        assertEquals(0, new BigDecimal("3064516").compareTo(items.get(0).getAmount()));
        assertEquals("Tiền nhà chu kỳ đầu", items.get(0).getLabel());
    }
}
