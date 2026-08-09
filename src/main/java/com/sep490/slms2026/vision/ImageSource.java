package com.sep490.slms2026.vision;

import com.sep490.slms2026.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Nguồn ảnh lazy: {@link #url()} luôn có; {@link #bytes()} chỉ tải khi provider cần
 * (model local) và cache trong 1 lần gọi để nhánh fallback không tải hai lần.
 * Google Vision dùng {@code imageUri} — không tải qua BE.
 */
public final class ImageSource {

    private static final int MAX_BYTES = 12 * 1024 * 1024; // 12 MB

    private final String url;
    private final HttpClient httpClient;
    private final Duration downloadTimeout;
    private volatile byte[] cached;

    private ImageSource(String url, HttpClient httpClient, Duration downloadTimeout) {
        this.url = url;
        this.httpClient = httpClient;
        this.downloadTimeout = downloadTimeout;
    }

    public static ImageSource of(String imageUrl, HttpClient httpClient) {
        return of(imageUrl, httpClient, Duration.ofSeconds(20));
    }

    public static ImageSource of(String imageUrl, HttpClient httpClient, Duration downloadTimeout) {
        if (!StringUtils.hasText(imageUrl)) {
            throw new BusinessException("Thiếu URL ảnh");
        }
        return new ImageSource(imageUrl.trim(), httpClient, downloadTimeout);
    }

    public String url() {
        return url;
    }

    /** Tải ảnh 1 lần (thread-safe); các lần sau trả cache. */
    public byte[] bytes() {
        byte[] local = cached;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cached == null) {
                cached = downloadOnce(url);
            }
            return cached;
        }
    }

    private byte[] downloadOnce(String imageUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                    .timeout(downloadTimeout)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("Không tải được ảnh để nhận diện (HTTP " + response.statusCode() + ")");
            }
            byte[] body = response.body();
            if (body == null || body.length == 0) {
                throw new BusinessException("Ảnh trống, không nhận diện được");
            }
            if (body.length > MAX_BYTES) {
                throw new BusinessException("Ảnh quá lớn để nhận diện nội bộ");
            }
            return body;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Không tải được ảnh để nhận diện nội bộ");
        }
    }
}
