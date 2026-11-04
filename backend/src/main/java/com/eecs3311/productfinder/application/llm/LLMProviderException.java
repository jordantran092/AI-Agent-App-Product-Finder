package com.eecs3311.productfinder.application.llm;

public final class LLMProviderException extends LLMException {

    public LLMProviderException(String message, Throwable cause) {
        super(message, cause);
    }

    public LLMProviderException(String message) {
        super(message);
    }
}
