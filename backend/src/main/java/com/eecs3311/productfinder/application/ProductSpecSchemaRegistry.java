package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.domain.catalog.ProductSpecValidationException;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class ProductSpecSchemaRegistry {

    private final ObjectMapper objectMapper;
    private final Map<ProductCategory, Schema> schemas = new EnumMap<>(ProductCategory.class);

    public ProductSpecSchemaRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SchemaRegistry schemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        for (ProductCategory category : ProductCategory.values()) {
            String resource = "product-schemas/" + category.schemaName() + "-v1.schema.json";
            try (var input = new ClassPathResource(resource).getInputStream()) {
                schemas.put(category, schemaRegistry.getSchema(input));
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to load required product schema: " + resource, exception);
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Unable to parse required product schema: " + resource, exception);
            }
        }
    }

    public Schema getSchema(ProductCategory category) {
        Objects.requireNonNull(category, "category must not be null");
        return schemas.get(category);
    }

    public String getSchemaJson(ProductCategory category) {
        return getSchema(category).getSchemaNode().toString();
    }

    public String requirementsSchema(ProductCategory category) {
        Objects.requireNonNull(category, "category must not be null");
        String properties = getSchema(category).getSchemaNode().get("properties").toString();
        String conditions = java.util.Arrays.stream(
                        com.eecs3311.productfinder.domain.catalog.ProductCondition.values())
                .map(condition -> "\"" + condition.name() + "\"")
                .collect(java.util.stream.Collectors.joining(", "));
        return """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "title": "%s requirements",
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["category", "maxPrice", "mustHaves", "niceToHaves", "excludedBrands", "condition", "attributes"],
                  "properties": {
                    "category": {"const": "%s"},
                    "maxPrice": {"type": ["number", "null"], "minimum": 0},
                    "mustHaves": {"type": "array", "items": {"type": "string"}},
                    "niceToHaves": {"type": "array", "items": {"type": "string"}},
                    "excludedBrands": {"type": "array", "items": {"type": "string"}},
                    "condition": {"type": ["string", "null"], "enum": [%s, null]},
                    "attributes": {
                      "type": "object",
                      "properties": %s,
                      "additionalProperties": false
                    }
                  }
                }
                """.formatted(category.schemaName(), category.schemaName(), conditions, properties);
    }

    public String requirementsExtractionSchema() {
        List<Map<String, Object>> categorySchemas = java.util.Arrays.stream(ProductCategory.values())
                .map(category -> {
                    try {
                        return objectMapper.readValue(
                                requirementsSchema(category),
                                new TypeReference<Map<String, Object>>() { }
                        );
                    } catch (JacksonException exception) {
                        throw new IllegalStateException("Unable to create requirements schema", exception);
                    }
                })
                .toList();
        Map<String, Object> followUpSchema = Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("status", "followUpQuestion"),
                "properties", Map.of(
                        "status", Map.of("const", "FOLLOW_UP"),
                        "followUpQuestion", Map.of("type", "string", "minLength", 1)
                )
        );
        List<Map<String, Object>> branches = new java.util.ArrayList<>();
        for (Map<String, Object> categorySchema : categorySchemas) {
            Map<String, Object> branch = new java.util.LinkedHashMap<>(categorySchema);
            List<String> required = new java.util.ArrayList<>(stringList(branch.get("required")));
            required.add("status");
            branch.put("required", required);
            Map<String, Object> properties = mutableObjectMap(branch.get("properties"));
            properties.put("status", Map.of("const", "READY"));
            branch.put("properties", properties);
            branches.add(branch);
        }
        branches.add(followUpSchema);
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "$schema", "https://json-schema.org/draft/2020-12/schema",
                    "oneOf", branches
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize requirements extraction schema", exception);
        }
    }

    public String allProductSchemasJson() {
        Map<String, Object> categorySchemas = new java.util.LinkedHashMap<>();
        for (ProductCategory category : ProductCategory.values()) {
            categorySchemas.put(category.schemaName(), getSchema(category).getSchemaNode());
        }
        try {
            return objectMapper.writeValueAsString(categorySchemas);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize product schemas", exception);
        }
    }

    public List<String> validateRequirementAttributes(ProductCategory category, Map<String, Object> attributes) {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(attributes, "attributes must not be null");
        var schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(requirementsSchema(category));
        var requirement = objectMapper.createObjectNode()
                .put("category", category.schemaName())
                .putNull("maxPrice")
                .putNull("condition");
        requirement.putArray("mustHaves");
        requirement.putArray("niceToHaves");
        requirement.putArray("excludedBrands");
        requirement.set("attributes", objectMapper.valueToTree(attributes));
        return schema.validate(requirement).stream()
                .map(error -> error.getInstanceLocation() + " " + error.getMessage())
                .toList();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)
                || values.stream().anyMatch(element -> !(element instanceof String))) {
            throw new IllegalStateException("Generated schema required fields are invalid");
        }
        return values.stream().map(String.class::cast).toList();
    }

    private Map<String, Object> mutableObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> values)) {
            throw new IllegalStateException("Generated schema properties are invalid");
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalStateException("Generated schema contains a non-string property name");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    public List<String> validate(ProductCategory category, Map<String, Object> attributes) {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(attributes, "attributes must not be null");
        var instance = objectMapper.valueToTree(attributes);
        return getSchema(category).validate(instance).stream()
                .map(error -> error.getInstanceLocation() + " " + error.getMessage())
                .toList();
    }

    public void validateOrThrow(ProductCategory category, Map<String, Object> attributes) {
        List<String> violations = validate(category, attributes);
        if (!violations.isEmpty()) {
            throw new ProductSpecValidationException(category, violations);
        }
    }
}
