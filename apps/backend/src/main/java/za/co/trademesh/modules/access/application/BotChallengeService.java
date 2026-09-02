package za.co.trademesh.modules.access.application;

import org.springframework.stereotype.Service;

@Service
public class BotChallengeService {

    private final BotChallengeVerifier verifier;

    public BotChallengeService(BotChallengeVerifier verifier) {
        this.verifier = verifier;
    }

    public void requireValid(String token, String remoteIp, String expectedAction) {
        BotChallengeVerifier.VerificationResult result = verifier.verify(token, remoteIp, expectedAction);
        if (!result.success()) {
            throw AccessException.botChallengeFailed();
        }
    }
}
