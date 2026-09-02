package za.co.trademesh.modules.access.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.access.application.PhoneIdentityRepository.VerificationMethod;
import za.co.trademesh.modules.payment.application.MomoClient;

@Service
public class PhoneAuthService {

    private static final String OTP_ACTION = "otp-send";
    private static final String MOMO_ACTION = "momo-sign-in";

    private final BotChallengeService botChallenges;
    private final OtpProvider otpProvider;
    private final OtpSendRateLimiter otpRateLimiter;
    private final OtpProperties otpProperties;
    private final MomoClient momoClient;
    private final MomoSignInRepository momoSignIns;
    private final MomoProfileRepository momoProfiles;
    private final MomoSignInProperties momoProperties;
    private final AccessPollTokens pollTokens;
    private final ExternalAccountService externalAccounts;
    private final AuthService authService;
    private final Clock clock;

    public PhoneAuthService(
            BotChallengeService botChallenges,
            OtpProvider otpProvider,
            OtpSendRateLimiter otpRateLimiter,
            OtpProperties otpProperties,
            MomoClient momoClient,
            MomoSignInRepository momoSignIns,
            MomoProfileRepository momoProfiles,
            MomoSignInProperties momoProperties,
            AccessPollTokens pollTokens,
            ExternalAccountService externalAccounts,
            AuthService authService,
            Clock clock) {
        this.botChallenges = botChallenges;
        this.otpProvider = otpProvider;
        this.otpRateLimiter = otpRateLimiter;
        this.otpProperties = otpProperties;
        this.momoClient = momoClient;
        this.momoSignIns = momoSignIns;
        this.momoProfiles = momoProfiles;
        this.momoProperties = momoProperties;
        this.pollTokens = pollTokens;
        this.externalAccounts = externalAccounts;
        this.authService = authService;
        this.clock = clock;
    }

    public void sendOtp(String rawPhoneNumber, String turnstileToken, String remoteIp) {
        String phoneNumber = PhoneNumbers.normalize(rawPhoneNumber);
        botChallenges.requireValid(turnstileToken, remoteIp, OTP_ACTION);
        Instant now = clock.instant();
        if (!otpRateLimiter.acquire(phoneNumber, now, otpProperties.sendCooldown())) {
            throw AccessException.otpRateLimited();
        }
        otpProvider.send(phoneNumber);
    }

    public AuthService.AuthTokens verifyOtp(String rawPhoneNumber, String code) {
        String phoneNumber = PhoneNumbers.normalize(rawPhoneNumber);
        if (!otpProvider.verify(phoneNumber, code)) {
            throw AccessException.otpInvalid();
        }
        UUID userId = externalAccounts.resolve(phoneNumber, VerificationMethod.TWILIO_OTP);
        return authService.authenticateExternal(userId);
    }

    @Transactional
    public InitiatedSignIn initiateMomo(String rawPhoneNumber, String turnstileToken, String remoteIp) {
        String phoneNumber = PhoneNumbers.normalize(rawPhoneNumber);
        botChallenges.requireValid(turnstileToken, remoteIp, MOMO_ACTION);
        MomoClient.ConsentRequest consent = momoClient.bcAuthorize(phoneNumber);
        AccessPollTokens.IssuedToken token = pollTokens.issue();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(momoProperties.pollTokenTtl());
        momoSignIns.save(new MomoSignIn(
                UUID.randomUUID(),
                token.hash(),
                phoneNumber,
                consent.providerReference(),
                consent.status(),
                expiresAt,
                null,
                now));
        return new InitiatedSignIn(token.raw(), consent.status(), expiresAt);
    }

    @Transactional
    public MomoClient.ConsentStatus momoStatus(String rawPollToken) {
        MomoSignIn signIn = requireAvailable(rawPollToken);
        MomoClient.ConsentStatus current = momoClient.getConsentStatus(signIn.providerReference());
        momoSignIns.updateStatus(signIn.id(), current);
        return current;
    }

    @Transactional
    public CompletedSignIn completeMomo(String rawPollToken) {
        MomoSignIn signIn = requireAvailable(rawPollToken);
        MomoClient.ConsentStatus status = momoClient.getConsentStatus(signIn.providerReference());
        momoSignIns.updateStatus(signIn.id(), status);
        if (status != MomoClient.ConsentStatus.APPROVED) {
            throw AccessException.momoConsentPending();
        }

        MomoClient.UserInfo userInfo = momoClient.getBasicUserInfo(signIn.providerReference());
        UUID userId = externalAccounts.resolve(signIn.phoneNumber(), VerificationMethod.MOMO_CONSENT);
        Instant now = clock.instant();
        momoProfiles.save(userId, signIn.phoneNumber(), userInfo, now);
        if (!momoSignIns.complete(signIn.id(), now)) {
            throw AccessException.momoSignInUnavailable();
        }
        return new CompletedSignIn(userInfo, authService.authenticateExternal(userId));
    }

    public boolean validateMomoAccount(String rawPhoneNumber, String turnstileToken, String remoteIp) {
        String phoneNumber = PhoneNumbers.normalize(rawPhoneNumber);
        botChallenges.requireValid(turnstileToken, remoteIp, MOMO_ACTION);
        return momoClient.validateAccountHolder(phoneNumber);
    }

    private MomoSignIn requireAvailable(String rawPollToken) {
        MomoSignIn signIn = momoSignIns
                .findByPollTokenHash(pollTokens.hash(rawPollToken))
                .orElseThrow(AccessException::momoSignInUnavailable);
        if (!signIn.availableAt(clock.instant())) {
            throw AccessException.momoSignInUnavailable();
        }
        return signIn;
    }

    public record InitiatedSignIn(String pollToken, MomoClient.ConsentStatus status, Instant expiresAt) {}

    public record CompletedSignIn(MomoClient.UserInfo profile, AuthService.AuthTokens tokens) {}
}
