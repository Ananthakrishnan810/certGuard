package com.certguard.config;

import com.certguard.entity.Role;

/** What the JWT filter puts in the SecurityContext. */
public record AppUserPrincipal(Long id, String email, Role role) {
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
