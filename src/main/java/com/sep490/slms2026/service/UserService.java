package com.sep490.slms2026.service;

import com.sep490.slms2026.entity.User;
import com.sep490.slms2026.enums.UserStatus;
import java.util.List;
import java.util.UUID;

public interface UserService {
    User createUser(User user);
    List<User> getAllUsers();
    User getUserById(UUID id);
    User updateUser(UUID id, User userDetails);
    User changeUserStatus(UUID id, UserStatus newStatus);
    public List<User> getAllManagers();
}