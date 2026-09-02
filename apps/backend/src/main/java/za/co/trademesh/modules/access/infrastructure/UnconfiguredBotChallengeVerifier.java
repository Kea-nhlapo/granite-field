package za.co.trademesh.modules.access.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.access.application.BotChallengeVerifier;

/** Fallback bot-challenge verifier: the application starts, the challenge itself fails loudly. */
@Component
@ConditionalOnProperty(
        prefix = "trademesh.access.turnstile",
        name = "provider",
        havingValue = "unconfigured",
        matchIfMissing = true)
class UnconfiguredBotChallengeVerifier implements BotChallengeVerifier {

    @Override
    public VerificationResult verify(String token, String remoteIp, String expectedAction) {
        throw new IllegalStateException(
                "No bot challenge provider is configured; set trademesh.access.turnstile.provider");
    }
}
