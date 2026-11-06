package com.eecs3311.productfinder.cli;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@ConditionalOnProperty(name = "app.cli.enabled", havingValue = "true")
class CliAuthenticationConfiguration {

    @Bean
    AuthenticationManager cliAuthenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        return authentication -> {
            UserDetails user;
            try {
                user = userDetailsService.loadUserByUsername(authentication.getName());
            } catch (UsernameNotFoundException exception) {
                throw new BadCredentialsException("Invalid username or password", exception);
            }
            Object credentials = authentication.getCredentials();
            if (!(credentials instanceof String password)
                    || !passwordEncoder.matches(password, user.getPassword())) {
                throw new BadCredentialsException("Invalid username or password");
            }
            return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        };
    }
}
