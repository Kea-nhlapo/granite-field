package za.co.trademesh.modules.access.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.trademesh.modules.access.application.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    ResponseEntity<AuthContracts.TokenResponse> register(@Valid @RequestBody AuthContracts.RegisterRequest request) {
        AuthService.AuthTokens tokens =
                authService.register(request.email(), request.password(), request.accountType());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(tokens));
    }

    @PostMapping("/login")
    AuthContracts.TokenResponse login(@Valid @RequestBody AuthContracts.LoginRequest request) {
        return toResponse(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    AuthContracts.TokenResponse refresh(@Valid @RequestBody AuthContracts.RefreshRequest request) {
        return toResponse(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@Valid @RequestBody AuthContracts.LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    private static AuthContracts.TokenResponse toResponse(AuthService.AuthTokens tokens) {
        return new AuthContracts.TokenResponse(
                tokens.userId(),
                tokens.tokenType(),
                tokens.accessToken(),
                tokens.expiresInSeconds(),
                tokens.refreshToken(),
                tokens.roles());
    }
}
