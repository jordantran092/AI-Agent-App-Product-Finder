package com.eecs3311.productfinder.application.llm;

public interface LLMClient {

    LLMResponse complete(LLMRequest request);
}
