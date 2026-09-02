package za.co.trademesh.modules.handover.application;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
class SecureHandoverTokenGenerator implements HandoverTokenGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        byte[] nonce = new byte[32];
        random.nextBytes(nonce);
        return "tmh_" + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
    }
}
