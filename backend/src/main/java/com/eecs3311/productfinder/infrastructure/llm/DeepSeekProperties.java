package com.eecs3311.productfinder.infrastructure.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "product-finder.deepseek")
public record DeepSeekProperties(
        String apiKey,
        String model,
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff
) {

    public DeepSeekProperties {
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        Objects.requireNonNull(connectTimeout, "connectTimeout must not be null");
        Objects.requireNonNull(readTimeout, "readTimeout must not be null");
        Objects.requireNonNull(initialBackoff, "initialBackoff must not be null");
        Objects.requireNonNull(maxBackoff, "maxBackoff must not be null");
        if (model.isBlank() || baseUrl.getHost() == null
                || !("https".equalsIgnoreCase(baseUrl.getScheme()) || "http".equalsIgnoreCase(baseUrl.getScheme()))) {
            throw new IllegalArgumentException("DeepSeek model and HTTP(S) base URL must be configured");
        }
        if (connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("DeepSeek HTTP timeouts must be positive");
        }
        if (maxAttempts < 1 || maxAttempts > 5
                || initialBackoff.isNegative() || maxBackoff.isNegative()
                || initialBackoff.compareTo(maxBackoff) > 0) {
            throw new IllegalArgumentException("DeepSeek retry settings are invalid");
        }
    }
}
