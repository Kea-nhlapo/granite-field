package za.co.trademesh.modules.access.infrastructure;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.access.application.BotChallengeVerifier;

@Component
@ConditionalOnProperty(prefix = "trademesh.access.turnstile", name = "provider", havingValue = "local")
class LocalTurnstileVerifier implements BotChallengeVerifier {

    private final Set<String> usedTokens = ConcurrentHashMap.newKeySet();

    @Override
    public VerificationResult verify(String token, String remoteIp, String expectedAction) {
        String requiredPrefix = "local-pass:" + expectedAction + ":";
        if (token == null || !token.startsWith(requiredPrefix)) {
            return new VerificationResult(false, List.of("invalid-input-response"));
        }
        if (!usedTokens.add(token)) {
            return new VerificationResult(false, List.of("timeout-or-duplicate"));
        }
        return new VerificationResult(true, List.of());
    }
}
