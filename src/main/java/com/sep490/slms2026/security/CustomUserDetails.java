package com.sep490.slms2026.security;

import com.sep490.slms2026.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

public class CustomUserDetails implements UserDetails {

    private final User user; // Ngậm entity User của bồ vào đây

    public CustomUserDetails(User user) {
        this.user = user;
    }

    // 🚀 Trọng tâm ở đây: Giúp tầng Controller/Service lấy được ID Manager nhanh gọn
    public UUID getId() {
        return user.getId();
    }

    public String getFullName() {
        return user.getFullName();
    }

    public String getPhoneNumber() {
        return user.getPhoneNumber();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Giữ nguyên logic thêm Role của bồ, nhớ thêm "ROLE_" nếu cấu hình HasRole ở Controller yêu cầu nhé
        return Collections.singletonList(new SimpleGrantedAuthority(user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}