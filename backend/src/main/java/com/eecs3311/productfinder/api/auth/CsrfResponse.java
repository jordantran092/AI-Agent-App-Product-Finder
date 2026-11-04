package com.eecs3311.productfinder.api.auth;

public record CsrfResponse(String token, String headerName) {
}
