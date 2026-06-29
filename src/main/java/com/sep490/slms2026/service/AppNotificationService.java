package com.sep490.slms2026.service;

import com.sep490.slms2026.dto.response.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

public interface AppNotificationService {

    Page<NotificationResponse> listNotifications(UUID userId, boolean unreadOnly, Pageable pageable);

    Map<String, Long> unreadCount(UUID userId);

    void markAsRead(UUID userId, Long notificationId);

    void markAllAsRead(UUID userId);
}
