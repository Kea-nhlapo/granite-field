package za.co.trademesh.modules.document.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentMismatchIndicator(
        UUID id,
        DocumentMismatchRule rule,
        int ruleVersion,
        String fieldPath,
        DocumentMismatchSeverity severity,
        UUID referenceDocumentId,
        String referenceValue,
        UUID comparedDocumentId,
        String comparedValue,
        String explanation,
        Instant createdAt) {}
