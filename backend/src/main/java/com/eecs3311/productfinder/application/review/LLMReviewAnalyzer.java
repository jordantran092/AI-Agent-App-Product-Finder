package com.eecs3311.productfinder.application.review;

import com.eecs3311.productfinder.application.llm.LLMClient;
import com.eecs3311.productfinder.application.llm.LLMMessage;
import com.eecs3311.productfinder.application.llm.LLMOptions;
import com.eecs3311.productfinder.application.llm.LLMOutputSchema;
import com.eecs3311.productfinder.application.llm.LLMProtocolException;
import com.eecs3311.productfinder.application.llm.LLMRequest;
import com.eecs3311.productfinder.application.llm.LLMResponseFormat;
import com.eecs3311.productfinder.application.llm.LLMRole;
import com.eecs3311.productfinder.domain.catalog.Listing;
import com.eecs3311.productfinder.domain.catalog.Product;
import com.eecs3311.productfinder.domain.catalog.Review;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class LLMReviewAnalyzer {

    private static final String TRUST_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "additionalProperties": false,
              "required": ["trustPhrase"],
              "properties": {
                "trustPhrase": {"type": "string", "minLength": 1, "maxLength": 200}
              }
            }
            """;
    private static final String SUMMARY_SCHEMA = """
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "additionalProperties": false,
              "required": ["summary"],
              "properties": {
                "summary": {"type": "string", "minLength": 1, "maxLength": 8000}
              }
            }
            """;

    private final LLMClient llmClient;
    private final ObjectMapper objectMapper;

    public LLMReviewAnalyzer(LLMClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public ReviewTrustResult analyzeTrust(List<Review> reviews) {
        Objects.requireNonNull(reviews, "reviews must not be null");
        if (reviews.isEmpty()) {
            return new ReviewTrustResult("No reviews to check");
        }
        List<Map<String, Object>> reviewData = reviews.stream().map(this::reviewData).toList();
        JsonNode response = complete("review-trust", TRUST_SCHEMA, """
                Assess the overall trustworthiness of the supplied product reviews.
                Choose a concise, user-facing phrase that reflects your assessment, such as
                "Trustworthy Reviews" or "Highly Suspicious Reviews". Decide based on the provided
                review text and ratings; do not invent review evidence or provide a numeric score.
                """, reviewData);
        return new ReviewTrustResult(requiredString(response, "trustPhrase"));
    }

    public ReviewSummaryResult summarize(Product product, List<Review> reviews) {
        Objects.requireNonNull(product, "product must not be null");
        Objects.requireNonNull(reviews, "reviews must not be null");
        List<Map<String, Object>> pricedListings = product.getListings().stream()
                .filter(listing -> listing.getPrice() != null)
                .map(this::listingData)
                .toList();
        List<Map<String, Object>> reviewData = reviews.stream().map(this::reviewData).toList();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("product", productData(product));
        input.put("pricedRetailerOffers", pricedListings);
        input.put("reviews", reviewData);
        JsonNode response = complete("product-review-summary", SUMMARY_SCHEMA, """
                Summarize the supplied product information and available retailer reviews for a shopper.
                Be concise, distinguish product specifications from reviewer opinions, and do not invent
                facts. Retailers without priced offers have already been excluded from the input. If no
                reviews are included, state that review information is unavailable rather than inferring it.
                """, input);
        return new ReviewSummaryResult(requiredString(response, "summary"));
    }

    private JsonNode complete(String schemaName, String schema, String instructions, Object input) {
        LLMRequest request = new LLMRequest(
                List.of(
                        new LLMMessage(LLMRole.SYSTEM, instructions
                                + "\nReturn exactly one JSON object conforming to this schema:\n" + schema),
                        new LLMMessage(LLMRole.USER, writeJson(input))
                ),
                new LLMOptions(0.7, 1000, LLMResponseFormat.JSON_OBJECT),
                new LLMOutputSchema(schemaName, schema)
        );
        String content = llmClient.complete(request).content();
        try {
            JsonNode parsed = objectMapper.readTree(content);
            if (parsed == null || !parsed.isObject()) {
                throw new LLMProtocolException("Review analysis returned a non-object JSON value");
            }
            return parsed;
        } catch (JacksonException exception) {
            throw new LLMProtocolException("Review analysis returned malformed JSON", exception);
        }
    }

    private String requiredString(JsonNode response, String fieldName) {
        JsonNode value = response.get(fieldName);
        if (value == null || !value.isString() || value.stringValue().isBlank()) {
            throw new LLMProtocolException("Review analysis returned an invalid " + fieldName);
        }
        String result = value.stringValue().trim();
        int maxLength = fieldName.equals("trustPhrase") ? 200 : 8000;
        if (result.length() > maxLength) {
            throw new LLMProtocolException("Review analysis returned an oversized " + fieldName);
        }
        return result;
    }

    private Map<String, Object> reviewData(Review review) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("retailer", review.getRetailer().getName());
        data.put("rating", review.getRating());
        data.put("text", review.getText());
        data.put("reviewedAt", review.getReviewedAt().toString());
        return data;
    }

    private Map<String, Object> productData(Product product) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", product.getName());
        data.put("brand", product.getBrand());
        data.put("model", product.getModel());
        data.put("category", product.getCategory());
        data.put("description", product.getDescription());
        data.put("attributes", product.getAttributes());
        return data;
    }

    private Map<String, Object> listingData(Listing listing) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("retailer", listing.getRetailer().getName());
        data.put("price", listing.getPrice());
        data.put("condition", listing.getCondition());
        data.put("inStock", listing.isInStock());
        return data;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize review analysis input", exception);
        }
    }
}
