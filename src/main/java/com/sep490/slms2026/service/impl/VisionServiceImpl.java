package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.VisionLabelsResponse;
import com.sep490.slms2026.exception.BusinessException;
import com.sep490.slms2026.security.CustomUserDetails;
import com.sep490.slms2026.security.SecurityUtils;
import com.sep490.slms2026.service.VisionService;
import com.sep490.slms2026.vision.GoogleVisionProvider;
import com.sep490.slms2026.vision.ImageSource;
import com.sep490.slms2026.vision.LocalVisionProvider;
import com.sep490.slms2026.vision.VisionProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Orchestrator: validate URL, chọn provider (google | local | auto), rate-limit chỉ cho Google.
 * Logic gọi model/API nằm ở {@link VisionProvider}.
 */
@Slf4j
@Service
public class VisionServiceImpl implements VisionService {

    private static final long WINDOW_MS = 3_600_000L;

    @Value("${vision.provider:auto}")
    private String providerMode;

    @Value("${vision.rate-limit-per-hour:20}")
    private int rateLimitPerHour;

    /** Hosts được phép (vd res.cloudinary.com); rỗng = chỉ chặn scheme không phải https. */
    @Value("${vision.allowed-image-hosts:res.cloudinary.com}")
    private String allowedImageHosts;

    private final Map<String, VisionProvider> providersByName;
    private final HttpClient httpClient;

    /** userId -> timestamps (ms) các lần gọi Google trong cửa sổ 1 giờ. */
    private final Map<UUID, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    public VisionServiceImpl(List<VisionProvider> providers, HttpClient visionHttpClient) {
        this.httpClient = visionHttpClient;
        Map<String, VisionProvider> map = new ConcurrentHashMap<>();
        for (VisionProvider p : providers) {
            map.put(p.name().toLowerCase(Locale.ROOT), p);
        }
        this.providersByName = Map.copyOf(map);
    }

    @Override
    public VisionLabelsResponse detectLabels(String imageUrl) {
        validateImageUrl(imageUrl);
        ImageSource src = ImageSource.of(imageUrl, httpClient);

        List<VisionProvider> order = providersInOrder();
        if (order.isEmpty()) {
            throw new BusinessException("Chưa cấu hình dịch vụ nhận diện ảnh. Vui lòng liên hệ quản trị.");
        }

        Exception lastError = null;
        boolean anyAvailable = false;
        boolean googleQuotaExhausted = false;

        for (VisionProvider p : order) {
            if (!p.isAvailable()) {
                log.debug("Vision provider {} chưa sẵn sàng, bỏ qua", p.name());
                continue;
            }
            anyAvailable = true;

            // Rate limit chỉ Google. Hết slot → skip, thử local (không tốn quota).
            if (GoogleVisionProvider.NAME.equals(p.name()) && !tryAcquireGoogleQuota()) {
                googleQuotaExhausted = true;
                log.info("Hết quota Google Vision trong giờ, thử provider tiếp theo");
                continue;
            }

            try {
                var labels = p.detect(src);
                log.info("Vision provider={} trả {} nhãn", p.name(), labels.size());
                return VisionLabelsResponse.builder().labels(labels).build();
            } catch (Exception e) {
                lastError = e;
                log.warn("Vision provider {} lỗi, thử provider tiếp theo: {}", p.name(), e.getMessage());
            }
        }

        if (!anyAvailable) {
            throw new BusinessException("Chưa cấu hình dịch vụ nhận diện ảnh. Vui lòng liên hệ quản trị.");
        }
        if (googleQuotaExhausted && lastError == null
                && !isProviderAvailable(LocalVisionProvider.NAME)) {
            throw new BusinessException("Bạn đã gửi quá nhiều ảnh để nhận diện. Vui lòng thử lại sau.");
        }
        if (lastError instanceof BusinessException be) {
            throw be;
        }
        throw new BusinessException("Không nhận diện được ảnh. Vui lòng thử ảnh khác.");
    }

    private boolean isProviderAvailable(String name) {
        VisionProvider p = providersByName.get(name);
        return p != null && p.isAvailable();
    }

    /**
     * auto → [google, local]; google → [google]; local → [local].
     */
    List<VisionProvider> providersInOrder() {
        String mode = StringUtils.hasText(providerMode)
                ? providerMode.trim().toLowerCase(Locale.ROOT)
                : "auto";

        List<String> names = switch (mode) {
            case GoogleVisionProvider.NAME -> List.of(GoogleVisionProvider.NAME);
            case LocalVisionProvider.NAME -> List.of(LocalVisionProvider.NAME);
            default -> List.of(GoogleVisionProvider.NAME, LocalVisionProvider.NAME); // auto
        };

        List<VisionProvider> ordered = new ArrayList<>();
        for (String name : names) {
            VisionProvider p = providersByName.get(name);
            if (p != null) {
                ordered.add(p);
            }
        }
        return ordered;
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
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(allowedImageHosts)) {
            return;
        }
        boolean allowed = false;
        for (String raw : allowedImageHosts.split(",")) {
            String allowedHost = raw.trim().toLowerCase(Locale.ROOT);
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

    /**
     * @return true nếu còn slot quota và đã ghi nhận 1 lượt; false nếu đã chạm trần.
     */
    private boolean tryAcquireGoogleQuota() {
        if (rateLimitPerHour <= 0) {
            return true;
        }
        CustomUserDetails user = SecurityUtils.requireCurrentUser();
        UUID userId = user.getId();
        long now = System.currentTimeMillis();
        Deque<Long> times = requestLog.computeIfAbsent(userId, id -> new ConcurrentLinkedDeque<>());
        synchronized (times) {
            while (true) {
                Long oldest = times.peekFirst();
                if (oldest == null || now - oldest < WINDOW_MS) {
                    break;
                }
                times.pollFirst();
            }
            if (times.size() >= rateLimitPerHour) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }
}
