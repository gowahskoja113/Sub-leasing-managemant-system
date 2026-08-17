package com.sep490.slms2026.util;

import com.sep490.slms2026.entity.TenantInvoice;
import com.sep490.slms2026.enums.TenantInvoiceType;

public final class ManagerInvoiceMask {

    private ManagerInvoiceMask() {
    }

    /** Manager không được đọc số tiền hoá đơn tiền nhà / onboard qua API thường. */
    public static boolean shouldMaskAmount(TenantInvoice invoice) {
        if (invoice == null) {
            return false;
        }
        if (invoice.getCode() != null && invoice.getCode().startsWith("HD-ONBOARD-")) {
            return true;
        }
        return invoice.getInvoiceType() == TenantInvoiceType.RENT;
    }
}
