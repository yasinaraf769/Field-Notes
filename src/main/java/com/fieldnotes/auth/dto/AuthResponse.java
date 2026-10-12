package com.fieldnotes.auth.dto;

public record AuthResponse(
        UserSummaryResponse user,
        String accessToken,
        String refreshToken,
        String tokenType
) {
    public static AuthResponse of(UserSummaryResponse user, String accessToken, String refreshToken) {
        return new AuthResponse(user, accessToken, refreshToken, "Bearer");
    }
}
