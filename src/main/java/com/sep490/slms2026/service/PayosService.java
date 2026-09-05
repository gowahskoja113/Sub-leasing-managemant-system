package com.sep490.slms2026.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * Tích hợp PayOS (https://payos.vn) để thu tiền cọc.
 * Tự gọi REST + ký HMAC-SHA256 theo tài liệu PayOS, không phụ thuộc SDK ngoài.
 */
public interface PayosService {

    class PaymentLink {
        public long orderCode;
        public long amount;
        public String checkoutUrl;
        public String qrCode;
        public String paymentLinkId;
    }

    /** Tạo link/QR với thời hạn (PayOS expiredAt). */
    PaymentLink createPaymentLink(long orderCode, long amount, String description, LocalDateTime expiredAt);

    default PaymentLink createPaymentLink(long orderCode, long amount, String description) {
        return createPaymentLink(orderCode, amount, description, null);
    }

    /** Kiểm tra chữ ký webhook PayOS hợp lệ. */
    boolean verifyWebhookSignature(JsonNode data, String signature);

    /** Hỏi trạng thái đơn thanh toán trực tiếp từ PayOS (PAID/PENDING/CANCELLED/EXPIRED), null nếu lỗi. */
    String getPaymentStatus(long orderCode);

    boolean isConfigured();
}
