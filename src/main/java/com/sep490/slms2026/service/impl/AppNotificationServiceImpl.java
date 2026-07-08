package com.sep490.slms2026.service.impl;

import com.sep490.slms2026.dto.response.NotificationResponse;
import com.sep490.slms2026.entity.Notification;
import com.sep490.slms2026.exception.ResourceNotFoundException;
import com.sep490.slms2026.repository.NotificationRepository;
import com.sep490.slms2026.service.AppNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppNotificationServiceImpl implements AppNotificationService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> listNotifications(UUID userId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByUserIdAndReadFalse(userId, pageable)
                : notificationRepository.findByUserId(userId, pageable);
        return page.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> unreadCount(UUID userId) {
        return Map.of("count", notificationRepository.countByUserIdAndReadFalse(userId));
    }

    @Override
    @Transactional
    public void markAsRead(UUID userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy thông báo ID=" + notificationId));
        if (!notification.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Không tìm thấy thông báo ID=" + notificationId);
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.markAllAsRead(userId);
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .content(notification.getContent())
                .type(notification.getType())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt() != null
                        ? notification.getCreatedAt().format(ISO) : null)
                .build();
    }
}
