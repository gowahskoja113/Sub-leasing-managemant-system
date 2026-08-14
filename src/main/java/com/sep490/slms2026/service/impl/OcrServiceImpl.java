package com.sep490.slms2026.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.slms2026.dto.response.OcrUtilityBillResponse;
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
    /** Dãy số (có thể xen khoảng trắng/dấu chấm) đứng ngay trước đơn vị kWh hoặc m³. */
    private static final Pattern READING_BEFORE_UNIT = Pattern.compile(
            "([\\d][\\d.,\\s]*)\\s*(?:kwh|m3|m³)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PERIOD_PATTERN = Pattern.compile(
            "(\\d{1,2}[/.]\\d{1,2}\\s*[–\\-]\\s*\\d{1,2}[/.]\\d{2,4})",
            Pattern.CASE_INSENSITIVE);

    @Value("${ocr.space.base-url}")
    private String baseUrl;

    @Value("${ocr.space.api-key}")
    private String apiKey;

    // Google Cloud Vision — dùng chung key/endpoint với VisionServiceImpl (nhận diện
    // vật thể), chỉ khác feature là TEXT_DETECTION.
    @Value("${vision.google.base-url:}")
    private String visionBaseUrl;

    @Value("${vision.google.api-key:}")
    private String visionApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * Đọc chỉ số đồng hồ.
     *
     * Ưu tiên Google Cloud Vision: OCR.space KHÔNG đọc được mặt công tơ cơ (chữ số
     * nằm trong từng ô có khe, nền đen, hàng lẻ màu đỏ) — đã thử crop đúng ô số,
     * phóng to, đảo màu, tăng tương phản và cả OCREngine 1 lẫn 2, tất cả đều ra rác
     * kiểu "1013 p 18". Cùng ảnh đó Vision đọc ra thẳng "030815 kWh" mà không cần
     * xử lý gì thêm.
     *
     * Vẫn giữ OCR.space làm dự phòng cho trường hợp chưa cấu hình key Vision, hết
     * quota hoặc Vision lỗi — thà đọc kém còn hơn chặn luồng đón khách.
     */
    @Override
    public OcrMeterResponse readMeter(String imageUrl) {
        String visionText = readTextWithVision(imageUrl);
        if (visionText != null && !visionText.isBlank()) {
            List<String> numbers = extractNumbers(visionText);
            return OcrMeterResponse.builder()
                    .reading(pickBest(numbers, visionText))
                    .numbers(numbers)
                    .rawText(visionText)
                    .build();
        }
        return readMeterWithOcrSpace(imageUrl);
    }

    private OcrMeterResponse readMeterWithOcrSpace(String imageUrl) {
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
                    .reading(pickBest(numbers, text))
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
    public OcrUtilityBillResponse readUtilityBill(String imageUrl) {
        String text = fetchOcrText(imageUrl);
        List<String> numbers = extractNumbers(text);

        BigDecimal totalQuantity = findLabeledNumber(text, numbers,
                "kwh", "điện năng", "tiêu thụ", "sản lượng");
        BigDecimal totalAmount = findLabeledNumber(text, numbers,
                "tổng tiền", "thanh toán", "số tiền", "phải thu");

        String billingPeriod = "";
        Matcher periodMatcher = PERIOD_PATTERN.matcher(text);
        if (periodMatcher.find()) {
            billingPeriod = periodMatcher.group(1).trim();
        }

        return OcrUtilityBillResponse.builder()
                .totalQuantity(totalQuantity)
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
    /**
     * Gọi Google Cloud Vision DOCUMENT_TEXT_DETECTION (đọc tốt hơn TEXT_DETECTION
     * với màn LCD 7 đoạn / đồng hồ điện tử; mentor feedback 08/2026).
     * Trả null khi chưa cấu hình key hoặc gọi lỗi — để caller rơi về OCR.space.
     */
    private String readTextWithVision(String imageUrl) {
        if (visionApiKey == null || visionApiKey.isBlank()
                || visionBaseUrl == null || visionBaseUrl.isBlank()) {
            return null;
        }
        try {
            String body = objectMapper.writeValueAsString(java.util.Map.of(
                    "requests", List.of(java.util.Map.of(
                            "image", java.util.Map.of("source", java.util.Map.of("imageUri", imageUrl)),
                            "features", List.of(java.util.Map.of(
                                    "type", "DOCUMENT_TEXT_DETECTION"))))));

            String url = visionBaseUrl + "?key="
                    + URLEncoder.encode(visionApiKey.trim(), StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode first = objectMapper.readTree(response.body()).path("responses").path(0);

            if (!first.path("error").isMissingNode()) {
                log.warn("Vision OCR lỗi: {}", first.path("error").path("message").asText());
                return null;
            }
            return first.path("fullTextAnnotation").path("text").asText("");
        } catch (Exception e) {
            log.warn("Không gọi được Vision OCR, rơi về OCR.space", e);
            return null;
        }
    }

    /**
     * Chọn con số là CHỈ SỐ trong đám số đọc được.
     *
     * Trước đây chọn số DÀI NHẤT, nên trên công tơ EMIC luôn bắt trúng serial
     * "Số SX: 24562125" (8 chữ số) thay vì chỉ số "030815" (6 chữ số). Giờ ưu tiên
     * số đứng NGAY TRƯỚC đơn vị (kWh / m³) — đó là vị trí của ô hiển thị — rồi mới
     * xét độ dài cho các trường hợp không nhận ra đơn vị.
     */
    private String pickBest(List<String> numbers, String text) {
        String beforeUnit = findNumberBeforeUnit(text);
        if (beforeUnit != null) return beforeUnit;

        return numbers.stream()
                .max((a, b) -> Integer.compare(a.replace(".", "").length(), b.replace(".", "").length()))
                .orElse("");
    }

    /** Số đứng ngay trước kWh/m³ trên cùng một dòng; bỏ dòng hằng số "900 vòng/kWh". */
    private String findNumberBeforeUnit(String text) {
        if (text == null || text.isBlank()) return null;
        for (String line : text.split("\\R")) {
            String low = line.toLowerCase();
            if (low.contains("vòng") || low.contains("vong") || low.contains("imp")) continue;

            Matcher m = READING_BEFORE_UNIT.matcher(line);
            if (m.find()) {
                String digits = m.group(1).replaceAll("[^0-9]", "");
                if (digits.length() >= 3 && digits.length() <= 8) return digits;
            }
        }
        return null;
    }

    private String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }
}

