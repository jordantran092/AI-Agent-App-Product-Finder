package com.eecs3311.productfinder.api.auth;

import java.util.Map;

public record ApiError(String error, Map<String, String> fields) {
}
