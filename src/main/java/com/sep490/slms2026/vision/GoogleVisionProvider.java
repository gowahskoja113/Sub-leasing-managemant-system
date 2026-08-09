package com.sep490.slms2026.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sep490.slms2026.dto.response.VisionLabelItem;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Google Cloud Vision LABEL_DETECTION — gửi imageUri, Google tự tải ảnh (BE không tải).
 */
@Slf4j
@Component
public class GoogleVisionProvider implements VisionProvider {

    public static final String NAME = "google";

    @Value("${vision.google.api-key:}")
    private String apiKey;

    @Value("${vision.google.base-url:https://vision.googleapis.com/v1/images:annotate}")
    private String baseUrl;

    @Value("${vision.max-results:20}")
    private int maxResults;

    /** Timeout gọi Google. Ở chế độ auto nên ~8s để fallback local nhanh. */
    @Value("${vision.google.timeout-seconds:8}")
    private int timeoutSeconds;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GoogleVisionProvider(ObjectMapper objectMapper, HttpClient visionHttpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = visionHttpClient;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAvailable() {
        return StringUtils.hasText(apiKey);
    }

    @Override
    public List<VisionLabelItem> detect(ImageSource src) {
        if (!isAvailable()) {
            throw new BusinessException("Chưa cấu hình Google Vision API key. Vui lòng liên hệ quản trị.");
        }
        try {
            String body = buildRequestBody(src.url());
            String url = baseUrl + "?key=" + URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(Math.max(2, timeoutSeconds)))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Google Vision HTTP {}: {}", response.statusCode(), response.body());
                throw new BusinessException("Dịch vụ nhận diện ảnh đang lỗi. Vui lòng thử lại.");
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode first = root.path("responses").path(0);

            if (first.has("error")) {
                String msg = first.path("error").path("message").asText("unknown");
                log.warn("Google Vision lỗi: {}", msg);
                throw new BusinessException("Không nhận diện được ảnh. Vui lòng thử ảnh khác.");
            }

            List<VisionLabelItem> labels = new ArrayList<>();
            for (JsonNode ann : first.path("labelAnnotations")) {
                String name = ann.path("description").asText("");
                if (name.isBlank()) {
                    continue;
                }
                labels.add(VisionLabelItem.builder()
                        .name(name)
                        .score(ann.path("score").asDouble(0))
                        .build());
            }
            return labels;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Lỗi gọi Google Vision", e);
            throw new BusinessException("Dịch vụ nhận diện ảnh đang lỗi. Vui lòng thử lại.");
        }
    }

    private String buildRequestBody(String imageUrl) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode requests = root.putArray("requests");
        ObjectNode req = requests.addObject();
        req.putObject("image").putObject("source").put("imageUri", imageUrl);
        ArrayNode features = req.putArray("features");
        ObjectNode feature = features.addObject();
        feature.put("type", "LABEL_DETECTION");
        feature.put("maxResults", Math.max(1, maxResults));
        return objectMapper.writeValueAsString(root);
    }
}
