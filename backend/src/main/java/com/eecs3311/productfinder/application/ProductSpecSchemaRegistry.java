package com.eecs3311.productfinder.application;

import com.eecs3311.productfinder.domain.catalog.ProductCategory;
import com.eecs3311.productfinder.domain.catalog.ProductSpecValidationException;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

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
