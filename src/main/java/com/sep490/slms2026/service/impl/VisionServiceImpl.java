package com.sep490.slms2026.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sep490.slms2026.dto.response.VisionLabelItem;
import com.sep490.slms2026.dto.response.VisionLabelsResponse;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.VisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@Service
public class VisionServiceImpl implements VisionService {

    private static final long WINDOW_MS = 3_600_000L;

    @Value("${vision.google.api-key:}")
    private String apiKey;

    @Value("${vision.google.base-url:https://vision.googleapis.com/v1/images:annotate}")
    private String baseUrl;

    @Value("${vision.max-results:20}")
    private int maxResults;

    @Value("${vision.rate-limit-per-hour:20}")
    private int rateLimitPerHour;

    /** Hosts được phép (vd res.cloudinary.com); rỗng = chỉ chặn scheme không phải https. */
    @Value("${vision.allowed-image-hosts:res.cloudinary.com}")
    private String allowedImageHosts;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /** userId -> timestamps (ms) các lần gọi trong cửa sổ 1 giờ. */
    private final Map<UUID, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    @Override
    public VisionLabelsResponse detectLabels(String imageUrl) {
        validateImageUrl(imageUrl);
        enforceRateLimit();

        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("Chưa cấu hình Google Vision API key. Vui lòng liên hệ quản trị.");
        }

        try {
            String body = buildRequestBody(imageUrl);
            String url = baseUrl + "?key=" + URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
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

            return VisionLabelsResponse.builder().labels(labels).build();
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

    private void validateImageUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            throw new BusinessException("Thiếu URL ảnh");
        }
        final URI uri;
        try {
            uri = URI.create(imageUrl.trim());
        } catch (Exception e) {
            throw new BusinessException("URL ảnh không hợp lệ");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new BusinessException("Chỉ chấp nhận URL ảnh HTTPS");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException("URL ảnh không hợp lệ");
        }
        String normalizedHost = host.toLowerCase();
        if (!StringUtils.hasText(allowedImageHosts)) {
            return;
        }
        boolean allowed = false;
        for (String raw : allowedImageHosts.split(",")) {
            String allowedHost = raw.trim().toLowerCase();
            if (allowedHost.isEmpty()) {
                continue;
            }
            if (normalizedHost.equals(allowedHost) || normalizedHost.endsWith("." + allowedHost)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new BusinessException("Chỉ chấp nhận ảnh đã upload lên hệ thống (Cloudinary)");
        }
    }

    private void enforceRateLimit() {
        if (rateLimitPerHour <= 0) {
            return;
        }
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        UUID userId = user.getId();
        long now = System.currentTimeMillis();
        Deque<Long> times = requestLog.computeIfAbsent(userId, id -> new ConcurrentLinkedDeque<>());
        while (true) {
            Long oldest = times.peekFirst();
            if (oldest == null || now - oldest < WINDOW_MS) {
                break;
            }
            times.pollFirst();
        }
        if (times.size() >= rateLimitPerHour) {
            throw new BusinessException("Bạn đã gửi quá nhiều ảnh để nhận diện. Vui lòng thử lại sau.");
        }
        times.addLast(now);
    }
}
