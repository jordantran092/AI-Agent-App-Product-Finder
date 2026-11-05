package com.eecs3311.productfinder.application.llm;

import com.eecs3311.productfinder.infrastructure.llm.DeepSeekProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

@Component
@Primary
public class RetryingLLMClient implements LLMClient {

    private final LLMClient delegate;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;

    @Autowired
    public RetryingLLMClient(
            @Qualifier("deepSeekClient") LLMClient delegate,
            DeepSeekProperties properties
    ) {
        this(delegate, properties.maxAttempts(), properties.initialBackoff(), properties.maxBackoff());
    }

    RetryingLLMClient(LLMClient delegate, int maxAttempts, Duration initialBackoff, Duration maxBackoff) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        if (maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and 5");
        }
        this.maxAttempts = maxAttempts;
        this.initialBackoff = Objects.requireNonNull(initialBackoff, "initialBackoff must not be null");
        this.maxBackoff = Objects.requireNonNull(maxBackoff, "maxBackoff must not be null");
        if (initialBackoff.isNegative() || maxBackoff.isNegative() || initialBackoff.compareTo(maxBackoff) > 0) {
            throw new IllegalArgumentException("backoff durations must be non-negative and initialBackoff <= maxBackoff");
        }
    }

    @Override
    public LLMResponse complete(LLMRequest request) {
        TransientLLMException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return delegate.complete(request);
            } catch (TransientLLMException exception) {
                lastFailure = exception;
                if (attempt < maxAttempts) {
                    pause(backoffFor(attempt), exception);
                }
            }
        }
        throw new LLMUnavailableException("The language model is temporarily unavailable", lastFailure);
    }

    private Duration backoffFor(int failedAttempt) {
        Duration backoff = initialBackoff;
        for (int i = 1; i < failedAttempt && backoff.compareTo(maxBackoff) < 0; i++) {
            try {
                backoff = backoff.multipliedBy(2);
            } catch (ArithmeticException exception) {
                return maxBackoff;
            }
        }
        return backoff.compareTo(maxBackoff) > 0 ? maxBackoff : backoff;
    }

    private void pause(Duration duration, TransientLLMException failure) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LLMUnavailableException unavailable =
                    new LLMUnavailableException("Interrupted while waiting to retry the language model", exception);
            unavailable.addSuppressed(failure);
            throw unavailable;
        }
    }
}
