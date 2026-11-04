package com.eecs3311.productfinder.infrastructure.llm;

import com.eecs3311.productfinder.application.llm.LLMClient;
import com.eecs3311.productfinder.application.llm.LLMMessage;
import com.eecs3311.productfinder.application.llm.LLMOptions;
import com.eecs3311.productfinder.application.llm.LLMProtocolException;
import com.eecs3311.productfinder.application.llm.LLMRequest;
import com.eecs3311.productfinder.application.llm.LLMResponse;
import com.eecs3311.productfinder.application.llm.LLMResponseFormat;
import com.eecs3311.productfinder.application.llm.LLMRole;
import com.eecs3311.productfinder.application.llm.LLMTokenUsage;
import com.eecs3311.productfinder.application.llm.StructuredOutputValidator;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DeepSeekClient implements LLMClient {

    private final SharedLLMClient sharedClient;
    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;
    private final StructuredOutputValidator outputValidator;

    public DeepSeekClient(
            SharedLLMClient sharedClient,
            DeepSeekProperties properties,
            ObjectMapper objectMapper,
            StructuredOutputValidator outputValidator
    ) {
        this.sharedClient = sharedClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.outputValidator = outputValidator;
    }

    @Override
    public LLMResponse complete(LLMRequest request) {
        Map<String, Object> providerRequest = toProviderRequest(request);
        String responseBody = sharedClient.postJson(providerRequest);
        LLMResponse response = fromProviderResponse(responseBody);
        if (request.outputSchema() != null) {
            outputValidator.validate(response.content(), request.outputSchema());
        }
        return response;
    }

    private Map<String, Object> toProviderRequest(LLMRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        List<Map<String, String>> messages = new ArrayList<>();
        for (LLMMessage message : request.messages()) {
            messages.add(Map.of(
                    "role", message.role().name().toLowerCase(Locale.ROOT),
                    "content", message.content()
            ));
        }
        body.put("messages", messages);
        LLMOptions options = request.options();
        if (options.temperature() != null) {
            body.put("temperature", options.temperature());
        }
        if (options.maxTokens() != null) {
            body.put("max_tokens", options.maxTokens());
        }
        if (options.responseFormat() == LLMResponseFormat.JSON_OBJECT || request.outputSchema() != null) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        return body;
    }

    private LLMResponse fromProviderResponse(String responseBody) {
        final JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (JacksonException exception) {
            throw new LLMProtocolException("DeepSeek returned malformed JSON", exception);
        }

        if (root == null || !root.isObject()) {
            throw new LLMProtocolException("DeepSeek response must be a JSON object");
        }
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new LLMProtocolException("DeepSeek response did not contain a completion choice");
        }
        JsonNode choice = choices.get(0);
        if (choice == null || !choice.isObject()) {
            throw new LLMProtocolException("DeepSeek response contains an invalid completion choice");
        }
        JsonNode message = choice.get("message");
        if (message != null && !message.isObject()) {
            throw new LLMProtocolException("DeepSeek response contains an invalid completion message");
        }
        String content = textValue(message == null ? null : message.get("content"));
        String model = textValue(root.get("model"));
        String finishReason = textValue(choice.get("finish_reason"));
        if (content == null || content.isBlank() || model == null || finishReason == null) {
            throw new LLMProtocolException("DeepSeek response is missing completion content or metadata");
        }
        return new LLMResponse(content, model, finishReason, readUsage(root.get("usage")));
    }

    private LLMTokenUsage readUsage(JsonNode usage) {
        if (usage == null || usage.isNull()) {
            return null;
        }
        JsonNode prompt = usage.get("prompt_tokens");
        JsonNode completion = usage.get("completion_tokens");
        JsonNode total = usage.get("total_tokens");
        if (!usage.isObject() || prompt == null || completion == null || total == null
                || !prompt.canConvertToInt() || !completion.canConvertToInt() || !total.canConvertToInt()) {
            throw new LLMProtocolException("DeepSeek response contains invalid token usage metadata");
        }
        try {
            return new LLMTokenUsage(prompt.intValue(), completion.intValue(), total.intValue());
        } catch (IllegalArgumentException exception) {
            throw new LLMProtocolException("DeepSeek response contains negative token usage metadata", exception);
        }
    }

    private String textValue(JsonNode value) {
        return value != null && value.isString() ? value.stringValue() : null;
    }
}
