package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.application.llm.LLMClient;
import com.eecs3311.productfinder.application.llm.LLMMessage;
import com.eecs3311.productfinder.application.llm.LLMProtocolException;
import com.eecs3311.productfinder.application.llm.LLMRequest;
import com.eecs3311.productfinder.application.llm.LLMRole;
import com.eecs3311.productfinder.application.llm.SchemaGuidedPromptBuilder;
import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.domain.catalog.ProductCondition;
import com.eecs3311.productfinder.domain.chat.ChatMessage;
import com.eecs3311.productfinder.domain.chat.MessageRole;
import com.eecs3311.productfinder.domain.chat.Requirements;
import com.eecs3311.productfinder.domain.chat.RequirementsValidationException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class RequirementsExtractor {

    private final LLMClient llmClient;
    private final SchemaGuidedPromptBuilder promptBuilder;
    private final ProductSpecSchemaRegistry schemaRegistry;
    private final ObjectMapper objectMapper;

    public RequirementsExtractor(
            LLMClient llmClient,
            SchemaGuidedPromptBuilder promptBuilder,
            ProductSpecSchemaRegistry schemaRegistry,
            ObjectMapper objectMapper
    ) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.schemaRegistry = schemaRegistry;
        this.objectMapper = objectMapper;
    }

    public RequirementsExtractionResult extract(List<ChatMessage> history, ProfileSettings profile) {
        Objects.requireNonNull(history, "history must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        List<LLMMessage> conversation = history.stream()
                .map(this::toLLMMessage)
                .toList();
        LLMRequest request = promptBuilder.requirementsExtraction(conversation, profileContext(profile));
        String content = llmClient.complete(request).content();
        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (JacksonException exception) {
            throw new LLMProtocolException("Requirements extractor received malformed structured JSON", exception);
        }
        if (root == null || !root.isObject()) {
            throw new LLMProtocolException("Requirements extractor response must be a JSON object");
        }
        String status = stringField(root, "status");
        if ("FOLLOW_UP".equals(status)) {
            return RequirementsExtractionResult.followUp(stringField(root, "followUpQuestion"));
        }
        if (!"READY".equals(status)) {
            throw new LLMProtocolException("Requirements extractor returned an unsupported status");
        }
        return RequirementsExtractionResult.ready(toRequirements(root));
    }

    private Requirements toRequirements(JsonNode root) {
        ProductCategory category;
        try {
            category = ProductCategory.fromSchemaName(stringField(root, "category"));
        } catch (IllegalArgumentException exception) {
            throw new LLMProtocolException("Requirements extractor returned an unknown product category", exception);
        }
        JsonNode maxPriceNode = root.get("maxPrice");
        BigDecimal maxPrice = maxPriceNode == null || maxPriceNode.isNull() ? null : maxPriceNode.decimalValue();
        JsonNode conditionNode = root.get("condition");
        ProductCondition condition = conditionNode == null || conditionNode.isNull()
                ? null
                : ProductCondition.valueOf(conditionNode.stringValue());
        List<String> mustHaves = stringArray(root, "mustHaves");
        List<String> niceToHaves = stringArray(root, "niceToHaves");
        List<String> excludedBrands = stringArray(root, "excludedBrands");
        JsonNode attributesNode = root.get("attributes");
        if (attributesNode == null || !attributesNode.isObject()) {
            throw new LLMProtocolException("Requirements extractor omitted category-specific attributes");
        }
        Map<String, Object> attributes;
        try {
            attributes = objectMapper.convertValue(attributesNode, new TypeReference<Map<String, Object>>() { });
        } catch (IllegalArgumentException exception) {
            throw new LLMProtocolException("Requirements extractor returned invalid category-specific attributes",
                    exception);
        }
        List<String> violations = schemaRegistry.validateRequirementAttributes(category, attributes);
        if (!violations.isEmpty()) {
            throw new RequirementsValidationException(violations);
        }
        return new Requirements(category, maxPrice, mustHaves, niceToHaves, excludedBrands, condition, attributes);
    }

    private LLMMessage toLLMMessage(ChatMessage message) {
        LLMRole role = message.role() == MessageRole.USER ? LLMRole.USER : LLMRole.ASSISTANT;
        return new LLMMessage(role, message.content());
    }

    private String stringField(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isString() || value.stringValue().isBlank()) {
            throw new LLMProtocolException("Requirements extractor response is missing '" + field + "'");
        }
        return value.stringValue();
    }

    private List<String> stringArray(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isArray()) {
            throw new LLMProtocolException("Requirements extractor response is missing '" + field + "'");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode element : value) {
            if (!element.isString()) {
                throw new LLMProtocolException("Requirements extractor returned a non-string value in '" + field + "'");
            }
            values.add(element.stringValue());
        }
        return List.copyOf(values);
    }

    private String profileContext(ProfileSettings profile) {
        List<String> settings = new ArrayList<>();
        if (profile.budget() != null) {
            settings.add("budget " + profile.budget());
        }
        if (!profile.excludedBrands().isEmpty()) {
            settings.add("excluded brands " + profile.excludedBrands().stream().sorted().toList());
        }
        Set<ProductCondition> conditions = profile.conditions();
        if (!conditions.isEmpty()) {
            settings.add("allowed conditions " + conditions.stream().sorted().toList());
        }
        return String.join(", ", settings);
    }
}
