package com.eecs3311.productfinder.application.llm;

public final class LLMResponseValidationException extends LLMException {

    public LLMResponseValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public LLMResponseValidationException(String message) {
        super(message);
    }
}
