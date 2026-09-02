package za.co.trademesh.modules.evidence.domain;

import java.util.UUID;

public record EvidenceFileReference(UUID fileId, String sha256) {}
