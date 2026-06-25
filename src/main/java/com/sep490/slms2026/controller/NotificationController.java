package com.sep490.slms2026.controller;

import com.sep490.slms2026.entity.Notification;
import com.sep490.slms2026.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/host/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping
    public Page<Notification> getNotifications(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            Pageable pageable) {
        // TODO: Get actual user ID from SecurityContext
        Long mockUserId = 1L;
        if (unreadOnly) {
            return notificationRepository.findByUserIdAndReadFalse(mockUserId, pageable);
        }
        return notificationRepository.findByUserId(mockUserId, pageable);
    }

    @PutMapping("/{id}/read")
    public Notification markAsRead(@PathVariable Long id) {
        Notification notification = notificationRepository.findById(id).orElseThrow();
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    @PutMapping("/read-all")
    @Transactional
    public void markAllAsRead() {
        // TODO: Get actual user ID from SecurityContext
        Long mockUserId = 1L;
        notificationRepository.markAllAsRead(mockUserId);
    }
}
