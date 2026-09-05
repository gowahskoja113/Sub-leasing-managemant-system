package com.sep490.slms2026.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sep490.slms2026.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/**
 * Gemini (Google AI Studio) — nhìn cả bộ ảnh rồi viết 1 đoạn hiện trạng tiếng Việt.
 * Chi phí: API key {@code GEMINI_API_KEY} do công ty trả (Google AI Studio / Cloud).
 */
@Slf4j
@Component
public class GeminiRoomDescribeProvider {

    static final String PROMPT = """
            Bạn là thư ký viết biên bản bàn giao phòng trọ bằng tiếng Việt.
            Dựa CHỈ vào các ảnh được gửi, viết 2–3 câu mô tả hiện trạng phòng theo giọng biên bản.

            Ràng buộc bắt buộc:
            - Chỉ mô tả những gì NHÌN THẤY RÕ trong ảnh.
            - Hư hỏng / vết bẩn / thiếu thiết bị: chỉ nêu khi thấy rõ.
            - KHÔNG suy đoán. KHÔNG viết "còn tốt", "không hư", "tường không nứt", "thiết bị hoạt động bình thường" nếu ảnh không cho thấy đủ để kết luận.
            - Không bịa thêm đồ không có trong ảnh. Không nêu giá, địa chỉ, tên người.
            - Không dùng markdown, không gạch đầu dòng, không tiêu đề.
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    @Value("${vision.gemini.api-key:}")
    private String apiKey;

    @Value("${vision.gemini.model:gemini-2.0-flash}")
    private String model;

    @Value("${vision.gemini.base-url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String baseUrl;

    @Value("${vision.gemini.timeout-seconds:30}")
    private int timeoutSeconds;

    public GeminiRoomDescribeProvider(HttpClient visionHttpClient) {
        this.httpClient = visionHttpClient;
    }

    public boolean isAvailable() {
        return StringUtils.hasText(apiKey);
    }

    public String modelName() {
        return StringUtils.hasText(model) ? model.trim() : "gemini-2.0-flash";
    }

    public String describe(List<ImageSource> images) {
        if (!isAvailable()) {
            throw new BusinessException("VISION_DESCRIBE_UNAVAILABLE",
                    "Chưa cấu hình Gemini API key. Vui lòng mô tả tay.");
        }
        if (images == null || images.isEmpty()) {
            throw new BusinessException("VISION_DESCRIBE_UNAVAILABLE", "Thiếu ảnh để mô tả hiện trạng.");
        }
        try {
            String body = buildRequestBody(images);
            String url = baseUrl.replaceAll("/$", "")
                    + "/" + URLEncoder.encode(modelName(), StandardCharsets.UTF_8)
                    + ":generateContent?key="
                    + URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(Math.max(8, timeoutSeconds)))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Gemini describe-room HTTP {}: {}", response.statusCode(), response.body());
                throw new BusinessException("VISION_DESCRIBE_UNAVAILABLE",
                        "Không tạo được mô tả hiện trạng. Vui lòng nhập tay.");
            }

            JsonNode root = objectMapper.readTree(response.body());
            String text = extractText(root);
            if (!StringUtils.hasText(text)) {
                log.warn("Gemini describe-room trả rỗng: {}", response.body());
                throw new BusinessException("VISION_DESCRIBE_UNAVAILABLE",
                        "Không tạo được mô tả hiện trạng. Vui lòng nhập tay.");
            }
            return text.trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gemini describe-room lỗi: {}", e.getMessage());
            throw new BusinessException("VISION_DESCRIBE_UNAVAILABLE",
                    "Không tạo được mô tả hiện trạng. Vui lòng nhập tay.");
        }
    }

    private String extractText(JsonNode root) {
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            String t = part.path("text").asText("");
            if (!t.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(t.trim());
            }
        }
        return sb.toString();
    }

    private String buildRequestBody(List<ImageSource> images) throws Exception {
        // Bắn hết task trước, join sau — stream pipeline map(join) ngay sau supplyAsync vẫn tuần tự.
        List<CompletableFuture<byte[]>> downloads = IntStream.range(0, images.size())
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> images.get(i).bytes()))
                .toList();
        List<byte[]> imageBytes = downloads.stream()
                .map(CompletableFuture::join)
                .toList();

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", PROMPT);
        for (int i = 0; i < images.size(); i++) {
            byte[] bytes = imageBytes.get(i);
            ObjectNode inline = parts.addObject().putObject("inline_data");
            inline.put("mime_type", guessMime(images.get(i).url()));
            inline.put("data", Base64.getEncoder().encodeToString(bytes));
        }
        ObjectNode gen = root.putObject("generationConfig");
        gen.put("temperature", 0.2);
        gen.put("maxOutputTokens", 400);
        return objectMapper.writeValueAsString(root);
    }

    private static String guessMime(String url) {
        String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (lower.contains(".png")) {
            return "image/png";
        }
        if (lower.contains(".webp")) {
            return "image/webp";
        }
        if (lower.contains(".gif")) {
            return "image/gif";
        }
        return "image/jpeg";
    }
}
