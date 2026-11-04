package com.eecs3311.productfinder.application.llm;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryingLLMClientTests {

    private final LLMRequest request = new LLMRequest(
            List.of(new LLMMessage(LLMRole.USER, "Hello")),
            LLMOptions.defaults()
    );

    @Test
    void retriesTransientFailuresAndReturnsTheSuccessfulResponse() {
        AtomicInteger calls = new AtomicInteger();
        LLMClient delegate = ignored -> {
            if (calls.incrementAndGet() < 3) {
                throw new TransientLLMException("temporary", null);
            }
            return new LLMResponse("done", "test-model", "stop", null);
        };
        RetryingLLMClient client = retrying(delegate, 3);

        assertThat(client.complete(request).content()).isEqualTo("done");
        assertThat(calls).hasValue(3);
    }

    @Test
    void reportsUnavailableAfterExhaustingBoundedAttempts() {
        AtomicInteger calls = new AtomicInteger();
        LLMClient delegate = ignored -> {
            calls.incrementAndGet();
            throw new TransientLLMException("temporary", null);
        };

        assertThatThrownBy(() -> retrying(delegate, 3).complete(request))
                .isInstanceOf(LLMUnavailableException.class)
                .hasCauseInstanceOf(TransientLLMException.class);
        assertThat(calls).hasValue(3);
    }

    @Test
    void doesNotRetryPermanentErrors() {
        AtomicInteger calls = new AtomicInteger();
        LLMClient delegate = ignored -> {
            calls.incrementAndGet();
            throw new LLMProviderException("invalid credentials");
        };

        assertThatThrownBy(() -> retrying(delegate, 3).complete(request))
                .isInstanceOf(LLMProviderException.class)
                .hasMessage("invalid credentials");
        assertThat(calls).hasValue(1);
    }

    private RetryingLLMClient retrying(LLMClient delegate, int attempts) {
        return new RetryingLLMClient(delegate, attempts, Duration.ZERO, Duration.ZERO);
    }
}
