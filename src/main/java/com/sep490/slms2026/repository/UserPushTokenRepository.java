package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.UserPushToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPushTokenRepository extends JpaRepository<UserPushToken, Long> {

    List<UserPushToken> findByUserId(UUID userId);

    Optional<UserPushToken> findByToken(String token);

    void deleteByUserId(UUID userId);

    void deleteByUserIdAndToken(UUID userId, String token);

    void deleteByToken(String token);

    boolean existsByUserIdAndToken(UUID userId, String token);
}
