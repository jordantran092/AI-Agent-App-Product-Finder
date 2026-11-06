package com.eecs3311.productfinder.application.llm;

import com.eecs3311.productfinder.application.ProductSpecSchemaRegistry;
import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class SchemaGuidedPromptBuilder {

    private final ProductSpecSchemaRegistry productSchemas;

    public SchemaGuidedPromptBuilder(ProductSpecSchemaRegistry productSchemas) {
        this.productSchemas = productSchemas;
    }

    public LLMRequest build(
            ProductCategory category,
            String taskInstructions,
            List<LLMMessage> conversation,
            LLMOutputSchema outputSchema
    ) {
        Objects.requireNonNull(category, "category must not be null");
        if (taskInstructions == null || taskInstructions.isBlank()) {
            throw new IllegalArgumentException("taskInstructions must not be blank");
        }
        Objects.requireNonNull(conversation, "conversation must not be null");
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage(LLMRole.SYSTEM, systemPrompt(category, taskInstructions, outputSchema)));
        messages.addAll(conversation);
        LLMOptions options = new LLMOptions(
                null,
                null,
                outputSchema == null ? LLMResponseFormat.TEXT : LLMResponseFormat.JSON_OBJECT
        );
        return new LLMRequest(messages, options, outputSchema);
    }

    public LLMRequest requirementsExtraction(ProductCategory category, List<LLMMessage> conversation) {
        LLMOutputSchema schema = new LLMOutputSchema(
                category.schemaName() + "-requirements",
                productSchemas.requirementsSchema(category)
        );
        return build(category, """
                Extract only requirements stated or clearly implied in the conversation.
                Use null for an unknown maximum price or condition, and empty arrays/maps when no value is specified.
                Do not invent category-specific attribute requirements.
                """, conversation, schema);
    }

    public LLMRequest requirementsExtraction(
            List<LLMMessage> conversation,
            String profileContext
    ) {
        Objects.requireNonNull(conversation, "conversation must not be null");
        LLMOutputSchema schema = new LLMOutputSchema(
                "category-requirements-extraction",
                productSchemas.requirementsExtractionSchema()
        );
        String context = profileContext == null || profileContext.isBlank()
                ? ""
                : "\n\nThe user's saved profile settings remain active eligibility filters: " + profileContext
                        + ". Do not override or weaken these settings.";
        String instructions = """
                Identify the product category and extract only requirements stated or clearly implied in the conversation.
                If the category cannot be determined or a necessary detail is missing, return status FOLLOW_UP and ask one concise question.
                Otherwise return status READY, use null for unknown maximum price or condition, and empty arrays/maps when no value is specified.
                Do not invent category-specific attribute requirements. Put each explicit, verifiable category specification in attributes so the application can enforce it before ranking.
                Keep mustHaves for concise user-facing restatements of non-negotiable criteria; if a non-negotiable criterion cannot be represented by the category schema, ask a follow-up question rather than silently dropping it.
                Use profile settings only as context; do not treat them as explicit request constraints.
                Treat all prices, budgets, and price limits as Canadian dollars (CAD). If the user mentions another currency, ask them to restate the amount in CAD instead of estimating an exchange rate.
                """ + context;
        List<LLMMessage> messages = new ArrayList<>();
        messages.add(new LLMMessage(LLMRole.SYSTEM, instructions
                + "\n\nUse these versioned product specification schemas to identify valid category attributes:\n"
                + productSchemas.allProductSchemasJson()
                + "\n\nReturn exactly one JSON value conforming to the requirements extraction schema. "
                + "Do not include markdown fences or additional text.\n" + schema.jsonSchema()));
        messages.addAll(conversation);
        return new LLMRequest(
                messages,
                new LLMOptions(null, null, LLMResponseFormat.JSON_OBJECT),
                schema
        );
    }

    private String systemPrompt(
            ProductCategory category,
            String taskInstructions,
            LLMOutputSchema outputSchema
    ) {
        StringBuilder prompt = new StringBuilder(taskInstructions)
                .append("\n\nUse the following product specification schema for category '")
                .append(category.schemaName())
                .append("':\n")
                .append(productSchemas.getSchemaJson(category));
        if (outputSchema != null) {
            prompt.append("\n\nReturn exactly one JSON value conforming to output schema '")
                    .append(outputSchema.name())
                    .append("'. Do not include markdown fences or additional text.\n")
                    .append(outputSchema.jsonSchema());
        }
        return prompt.toString();
    }
}
