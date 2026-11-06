package com.eecs3311.productfinder.api.savedlist;

import com.eecs3311.productfinder.api.auth.ApiError;
import com.eecs3311.productfinder.domain.catalog.ProductNotFoundException;
import com.eecs3311.productfinder.domain.savedlist.SavedItemNotFoundException;
import com.eecs3311.productfinder.domain.savedlist.EmptySavedListException;
import com.eecs3311.productfinder.domain.savedlist.SharedListNotFoundException;
import com.eecs3311.productfinder.domain.user.ProfileValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SavedProductExceptionHandler {

    @ExceptionHandler({ProductNotFoundException.class, SavedItemNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(EmptySavedListException.class)
    public ResponseEntity<ApiError> handleEmptyList(EmptySavedListException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(SharedListNotFoundException.class)
    public ResponseEntity<ApiError> handleUnavailableShare(SharedListNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ProfileValidationException.class)
    public ResponseEntity<ApiError> handleInvalidProfile(ProfileValidationException exception) {
        return ResponseEntity.badRequest().body(new ApiError(
                exception.getMessage(),
                Map.of("violations", String.join("; ", exception.getViolations()))
        ));
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(message, Map.of()));
    }
}
