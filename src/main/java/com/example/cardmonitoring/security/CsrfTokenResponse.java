package com.example.cardmonitoring.security;

public record CsrfTokenResponse(String headerName, String token) {
}
