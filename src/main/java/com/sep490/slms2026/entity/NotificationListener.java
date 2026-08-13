package com.sep490.slms2026.entity;

import com.sep490.slms2026.config.ApplicationContextProvider;
import com.sep490.slms2026.service.SseNotificationService;
import jakarta.persistence.PostPersist;

public class NotificationListener {

    @PostPersist
    public void afterPersist(Notification notification) {
        if (notification != null && !notification.isRead() && notification.getUserId() != null) {
            SseNotificationService sseService = ApplicationContextProvider.getBean(SseNotificationService.class);
            if (sseService != null) {
                sseService.pushNotification(notification.getUserId(), notification.getId());
            }
        }
    }
}
