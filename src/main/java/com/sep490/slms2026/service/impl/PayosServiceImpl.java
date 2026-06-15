package com.sep490.slms2026.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.service.PayosService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@Slf4j
@Service
public class PayosServiceImpl implements PayosService {

    private static final String CREATE_URL = "https://api-merchant.payos.vn/v2/payment-requests";

    @Value("${payos.client-id:}")
    private String clientId;
    @Value("${payos.api-key:}")
    private String apiKey;
    @Value("${payos.checksum-key:}")
    private String checksumKey;
    @Value("${payos.return-url:}")
    private String returnUrl;
    @Value("${payos.cancel-url:}")
    private String cancelUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(clientId)
                && StringUtils.hasText(apiKey)
                && StringUtils.hasText(checksumKey);
    }

    @Override
    public PaymentLink createPaymentLink(long orderCode, long amount, String description) {
        if (!isConfigured()) {
            throw new BusinessException("Chưa cấu hình PayOS (PAYOS_CLIENT_ID / PAYOS_API_KEY / PAYOS_CHECKSUM_KEY).");
        }
        // mô tả tối đa 25 ký tự theo giới hạn PayOS
        String desc = description.length() > 25 ? description.substring(0, 25) : description;

        // Chữ ký theo PayOS: các field sắp theo alphabet
        String dataToSign = "amount=" + amount
                + "&cancelUrl=" + cancelUrl
                + "&description=" + desc
                + "&orderCode=" + orderCode
                + "&returnUrl=" + returnUrl;
        String signature = hmacSha256(dataToSign, checksumKey);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("orderCode", orderCode);
        body.put("amount", amount);
        body.put("description", desc);
        body.put("cancelUrl", cancelUrl);
        body.put("returnUrl", returnUrl);
        body.put("signature", signature);

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(CREATE_URL))
                    .header("Content-Type", "application/json")
                    .header("x-client-id", clientId)
                    .header("x-api-key", apiKey)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());

            String code = root.path("code").asText("");
            if (!"00".equals(code)) {
                log.warn("PayOS tạo link lỗi: {}", root);
                throw new BusinessException("Tạo thanh toán PayOS thất bại: " + root.path("desc").asText());
            }

            JsonNode data = root.path("data");
            PaymentLink link = new PaymentLink();
            link.orderCode = data.path("orderCode").asLong(orderCode);
            link.amount = data.path("amount").asLong(amount);
            link.checkoutUrl = data.path("checkoutUrl").asText(null);
            link.qrCode = data.path("qrCode").asText(null);
            link.paymentLinkId = data.path("paymentLinkId").asText(null);
            return link;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi gọi PayOS", e);
            throw new BusinessException("Không kết nối được PayOS. Vui lòng thử lại.");
        }
    }

    @Override
    public String getPaymentStatus(long orderCode) {
        if (!isConfigured()) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://api-merchant.payos.vn/v2/payment-requests/" + orderCode))
                    .header("x-client-id", clientId)
                    .header("x-api-key", apiKey)
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            if (!"00".equals(root.path("code").asText(""))) {
                log.warn("PayOS get status lỗi: {}", root);
                return null;
            }
            return root.path("data").path("status").asText(null);
        } catch (Exception e) {
            log.error("Lỗi hỏi trạng thái PayOS", e);
            return null;
        }
    }

    @Override
    public boolean verifyWebhookSignature(JsonNode data, String signature) {
        if (data == null || !StringUtils.hasText(signature) || !StringUtils.hasText(checksumKey)) {
            return false;
        }
        // PayOS: build querystring từ các field của data, sắp theo alphabet key=value&...
        List<String> keys = new ArrayList<>();
        Iterator<String> it = data.fieldNames();
        while (it.hasNext()) keys.add(it.next());
        Collections.sort(keys);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            JsonNode v = data.get(k);
            String value = (v == null || v.isNull()) ? "" : (v.isValueNode() ? v.asText() : v.toString());
            if (i > 0) sb.append("&");
            sb.append(k).append("=").append(value);
        }
        String expected = hmacSha256(sb.toString(), checksumKey);
        return expected.equalsIgnoreCase(signature);
    }

    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : raw) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new BusinessException("Lỗi tạo chữ ký PayOS");
        }
    }
}
