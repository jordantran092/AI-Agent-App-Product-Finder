package com.eecs3311.productfinder.infrastructure.llm;

import com.eecs3311.productfinder.application.llm.LLMOptions;
import com.eecs3311.productfinder.application.llm.LLMRequest;
import com.eecs3311.productfinder.application.llm.LLMMessage;
import com.eecs3311.productfinder.application.llm.LLMRole;
import com.eecs3311.productfinder.application.llm.StructuredOutputValidator;
import com.eecs3311.productfinder.application.llm.TransientLLMException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepSeekTimeoutTests {

    @Test
    void readTimeoutBecomesRetryableTransientFailure() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            DeepSeekProperties properties = new DeepSeekProperties(
                    "unit-test-key",
                    "deepseek-chat",
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    Duration.ofSeconds(1),
                    Duration.ofMillis(100),
                    1,
                    Duration.ZERO,
                    Duration.ZERO
            );
            RestClient restClient = new LLMConfiguration().deepSeekRestClient(properties);
            ObjectMapper objectMapper = new ObjectMapper();
            DeepSeekClient client = new DeepSeekClient(
                    new SharedLLMClient(restClient, properties),
                    properties,
                    objectMapper,
                    new StructuredOutputValidator(objectMapper)
            );

            assertThatThrownBy(() -> client.complete(new LLMRequest(
                    List.of(new LLMMessage(LLMRole.USER, "Hello")),
                    LLMOptions.defaults()
            ))).isInstanceOf(TransientLLMException.class);
        } finally {
            server.stop(0);
        }
    }
}
