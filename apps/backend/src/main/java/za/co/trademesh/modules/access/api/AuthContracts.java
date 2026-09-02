package za.co.trademesh.modules.access.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.modules.payment.application.MomoClient;
import za.co.trademesh.shared.security.AccountRole;

public final class AuthContracts {

    private AuthContracts() {}

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            @NotNull RegistrationType accountType) {}

    public record LoginRequest(
            @NotBlank @Email String email, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record LogoutRequest(@NotBlank String refreshToken) {}

    public record OtpSendRequest(
            @NotBlank @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$")
            String phoneNumber,

            @NotBlank @Size(max = 2048) String turnstileToken) {}

    public record OtpVerifyRequest(
            @NotBlank @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$")
            String phoneNumber,

            @NotBlank @Pattern(regexp = "^[0-9]{4,10}$") String code) {}

    public record MomoInitiateRequest(
            @NotBlank @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$")
            String phoneNumber,

            @NotBlank @Size(max = 2048) String turnstileToken) {}

    public record MomoValidationRequest(
            @NotBlank @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$")
            String phoneNumber,

            @NotBlank @Size(max = 2048) String turnstileToken) {}

    public record TokenResponse(
            UUID userId,
            String tokenType,
            String accessToken,
            long expiresInSeconds,
            String refreshToken,
            Set<AccountRole> roles) {}

    public record OtpAcceptedResponse(String status) {}

    public record MomoInitiatedResponse(String pollToken, MomoClient.ConsentStatus status, Instant expiresAt) {}

    public record MomoStatusResponse(MomoClient.ConsentStatus status) {}

    public record MomoProfileResponse(String givenName, String familyName, String locale) {}

    public record MomoSignInResponse(MomoProfileResponse profile, TokenResponse tokens) {}

    public record MomoAccountHolderResponse(boolean active) {}
}
