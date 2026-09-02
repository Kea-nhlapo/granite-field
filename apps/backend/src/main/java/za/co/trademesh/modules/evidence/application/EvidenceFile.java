package za.co.trademesh.modules.evidence.application;

import java.util.UUID;

public record EvidenceFile(UUID fileId, String sha256) {}
