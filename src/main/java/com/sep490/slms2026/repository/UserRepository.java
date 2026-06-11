package com.sep490.slms2026.repository;

import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.Role;
import com.sep490.slms2026.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByPhoneNumber(String phoneNumber);
    List<User> findByStatus(UserStatus status);
    List<User> findByRole(Role role);
}