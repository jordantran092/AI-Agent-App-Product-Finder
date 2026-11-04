package com.eecs3311.productfinder.infrastructure.llm;

import com.eecs3311.productfinder.application.llm.LLMConfigurationException;
import com.eecs3311.productfinder.application.llm.LLMProtocolException;
import com.eecs3311.productfinder.application.llm.LLMProviderException;
import com.eecs3311.productfinder.application.llm.TransientLLMException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class SharedLLMClient {

    private final RestClient restClient;
    private final DeepSeekProperties properties;

    public SharedLLMClient(RestClient deepSeekRestClient, DeepSeekProperties properties) {
        this.restClient = deepSeekRestClient;
        this.properties = properties;
    }

    public String postJson(Map<String, Object> body) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new LLMConfigurationException("DEEPSEEK_API_KEY is required to make language model requests");
        }
        try {
            String response = restClient.post()
                    .uri("/chat/completions")
                    .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                    .body(body)
                    .retrieve()
                    .body(String.class);
            if (response == null || response.isBlank()) {
                throw new LLMProtocolException("DeepSeek returned an empty response body");
            }
            return response;
        } catch (ResourceAccessException exception) {
            throw new TransientLLMException("DeepSeek request timed out or failed at the network layer", exception);
        } catch (HttpStatusCodeException exception) {
            int status = exception.getStatusCode().value();
            if (isTransientStatus(status)) {
                throw new TransientLLMException("DeepSeek returned a temporary HTTP error (" + status + ")", exception);
            }
            throw new LLMProviderException("DeepSeek rejected the request with HTTP status " + status, exception);
        } catch (RestClientException exception) {
            throw new LLMProtocolException("Unable to process the DeepSeek HTTP response", exception);
        }
    }

    private boolean isTransientStatus(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }
}
