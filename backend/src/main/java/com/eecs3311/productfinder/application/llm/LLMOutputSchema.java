package com.eecs3311.productfinder.application.llm;

public record LLMOutputSchema(String name, String jsonSchema) {

    public LLMOutputSchema {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (jsonSchema == null || jsonSchema.isBlank()) {
            throw new IllegalArgumentException("jsonSchema must not be blank");
        }
    }
}
