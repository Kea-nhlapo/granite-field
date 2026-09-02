package za.co.trademesh.modules.access.application;

import java.util.List;

public interface BotChallengeVerifier {

    VerificationResult verify(String token, String remoteIp, String expectedAction);

    record VerificationResult(boolean success, List<String> errorCodes) {
        public VerificationResult {
            errorCodes = errorCodes == null ? List.of() : List.copyOf(errorCodes);
        }
    }
}
