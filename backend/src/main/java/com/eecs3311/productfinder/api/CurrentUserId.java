package com.eecs3311.productfinder.api;

import com.eecs3311.productfinder.domain.user.User;
import com.eecs3311.productfinder.infrastructure.persistence.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserId {

    private final UserRepository userRepository;

    public CurrentUserId(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public long resolve(UserDetails principal) {
        User user = userRepository.findByUsernameIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new IllegalStateException("Authenticated user no longer exists"));
        return user.getId();
    }
}
