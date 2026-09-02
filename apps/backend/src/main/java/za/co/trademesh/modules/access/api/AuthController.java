package za.co.trademesh.modules.access.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.trademesh.modules.access.application.AuthService;
import za.co.trademesh.modules.access.application.PhoneAuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PhoneAuthService phoneAuthService;

    public AuthController(AuthService authService, PhoneAuthService phoneAuthService) {
        this.authService = authService;
        this.phoneAuthService = phoneAuthService;
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

    @PostMapping("/otp/send")
    ResponseEntity<AuthContracts.OtpAcceptedResponse> sendOtp(
            @Valid @RequestBody AuthContracts.OtpSendRequest request, HttpServletRequest httpRequest) {
        phoneAuthService.sendOtp(request.phoneNumber(), request.turnstileToken(), httpRequest.getRemoteAddr());
        return ResponseEntity.accepted().body(new AuthContracts.OtpAcceptedResponse("accepted"));
    }

    @PostMapping("/otp/verify")
    AuthContracts.TokenResponse verifyOtp(@Valid @RequestBody AuthContracts.OtpVerifyRequest request) {
        return toResponse(phoneAuthService.verifyOtp(request.phoneNumber(), request.code()));
    }

    @PostMapping("/momo/initiate")
    ResponseEntity<AuthContracts.MomoInitiatedResponse> initiateMomo(
            @Valid @RequestBody AuthContracts.MomoInitiateRequest request, HttpServletRequest httpRequest) {
        PhoneAuthService.InitiatedSignIn signIn = phoneAuthService.initiateMomo(
                request.phoneNumber(), request.turnstileToken(), httpRequest.getRemoteAddr());
        return ResponseEntity.accepted()
                .body(new AuthContracts.MomoInitiatedResponse(signIn.pollToken(), signIn.status(), signIn.expiresAt()));
    }

    @GetMapping("/momo/status/{pollToken}")
    AuthContracts.MomoStatusResponse momoStatus(@PathVariable String pollToken) {
        return new AuthContracts.MomoStatusResponse(phoneAuthService.momoStatus(pollToken));
    }

    @PostMapping("/momo/userinfo/{pollToken}")
    AuthContracts.MomoSignInResponse completeMomo(@PathVariable String pollToken) {
        PhoneAuthService.CompletedSignIn signIn = phoneAuthService.completeMomo(pollToken);
        return new AuthContracts.MomoSignInResponse(
                new AuthContracts.MomoProfileResponse(
                        signIn.profile().givenName(),
                        signIn.profile().familyName(),
                        signIn.profile().locale()),
                toResponse(signIn.tokens()));
    }

    @PostMapping("/momo/validate")
    AuthContracts.MomoAccountHolderResponse validateMomo(
            @Valid @RequestBody AuthContracts.MomoValidationRequest request, HttpServletRequest httpRequest) {
        return new AuthContracts.MomoAccountHolderResponse(phoneAuthService.validateMomoAccount(
                request.phoneNumber(), request.turnstileToken(), httpRequest.getRemoteAddr()));
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
