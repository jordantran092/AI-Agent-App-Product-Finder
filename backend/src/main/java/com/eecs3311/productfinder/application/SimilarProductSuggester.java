package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.application.llm.LLMClient;
import com.eecs3311.productfinder.application.llm.LLMMessage;
import com.eecs3311.productfinder.application.llm.LLMOptions;
import com.eecs3311.productfinder.application.llm.LLMOutputSchema;
import com.eecs3311.productfinder.application.llm.LLMProtocolException;
import com.eecs3311.productfinder.application.llm.LLMRequest;
import com.eecs3311.productfinder.application.llm.LLMResponseFormat;
import com.eecs3311.productfinder.application.llm.LLMRole;
import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.infrastructure.persistence.ProductRepository;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class SimilarProductSuggester {

    private static final int MAX_CANDIDATES = 8;
    private static final double MIN_SIMILARITY = 0.35;
    private static final String OUTPUT_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "additionalProperties": false,
              "required": ["productId"],
              "properties": {
                "productId": {"type": "integer", "minimum": 1}
              }
            }
            """;

    private final ProductRepository productRepository;
    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;

    public SimilarProductSuggester(ProductRepository productRepository, LLMClient llmClient,
                                   ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public Optional<Product> suggest(String productName) {
        Objects.requireNonNull(productName, "productName must not be null");
        List<Product> candidates = productRepository.findAll().stream()
                .map(product -> new Candidate(product, similarity(productName, product)))
                .filter(candidate -> candidate.similarity() >= MIN_SIMILARITY)
                .sorted(Comparator.comparingDouble(Candidate::similarity).reversed()
                        .thenComparing(candidate -> candidate.product().getModel()))
                .limit(MAX_CANDIDATES)
                .map(Candidate::product)
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        JsonNode response = parseObject(llmClient.complete(new LLMRequest(
                List.of(
                        new LLMMessage(LLMRole.SYSTEM, """
                                Choose the one catalog product whose brand and product name most closely match
                                the user's product-name query. The query may be a rough name or contain a typo.
                                Choose only from the provided candidates and return its exact productId as JSON.
                                """),
                        new LLMMessage(LLMRole.USER, writePrompt(productName, candidates))
                ),
                new LLMOptions(0.7, 200, LLMResponseFormat.JSON_OBJECT),
                new LLMOutputSchema("similar-product", OUTPUT_SCHEMA)
        )).content());
        JsonNode productId = response.get("productId");
        if (productId == null || !productId.canConvertToLong()) {
            throw new LLMProtocolException("Similar-product suggestion returned an invalid productId");
        }
        long selectedId = productId.longValue();
        Product selected = candidates.stream()
                .filter(candidate -> Objects.equals(candidate.getId(), selectedId))
                .findFirst()
                .orElseThrow(() -> new LLMProtocolException(
                        "Similar-product suggestion selected a product outside the candidate set"));
        return Optional.of(selected);
    }

    private String writePrompt(String productName, List<Product> candidates) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("productName", productName);
        input.put("candidates", candidates.stream().map(product -> {
            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("productId", product.getId());
            candidate.put("brand", product.getBrand());
            candidate.put("name", product.getName());
            candidate.put("category", product.getCategory());
            candidate.put("attributes", product.getAttributes());
            return candidate;
        }).toList());
        try {
            return objectMapper.writeValueAsString(input);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize similar-product candidates", exception);
        }
    }

    private JsonNode parseObject(String content) {
        try {
            JsonNode parsed = objectMapper.readTree(content);
            if (parsed == null || !parsed.isObject()) {
                throw new LLMProtocolException("Similar-product suggestion must be a JSON object");
            }
            return parsed;
        } catch (JacksonException exception) {
            throw new LLMProtocolException("Similar-product suggestion returned malformed JSON", exception);
        }
    }

    private double similarity(String productName, Product product) {
        List<String> queryTokens = tokens(productName);
        if (queryTokens.isEmpty()) {
            return 0;
        }
        List<String> candidateTokens = tokens(product.getBrand() + " " + product.getName());
        double tokenSimilarity = queryTokens.stream()
                .mapToDouble(queryToken -> candidateTokens.stream()
                        .mapToDouble(candidateToken -> editSimilarity(queryToken, candidateToken))
                        .max()
                        .orElse(0))
                .average()
                .orElse(0);
        double phraseSimilarity = editSimilarity(normalize(productName),
                normalize(product.getBrand() + " " + product.getName()));
        return Math.max(tokenSimilarity, phraseSimilarity);
    }

    private double editSimilarity(String first, String second) {
        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];
        for (int j = 0; j <= second.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= first.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= second.length(); j++) {
                int substitutionCost = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + substitutionCost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return 1.0 - (double) previous[second.length()] / Math.max(first.length(), second.length());
    }

    private static String normalize(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static List<String> tokens(String value) {
        return java.util.Arrays.stream(value.toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9]+"))
                .filter(token -> !token.isEmpty())
                .toList();
    }

    private record Candidate(Product product, double similarity) {
    }
}
