package com.sep490.slms2026.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.slms2026.dto.response.OcrEvnBillResponse;
import com.sep490.slms2026.dto.response.OcrMeterResponse;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.service.OcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OcrServiceImpl implements OcrService {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:[.,]\\d+)?");
    private static final Pattern PERIOD_PATTERN = Pattern.compile(
            "(\\d{1,2}[/.]\\d{1,2}\\s*[–\\-]\\s*\\d{1,2}[/.]\\d{2,4})",
            Pattern.CASE_INSENSITIVE);

    @Value("${ocr.space.base-url}")
    private String baseUrl;

    @Value("${ocr.space.api-key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public OcrMeterResponse readMeter(String imageUrl) {
        try {
            String form = "apikey=" + enc(apiKey == null ? "" : apiKey.trim())
                    + "&url=" + enc(imageUrl)
                    + "&OCREngine=2"
                    + "&scale=true"
                    + "&isTable=false"
                    + "&filetype=JPG"; // ảnh upload từ Cloudinary là JPG; tránh lỗi 'Unable to detect file extension'

            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());

            if (root.path("IsErroredOnProcessing").asBoolean(false)) {
                String msg = root.path("ErrorMessage").toString();
                log.warn("OCR.space lỗi: {}", msg);
                throw new BusinessException("Không đọc được ảnh đồng hồ. Vui lòng nhập tay.");
            }

            String text = root.path("ParsedResults").path(0).path("ParsedText").asText("");
            List<String> numbers = extractNumbers(text);

            return OcrMeterResponse.builder()
                    .reading(pickBest(numbers))
                    .numbers(numbers)
                    .rawText(text)
                    .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi gọi OCR.space", e);
            throw new BusinessException("Dịch vụ OCR đang lỗi. Vui lòng nhập chỉ số tay.");
        }
    }

    @Override
    public OcrEvnBillResponse readEvnBill(String imageUrl) {
        String text = fetchOcrText(imageUrl);
        List<String> numbers = extractNumbers(text);

        BigDecimal totalKwh = findLabeledNumber(text, numbers,
                "kwh", "điện năng", "tiêu thụ", "sản lượng");
        BigDecimal totalAmount = findLabeledNumber(text, numbers,
                "tổng tiền", "thanh toán", "số tiền", "phải thu");

        String billingPeriod = "";
        Matcher periodMatcher = PERIOD_PATTERN.matcher(text);
        if (periodMatcher.find()) {
            billingPeriod = periodMatcher.group(1).trim();
        }

        return OcrEvnBillResponse.builder()
                .totalKwh(totalKwh)
                .totalAmount(totalAmount)
                .billingPeriod(billingPeriod)
                .rawText(text)
                .build();
    }

    private String fetchOcrText(String imageUrl) {
        try {
            String form = "apikey=" + enc(apiKey == null ? "" : apiKey.trim())
                    + "&url=" + enc(imageUrl)
                    + "&OCREngine=2"
                    + "&scale=true"
                    + "&isTable=true"
                    + "&filetype=JPG";

            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());

            if (root.path("IsErroredOnProcessing").asBoolean(false)) {
                String msg = root.path("ErrorMessage").toString();
                log.warn("OCR.space lỗi: {}", msg);
                throw new BusinessException("Không đọc được hóa đơn EVN. Vui lòng nhập tay.");
            }

            return root.path("ParsedResults").path(0).path("ParsedText").asText("");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi gọi OCR.space", e);
            throw new BusinessException("Dịch vụ OCR đang lỗi. Vui lòng nhập tay.");
        }
    }

    private BigDecimal findLabeledNumber(String text, List<String> numbers, String... labels) {
        if (text == null) {
            return null;
        }
        String lower = text.toLowerCase();
        for (String label : labels) {
            int idx = lower.indexOf(label.toLowerCase());
            if (idx >= 0) {
                String snippet = text.substring(idx, Math.min(text.length(), idx + 80));
                Matcher matcher = NUMBER_PATTERN.matcher(snippet);
                String best = "";
                while (matcher.find()) {
                    String candidate = matcher.group().replace(",", "");
                    if (candidate.replace(".", "").length() >= best.replace(".", "").length()) {
                        best = candidate;
                    }
                }
                if (!best.isBlank()) {
                    return new BigDecimal(best);
                }
            }
        }
        if (numbers.isEmpty()) {
            return null;
        }
        return new BigDecimal(numbers.getLast());
    }

    private List<String> extractNumbers(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) return result;
        Matcher m = NUMBER_PATTERN.matcher(text);
        while (m.find()) {
            String n = m.group().replace(",", "");
            // bỏ số quá ngắn (nhiễu) — đồng hồ thường >= 2 chữ số
            if (n.replace(".", "").length() >= 2) {
                result.add(n);
            }
        }
        return result;
    }

    /** Chọn cụm số dài nhất làm gợi ý (chỉ số đồng hồ thường là số dài nhất trong ảnh). */
    private String pickBest(List<String> numbers) {
        return numbers.stream()
                .max((a, b) -> Integer.compare(a.replace(".", "").length(), b.replace(".", "").length()))
                .orElse("");
    }

    private String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }
}
