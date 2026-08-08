package com.sep490.slms2026.service;

import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.entity.UserPushToken;
import com.sep490.slms2026.repository.UserPushTokenRepository;
import com.sep490.slms2026.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Register / unregister Expo push tokens per device. Sends fan-out to every token of a user.
 */
@Service
@RequiredArgsConstructor
public class UserPushTokenService {

    private final UserPushTokenRepository userPushTokenRepository;
    private final UserRepository userRepository;
    private final PushNotificationService pushNotificationService;

    @Transactional
    public void register(User user, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        String trimmed = token.trim();
        userPushTokenRepository.findByToken(trimmed).ifPresentOrElse(existing -> {
            if (!existing.getUserId().equals(user.getId())) {
                // Token reused on different account — rebind
                existing.setUserId(user.getId());
                userPushTokenRepository.save(existing);
            } else {
                existing.setUpdatedAt(java.time.LocalDateTime.now());
                userPushTokenRepository.save(existing);
            }
        }, () -> userPushTokenRepository.save(UserPushToken.builder()
                .userId(user.getId())
                .token(trimmed)
                .build()));

        // Latest token mirrored on User for simple SELECTs / legacy readers
        user.setPushToken(trimmed);
        userRepository.save(user);
    }

    /**
     * @param token if blank/null → clear all devices for this user (logout)
     */
    @Transactional
    public void unregister(User user, String token) {
        if (token != null && !token.isBlank()) {
            String trimmed = token.trim();
            userPushTokenRepository.deleteByUserIdAndToken(user.getId(), trimmed);
            if (trimmed.equals(user.getPushToken())) {
                List<UserPushToken> remaining = userPushTokenRepository.findByUserId(user.getId());
                user.setPushToken(remaining.isEmpty() ? null : remaining.get(0).getToken());
                userRepository.save(user);
            }
            return;
        }
        userPushTokenRepository.deleteByUserId(user.getId());
        user.setPushToken(null);
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<String> tokensOf(UUID userId) {
        Set<String> tokens = new LinkedHashSet<>();
        for (UserPushToken row : userPushTokenRepository.findByUserId(userId)) {
            if (row.getToken() != null && !row.getToken().isBlank()) {
                tokens.add(row.getToken().trim());
            }
        }
        // Legacy fallback if migration/backfill missed this user
        userRepository.findById(userId).ifPresent(u -> {
            if (u.getPushToken() != null && !u.getPushToken().isBlank()) {
                tokens.add(u.getPushToken().trim());
            }
        });
        return List.copyOf(tokens);
    }

    public void sendToUser(UUID userId, String title, String body, java.util.Map<String, Object> data) {
        if (userId == null) {
            return;
        }
        for (String token : tokensOf(userId)) {
            pushNotificationService.sendPushNotification(token, title, body, data);
        }
    }
}
