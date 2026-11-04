package com.eecs3311.productfinder.application.llm;

public final class LLMProtocolException extends LLMException {

    public LLMProtocolException(String message, Throwable cause) {
        super(message, cause);
    }

    public LLMProtocolException(String message) {
        super(message);
    }
}
