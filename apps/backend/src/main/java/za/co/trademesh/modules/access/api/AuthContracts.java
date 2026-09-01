package za.co.trademesh.modules.access.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.shared.security.AccountRole;

import java.util.Set;
import java.util.UUID;

public final class AuthContracts {

    private AuthContracts() {
    }

    public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotNull RegistrationType accountType
    ) {
    }

    public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(
        UUID userId,
        String tokenType,
        String accessToken,
        long expiresInSeconds,
        String refreshToken,
        Set<AccountRole> roles
    ) {
    }
}
