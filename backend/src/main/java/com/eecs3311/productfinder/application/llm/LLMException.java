package com.eecs3311.productfinder.application.llm;

public abstract class LLMException extends RuntimeException {

    protected LLMException(String message) {
        super(message);
    }

    protected LLMException(String message, Throwable cause) {
        super(message, cause);
    }
}
