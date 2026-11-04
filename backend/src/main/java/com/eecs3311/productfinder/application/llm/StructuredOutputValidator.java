package com.eecs3311.productfinder.application.llm;

import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

@Component
public class StructuredOutputValidator {

    private final ObjectMapper objectMapper;

    public StructuredOutputValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validate(String content, LLMOutputSchema outputSchema) {
        Objects.requireNonNull(outputSchema, "outputSchema must not be null");
        var schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        var schema = schemaRegistry.getSchema(outputSchema.jsonSchema());
        final var value = parseContent(content);
        var violations = schema.validate(value);
        if (!violations.isEmpty()) {
            var details = violations.stream()
                    .map(error -> error.getInstanceLocation() + " " + error.getMessage())
                    .sorted()
                    .toList();
            throw new LLMResponseValidationException(
                    "Model output does not conform to schema '" + outputSchema.name() + "': " + details
            );
        }
    }

    private tools.jackson.databind.JsonNode parseContent(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (JacksonException exception) {
            throw new LLMResponseValidationException("Model output is not valid JSON", exception);
        }
    }
}
