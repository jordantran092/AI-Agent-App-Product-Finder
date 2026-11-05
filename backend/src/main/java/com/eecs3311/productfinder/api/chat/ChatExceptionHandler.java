package com.eecs3311.productfinder.api.chat;

import com.eecs3311.productfinder.api.auth.ApiError;
import com.eecs3311.productfinder.application.llm.LLMConfigurationException;
import com.eecs3311.productfinder.application.llm.LLMException;
import com.eecs3311.productfinder.application.llm.LLMUnavailableException;
import com.eecs3311.productfinder.domain.chat.RequirementsValidationException;
import com.eecs3311.productfinder.domain.catalog.ProductSpecValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ChatExceptionHandler {

    @ExceptionHandler(LLMUnavailableException.class)
    public ResponseEntity<ApiError> handleUnavailable(LLMUnavailableException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(LLMConfigurationException.class)
    public ResponseEntity<ApiError> handleConfiguration(LLMConfigurationException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(LLMException.class)
    public ResponseEntity<ApiError> handleProviderFailure(LLMException exception) {
        return error(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    @ExceptionHandler(RequirementsValidationException.class)
    public ResponseEntity<ApiError> handleInvalidRequirements(RequirementsValidationException exception) {
        return error(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    @ExceptionHandler(ProductSpecValidationException.class)
    public ResponseEntity<ApiError> handleInvalidCatalogSpecs(ProductSpecValidationException exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleInvalidState(IllegalStateException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleInvalidInput(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message, Map.of()));
    }
}
