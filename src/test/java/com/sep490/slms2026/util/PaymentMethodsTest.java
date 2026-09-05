package com.sep490.slms2026.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentMethodsTest {

    @Test
    void payosMapsToQr() {
        assertEquals("QR", PaymentMethods.toPublic("PAYOS"));
        assertEquals("QR", PaymentMethods.toPublic("payos"));
        assertEquals("CASH", PaymentMethods.toPublic("CASH"));
        assertEquals("QR", PaymentMethods.toPublic("QR"));
        assertEquals("BANK_TRANSFER", PaymentMethods.toPublic("BANK_TRANSFER"));
    }
}
