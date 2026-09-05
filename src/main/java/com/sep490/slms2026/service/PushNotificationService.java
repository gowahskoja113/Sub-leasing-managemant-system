package com.sep490.slms2026.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.repository.UserPushTokenRepository;
import com.sep490.slms2026.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class PushNotificationService {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";
    private static final int EXPO_BATCH_SIZE = 100;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final RestTemplate restTemplate;
    // Spring Boot 4 không expose ObjectMapper Jackson 2 thành bean — tự new như OCR/PayOS.
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserPushTokenRepository userPushTokenRepository;
    private final UserRepository userRepository;

    public PushNotificationService(UserPushTokenRepository userPushTokenRepository,
                                   UserRepository userRepository) {
        this.userPushTokenRepository = userPushTokenRepository;
        this.userRepository = userRepository;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        this.restTemplate = new RestTemplate(factory);
    }

    public void sendPushNotification(String token, String title, String body, Map<String, Object> data) {
        if (token == null || token.isBlank()) {
            return;
        }
        sendPushNotifications(List.of(token), title, body, data);
    }

    /** Gửi batch Expo (tối đa 100 token/request). Best-effort — timeout/lỗi không ném ra caller. */
    public void sendPushNotifications(List<String> tokens, String title, String body, Map<String, Object> data) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        List<String> expoTokens = new ArrayList<>();
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String trimmed = token.trim();
            if (isExpoToken(trimmed)) {
                expoTokens.add(trimmed);
            } else {
                log.info("FCM/Other Push Token detected. Payload: title={}, body={}, data={}", title, body, data);
            }
        }
        for (int i = 0; i < expoTokens.size(); i += EXPO_BATCH_SIZE) {
            int end = Math.min(i + EXPO_BATCH_SIZE, expoTokens.size());
            sendExpoChunk(expoTokens.subList(i, end), title, body, data);
        }
    }

    private static boolean isExpoToken(String token) {
        return token.startsWith("ExponentPushToken") || token.startsWith("ExpoPushToken");
    }

    private void sendExpoChunk(List<String> tokens, String title, String body, Map<String, Object> data) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            List<Map<String, Object>> messages = new ArrayList<>(tokens.size());
            for (String token : tokens) {
                messages.add(expoMessage(token, title, body, data));
            }

            HttpEntity<List<Map<String, Object>>> request = new HttpEntity<>(messages, headers);
            String response = restTemplate.postForObject(EXPO_PUSH_URL, request, String.class);
            handleExpoResponse(tokens, response);
        } catch (Exception e) {
            log.warn("Expo push failed (best-effort, {} tokens): {}", tokens.size(), e.getMessage());
        }
    }

    private static Map<String, Object> expoMessage(String token, String title, String body, Map<String, Object> data) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("to", token);
        payload.put("sound", "default");
        payload.put("title", title);
        payload.put("body", body);
        if (data != null) {
            payload.put("data", data);
        }
        return payload;
    }

    private void handleExpoResponse(List<String> tokens, String response) {
        if (response == null || response.isBlank()) {
            log.warn("Expo push empty response for {} tokens", tokens.size());
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.get("data");
            if (data == null) {
                log.info("Expo push response (no data): {}", response);
                return;
            }
            if (data.isArray()) {
                int n = Math.min(tokens.size(), data.size());
                int ok = 0;
                for (int i = 0; i < n; i++) {
                    if (processTicket(tokens.get(i), data.get(i))) {
                        ok++;
                    }
                }
                log.info("Expo push chunk: {}/{} ok", ok, n);
            } else if (data.isObject()) {
                processTicket(tokens.get(0), data);
            }
        } catch (Exception e) {
            log.warn("Expo push parse failed: {} — raw={}", e.getMessage(), response);
        }
    }

    /** @return true if ticket status is ok */
    private boolean processTicket(String token, JsonNode ticket) {
        if (ticket == null || !ticket.isObject()) {
            return false;
        }
        String status = text(ticket, "status");
        if ("ok".equalsIgnoreCase(status)) {
            return true;
        }
        String errorCode = null;
        JsonNode details = ticket.get("details");
        if (details != null && details.hasNonNull("error")) {
            errorCode = details.get("error").asText();
        }
        String message = text(ticket, "message");
        if ("DeviceNotRegistered".equals(errorCode)
                || (message != null && message.contains("DeviceNotRegistered"))) {
            log.info("Dropping dead Expo token: {}", token);
            forgetDeadToken(token);
        } else {
            log.warn("Expo ticket error token={}: status={} code={} message={}",
                    token, status, errorCode, message);
        }
        return false;
    }

    private void forgetDeadToken(String token) {
        try {
            userPushTokenRepository.findByToken(token).ifPresent(row -> {
                UUID userId = row.getUserId();
                userPushTokenRepository.delete(row);
                userRepository.findById(userId).ifPresent(u -> clearLegacyPushToken(u, token));
            });
            userRepository.findByPushToken(token).ifPresent(u -> clearLegacyPushToken(u, token));
        } catch (Exception e) {
            log.warn("Failed to drop dead push token {}: {}", token, e.getMessage());
        }
    }

    private void clearLegacyPushToken(User user, String token) {
        if (token.equals(user.getPushToken())) {
            user.setPushToken(null);
            userRepository.save(user);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
