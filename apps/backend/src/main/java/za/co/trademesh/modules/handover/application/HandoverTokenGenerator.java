package za.co.trademesh.modules.handover.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface HandoverTokenGenerator {

    String generate(TokenClaims claims);

    TokenClaims verify(String token);

    record TokenClaims(
            UUID challengeId, UUID shipmentId, BigDecimal expectedQuantity, String unitOfMeasure, Instant expiresAt) {}
}
