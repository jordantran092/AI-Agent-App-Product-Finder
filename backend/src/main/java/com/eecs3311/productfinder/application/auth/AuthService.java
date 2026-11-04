package com.eecs3311.productfinder.application.auth;

import com.eecs3311.productfinder.domain.user.User;
import com.eecs3311.productfinder.infrastructure.persistence.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

@Service
public class AuthService {

    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public User register(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        validatePassword(password);
        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw AuthException.conflict("Username is already registered");
        }

        User user = new User(normalizedUsername, passwordEncoder.encode(password));
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw AuthException.conflict("Username is already registered");
        }
    }

    public Authentication authenticate(String username, String password) {
        String normalizedUsername = normalizeUsername(username);
        validatePasswordByteLength(password);
        try {
            return authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(normalizedUsername, password));
        } catch (BadCredentialsException exception) {
            throw AuthException.unauthorized("Invalid username or password");
        }
    }

    private String normalizeUsername(String username) {
        Objects.requireNonNull(username, "username must not be null");
        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        if (!normalizedUsername.matches("[a-z0-9._-]{3,80}")) {
            throw AuthException.badRequest("Username must contain 3 to 80 letters, numbers, dots, underscores or hyphens");
        }
        return normalizedUsername;
    }

    private void validatePassword(String password) {
        Objects.requireNonNull(password, "password must not be null");
        if (password.length() < 12) {
            throw AuthException.badRequest("Password must be at least 12 characters and no more than 72 UTF-8 bytes");
        }
        validatePasswordByteLength(password);
    }

    private void validatePasswordByteLength(String password) {
        Objects.requireNonNull(password, "password must not be null");
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            throw AuthException.badRequest("Password must be no more than 72 UTF-8 bytes");
        }
    }
}
