package com.certguard.service;

import com.certguard.config.AppUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    public AppUserPrincipal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal p)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Not logged in");
        }
        return p;
    }

    public Long id() {
        return get().id();
    }
}
