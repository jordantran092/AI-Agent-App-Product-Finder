package com.eecs3311.productfinder.application.llm;

import com.eecs3311.productfinder.application.ProductSpecSchemaRegistry;
import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.infrastructure.llm.DeepSeekClient;
import com.eecs3311.productfinder.infrastructure.llm.DeepSeekProperties;
import com.eecs3311.productfinder.infrastructure.llm.SharedLLMClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class DeepSeekClientTests {

    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    private static final String VALID_COMPLETION = """
            {
              "model": "deepseek-chat",
              "choices": [{
                "message": {"content": "{\\"answer\\":\\"ready\\"}"},
                "finish_reason": "stop"
              }],
              "usage": {"prompt_tokens": 10, "completion_tokens": 4, "total_tokens": 14}
            }
            """;
    private static final String ANSWER_SCHEMA = """
            {"type":"object","required":["answer"],"properties":{"answer":{"type":"string"}},"additionalProperties":false}
            """;

    @Test
    void mapsTypedRequestAndResponseAndValidatesStructuredOutput() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.deepseek.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(API_URL))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer unit-test-key"))
                .andExpect(jsonPath("$.model").value("deepseek-chat"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("Find a phone."))
                .andExpect(jsonPath("$.max_tokens").value(100))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess(VALID_COMPLETION, MediaType.APPLICATION_JSON));

        DeepSeekClient client = client(builder, properties("unit-test-key"));
        LLMRequest request = new LLMRequest(
                List.of(
                        new LLMMessage(LLMRole.SYSTEM, "Return JSON."),
                        new LLMMessage(LLMRole.USER, "Find a phone.")
                ),
                new LLMOptions(0.3, 100, LLMResponseFormat.JSON_OBJECT),
                new LLMOutputSchema("answer", ANSWER_SCHEMA)
        );

        LLMResponse response = client.complete(request);

        assertThat(response.content()).isEqualTo("{\"answer\":\"ready\"}");
        assertThat(response.model()).isEqualTo("deepseek-chat");
        assertThat(response.finishReason()).isEqualTo("stop");
        assertThat(response.usage()).isEqualTo(new LLMTokenUsage(10, 4, 14));
        server.verify();
    }

    @Test
    void rejectsStructuredContentThatDoesNotMatchItsSchema() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.deepseek.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(VALID_COMPLETION.replace("{\\\"answer\\\":\\\"ready\\\"}", "{\\\"wrong\\\":true}"),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(builder, properties("unit-test-key")).complete(
                new LLMRequest(
                        List.of(new LLMMessage(LLMRole.USER, "Respond.")),
                        new LLMOptions(null, null, LLMResponseFormat.JSON_OBJECT),
                        new LLMOutputSchema("answer", ANSWER_SCHEMA)
                )))
                .isInstanceOf(LLMResponseValidationException.class)
                .hasMessageContaining("does not conform");
        server.verify();
    }

    @Test
    void mapsRetryableHttpErrorsToTransientFailures() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.deepseek.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(API_URL)).andRespond(withStatus(TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client(builder, properties("unit-test-key")).complete(textRequest()))
                .isInstanceOf(TransientLLMException.class)
                .hasMessageContaining("429");
        server.verify();
    }

    @Test
    void retriesTemporaryProviderErrorsThroughTheDecorator() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.deepseek.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(API_URL)).andRespond(withStatus(TOO_MANY_REQUESTS));
        server.expect(requestTo(API_URL)).andRespond(withSuccess(VALID_COMPLETION, MediaType.APPLICATION_JSON));
        RetryingLLMClient retryingClient = new RetryingLLMClient(
                client(builder, properties("unit-test-key")),
                2,
                Duration.ZERO,
                Duration.ZERO
        );

        assertThat(retryingClient.complete(textRequest()).content()).isEqualTo("{\"answer\":\"ready\"}");
        server.verify();
    }

    @Test
    void doesNotClassifyAuthenticationErrorsAsTransient() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.deepseek.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(API_URL)).andRespond(withStatus(UNAUTHORIZED));

        assertThatThrownBy(() -> client(builder, properties("unit-test-key")).complete(textRequest()))
                .isInstanceOf(LLMProviderException.class)
                .hasMessageContaining("401");
        server.verify();
    }

    @Test
    void rejectsMalformedProviderJson() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.deepseek.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(API_URL)).andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(builder, properties("unit-test-key")).complete(textRequest()))
                .isInstanceOf(LLMProtocolException.class)
                .hasMessageContaining("malformed JSON");
        server.verify();
    }

    @Test
    void requiresAnApiKeyOnlyWhenMakingARequest() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.deepseek.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        assertThatThrownBy(() -> client(builder, properties(" ")).complete(textRequest()))
                .isInstanceOf(LLMConfigurationException.class)
                .hasMessageContaining("DEEPSEEK_API_KEY");
        server.verify();
    }

    @Test
    void generatedRequirementsPromptIncludesProductAndCategorySpecificSchemas() {
        ObjectMapper objectMapper = new ObjectMapper();
        ProductSpecSchemaRegistry registry = new ProductSpecSchemaRegistry(objectMapper);
        SchemaGuidedPromptBuilder prompts = new SchemaGuidedPromptBuilder(registry);

        LLMRequest request = prompts.requirementsExtraction(
                ProductCategory.SMARTPHONE,
                List.of(new LLMMessage(LLMRole.USER, "I need a phone with 128 GB storage."))
        );
        var schema = new LLMOutputSchema("requirements", registry.requirementsSchema(ProductCategory.SMARTPHONE));
        new StructuredOutputValidator(objectMapper).validate("""
                {
                  "category": "smartphone",
                  "maxPrice": null,
                  "mustHaves": ["128 GB storage"],
                  "niceToHaves": [],
                  "excludedBrands": [],
                  "condition": null,
                  "attributes": {"storageGb": 128}
                }
                """, schema);

        assertThat(request.options().responseFormat()).isEqualTo(LLMResponseFormat.JSON_OBJECT);
        assertThat(request.messages().getFirst().content()).contains("displaySizeInches", "storageGb");
        assertThat(request.outputSchema().jsonSchema()).contains("\"const\": \"smartphone\"");
    }

    private DeepSeekClient client(RestClient.Builder builder, DeepSeekProperties properties) {
        ObjectMapper objectMapper = new ObjectMapper();
        return new DeepSeekClient(
                new SharedLLMClient(builder.build(), properties),
                properties,
                objectMapper,
                new StructuredOutputValidator(objectMapper)
        );
    }

    private LLMRequest textRequest() {
        return new LLMRequest(
                List.of(new LLMMessage(LLMRole.USER, "Hello")),
                LLMOptions.defaults()
        );
    }

    private DeepSeekProperties properties(String apiKey) {
        return new DeepSeekProperties(
                apiKey,
                "deepseek-chat",
                URI.create("https://api.deepseek.com"),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                3,
                Duration.ofMillis(1),
                Duration.ofMillis(10)
        );
    }
}
