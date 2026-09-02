package za.co.trademesh.modules.risk.domain;

import java.time.Instant;
import java.util.UUID;

public record RiskEvidenceReference(String evidenceType, UUID evidenceId, Instant observedAt) {}
