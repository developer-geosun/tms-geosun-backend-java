package com.geosun.tms.auth.security;

import com.geosun.tms.auth.domain.user.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Принципал після успішної перевірки access JWT (для SecurityContext).
 */
public class UserPrincipal implements UserDetails {

    public static final String CLAIM_SESSION_ID = "sessionId";

    private final String userId;
    private final String email;
    private final Role role;
    private final String refreshSessionId;

    public UserPrincipal(String userId, String email, Role role, String refreshSessionId) {
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.refreshSessionId = refreshSessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getRefreshSessionId() {
        return refreshSessionId;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
