package com.sep490.slms2026.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class PushNotificationService {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final RestTemplate restTemplate;

    public PushNotificationService() {
        this.restTemplate = new RestTemplate();
    }

    public void sendPushNotification(String token, String title, String body, Map<String, Object> data) {
        if (token == null || token.isBlank()) {
            return;
        }

        if (token.startsWith("ExponentPushToken") || token.startsWith("ExpoPushToken")) {
            sendExpoPush(token, title, body, data);
        } else {
            // Placeholder for FCM / APNs or other push providers
            log.info("FCM/Other Push Token detected. Payload: title={}, body={}, data={}", title, body, data);
        }
    }

    private void sendExpoPush(String token, String title, String body, Map<String, Object> data) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> payload = new HashMap<>();
            payload.put("to", token);
            payload.put("title", title);
            payload.put("body", body);
            if (data != null) {
                payload.put("data", data);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
            String response = restTemplate.postForObject(EXPO_PUSH_URL, request, String.class);
            log.info("Expo Push Notification sent successfully to {}: {}", token, response);
        } catch (Exception e) {
            log.error("Failed to send Expo Push Notification to {}", token, e);
        }
    }
}
