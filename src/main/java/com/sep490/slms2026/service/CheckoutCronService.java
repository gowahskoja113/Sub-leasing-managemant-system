package com.sep490.slms2026.service;

public interface CheckoutCronService {
    void autoAcceptSettlementTask();

    /**
     * Host đã chuyển cọc, khách im lặng 30 ngày (không xác nhận / không khiếu nại) → khoá TK.
     */
    void disableAccountsAfterSilentRefund();
}
