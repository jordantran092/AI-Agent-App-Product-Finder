package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.application.llm.LLMClient;
import com.eecs3311.productfinder.application.llm.LLMMessage;
import com.eecs3311.productfinder.application.llm.LLMOutputSchema;
import com.eecs3311.productfinder.application.llm.LLMProtocolException;
import com.eecs3311.productfinder.application.llm.LLMRequest;
import com.eecs3311.productfinder.application.llm.LLMResponseFormat;
import com.eecs3311.productfinder.application.llm.LLMRole;
import com.eecs3311.productfinder.application.llm.SchemaGuidedPromptBuilder;
import com.eecs3311.productfinder.domain.catalog.Listing;
import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.chat.Requirements;
import com.eecs3311.productfinder.infrastructure.persistence.ProductRepository;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ProductMatcher {

    private static final String MATCH_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "additionalProperties": false,
              "required": ["productId", "explanation"],
              "properties": {
                "productId": {"type": "integer", "minimum": 1},
                "explanation": {"type": "string", "minLength": 1}
              }
            }
            """;

    private final ProductRepository productRepository;
    private final PriceComparisonService priceComparisonService;
    private final ProductSpecSchemaRegistry schemaRegistry;
    private final SchemaGuidedPromptBuilder promptBuilder;
    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;

    public ProductMatcher(
            ProductRepository productRepository,
            PriceComparisonService priceComparisonService,
            ProductSpecSchemaRegistry schemaRegistry,
            SchemaGuidedPromptBuilder promptBuilder,
            LLMClient llmClient,
            ObjectMapper objectMapper
    ) {
        this.productRepository = productRepository;
        this.priceComparisonService = priceComparisonService;
        this.schemaRegistry = schemaRegistry;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public ProductMatchResult match(Requirements requirements, ProfileSettings profile) {
        Objects.requireNonNull(requirements, "requirements must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        List<Candidate> eligible = new ArrayList<>();
        for (Product product : productRepository.findWithListingsByCategory(requirements.category())) {
            schemaRegistry.validateOrThrow(product.getCategory(), product.getAttributes());
            if (!matchesAttributes(product.getAttributes(), requirements.attributes())) {
                continue;
            }
            ProfileSettings effectiveProfile = withRequirementBrandExclusions(profile, requirements);
            priceComparisonService.bestListing(
                            product,
                            effectiveProfile,
                            requirements.maxPrice(),
                            requirements.condition())
                    .ifPresent(listing -> eligible.add(new Candidate(product, listing)));
        }
        if (eligible.isEmpty()) {
            return ProductMatchResult.noMatch(
                    "No products meet the selected category, specifications, budget, brand, and condition filters.",
                    constraintsToRelax(requirements, profile)
            );
        }

        LLMRequest request = rankingRequest(requirements, eligible);
        JsonNode response = parseObject(llmClient.complete(request).content());
        JsonNode productIdNode = response.get("productId");
        JsonNode explanationNode = response.get("explanation");
        if (productIdNode == null || !productIdNode.canConvertToLong()
                || explanationNode == null || !explanationNode.isString()
                || explanationNode.stringValue().isBlank()) {
            throw new LLMProtocolException("Product matcher returned an invalid recommendation");
        }
        long productId = productIdNode.longValue();
        Candidate selected = eligible.stream()
                .filter(candidate -> Objects.equals(candidate.product().getId(), productId))
                .findFirst()
                .orElseThrow(() -> new LLMProtocolException(
                        "Product matcher selected a product outside the eligible candidate set"));
        return ProductMatchResult.matched(
                new ProductMatch(selected.product(), selected.listing(), explanationNode.stringValue())
        );
    }

    private LLMRequest rankingRequest(Requirements requirements, List<Candidate> candidates) {
        List<Map<String, Object>> candidateData = candidates.stream()
                .map(this::candidateData)
                .toList();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("requirements", requirements);
        input.put("eligibleCandidates", candidateData);
        String prompt = writeJson(input, "Unable to serialize product matching input");
        LLMOutputSchema outputSchema = new LLMOutputSchema("product-match", MATCH_SCHEMA);
        return promptBuilder.build(
                requirements.category(),
                """
                Rank only the eligible candidates provided by the application against the user's requirements.
                Choose the best fit non-deterministically based on the structured specifications and explain relevant trade-offs.
                Do not invent specifications, prices, or candidates. Do not apply a fixed weighted score or tie-break formula.
                Return the selected product's exact productId and a concise explanation.
                """,
                List.of(new LLMMessage(LLMRole.USER, prompt)),
                outputSchema
        );
    }

    private Map<String, Object> candidateData(Candidate candidate) {
        Product product = candidate.product();
        Listing listing = candidate.listing();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("productId", product.getId());
        data.put("name", product.getName());
        data.put("brand", product.getBrand());
        data.put("model", product.getModel());
        data.put("description", product.getDescription());
        data.put("attributes", product.getAttributes());
        data.put("bestEligibleOffer", Map.of(
                "retailer", listing.getRetailer().getName(),
                "price", listing.getPrice(),
                "condition", listing.getCondition().name()
        ));
        return data;
    }

    private JsonNode parseObject(String json) {
        try {
            JsonNode parsed = objectMapper.readTree(json);
            if (parsed == null || !parsed.isObject()) {
                throw new LLMProtocolException("Product matcher response must be a JSON object");
            }
            return parsed;
        } catch (JacksonException exception) {
            throw new LLMProtocolException("Product matcher returned malformed JSON", exception);
        }
    }

    private ProfileSettings withRequirementBrandExclusions(ProfileSettings profile, Requirements requirements) {
        Set<String> excluded = new LinkedHashSet<>(profile.excludedBrands());
        requirements.excludedBrands().stream()
                .map(brand -> brand.toLowerCase(Locale.ROOT).trim())
                .forEach(excluded::add);
        return new ProfileSettings(profile.budget(), excluded, profile.conditions());
    }

    private boolean matchesAttributes(Map<String, Object> productAttributes, Map<String, Object> requiredAttributes) {
        return requiredAttributes.entrySet().stream()
                .allMatch(entry -> matchesValue(productAttributes.get(entry.getKey()), entry.getValue()));
    }

    private boolean matchesValue(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return Objects.equals(actual, expected);
        }
        if (actual instanceof Number actualNumber && expected instanceof Number expectedNumber) {
            return new BigDecimal(actualNumber.toString()).compareTo(new BigDecimal(expectedNumber.toString())) >= 0;
        }
        if (actual instanceof Collection<?> actualValues && expected instanceof Collection<?> expectedValues) {
            return expectedValues.stream().allMatch(expectedValue ->
                    actualValues.stream().anyMatch(actualValue -> matchesValue(actualValue, expectedValue)));
        }
        if (actual instanceof Collection<?> actualValues) {
            return actualValues.stream().anyMatch(actualValue -> matchesValue(actualValue, expected));
        }
        if (actual instanceof String actualText && expected instanceof String expectedText) {
            return actualText.equalsIgnoreCase(expectedText);
        }
        return Objects.equals(actual, expected);
    }

    private List<String> constraintsToRelax(Requirements requirements, ProfileSettings profile) {
        List<String> constraints = new ArrayList<>();
        constraints.add("product specifications");
        if (requirements.maxPrice() != null || profile.budget() != null) {
            constraints.add("maximum price or profile budget");
        }
        if (!requirements.excludedBrands().isEmpty() || !profile.excludedBrands().isEmpty()) {
            constraints.add("excluded brands");
        }
        if (requirements.condition() != null || !profile.conditions().isEmpty()) {
            constraints.add("product condition");
        }
        return List.copyOf(constraints);
    }

    private String writeJson(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException(errorMessage, exception);
        }
    }

    private record Candidate(Product product, Listing listing) {
    }
}
