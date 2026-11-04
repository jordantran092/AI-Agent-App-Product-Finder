package com.eecs3311.productfinder.application.llm;

public final class TransientLLMException extends LLMException {

    public TransientLLMException(String message, Throwable cause) {
        super(message, cause);
    }
}
