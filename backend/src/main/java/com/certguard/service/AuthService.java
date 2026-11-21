package com.certguard.service;

import com.certguard.config.AppProperties;
import com.certguard.config.JwtService;
import com.certguard.dto.AuthDtos.AuthResponse;
import com.certguard.dto.AuthDtos.LoginRequest;
import com.certguard.dto.AuthDtos.RegisterRequest;
import com.certguard.dto.UserDto;
import com.certguard.entity.Role;
import com.certguard.entity.User;
import com.certguard.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AppProperties props;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.props = props;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User u = new User();
        u.setName(req.name().trim());
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.password()));
        // the first real account on a fresh install becomes the admin
        u.setRole(users.existsByRole(Role.ADMIN) ? Role.USER : Role.ADMIN);
        u.setDefaultReminderDays(ReminderDays.format(ReminderDays.parse(props.reminders().defaultDays())));
        users.save(u);
        return new AuthResponse(jwt.issue(u), UserDto.from(u));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User u = users.findByEmailIgnoreCase(req.email().trim())
                .filter(x -> encoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return new AuthResponse(jwt.issue(u), UserDto.from(u));
    }
}
