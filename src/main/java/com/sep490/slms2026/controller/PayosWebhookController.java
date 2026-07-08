package com.sep490.slms2026.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sep490.slms2026.service.PayosService;
import com.sep490.slms2026.service.TenantBillingService;
import com.sep490.slms2026.service.TenantOnboardingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Webhook PayOS gọi về khi có biến động thanh toán. Endpoint này PUBLIC (không JWT)
 * — bảo mật bằng chữ ký HMAC trong payload.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payos")
@RequiredArgsConstructor
public class PayosWebhookController {

    private final PayosService payosService;
    private final TenantOnboardingService tenantOnboardingService;
    private final com.sep490.slms2026.service.TenantBillingService tenantBillingService;

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleWebhook(@RequestBody JsonNode payload) {
        try {
            JsonNode data = payload.path("data");
            String signature = payload.path("signature").asText(null);

            if (data.isMissingNode() || data.isNull()) {
                // PayOS gửi ping xác minh khi đăng ký webhook -> trả 200
                return ResponseEntity.ok(Map.of("success", true));
            }

            if (!payosService.verifyWebhookSignature(data, signature)) {
                log.warn("PayOS webhook sai chữ ký, bỏ qua. orderCode={}", data.path("orderCode").asText());
                return ResponseEntity.ok(Map.of("success", false));
            }

            // code "00" = thanh toán thành công
            String code = payload.path("code").asText(data.path("code").asText(""));
            long orderCode = data.path("orderCode").asLong(0);
            if ("00".equals(code) && orderCode > 0) {
                tenantOnboardingService.markDepositPaid(orderCode);
                tenantBillingService.markInvoicePaidByPayosOrderCode(orderCode);
                log.info("PayOS: đã ghi nhận thanh toán orderCode={}", orderCode);
            }
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("Lỗi xử lý webhook PayOS", e);
            // vẫn trả 200 để PayOS không spam retry; đã log để điều tra
            return ResponseEntity.ok(Map.of("success", false));
        }
    }
}
