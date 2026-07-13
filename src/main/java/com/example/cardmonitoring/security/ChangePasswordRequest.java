package com.example.cardmonitoring.security;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
