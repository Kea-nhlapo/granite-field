package za.co.trademesh.modules.document.domain;

import java.time.Instant;
import java.util.UUID;

public record DocumentRecord(
        UUID id,
        UUID businessId,
        UUID storedFileId,
        DocumentType type,
        DocumentState state,
        int processingAttempts,
        String lastError,
        UUID createdByUserId,
        Instant createdAt,
        Instant updatedAt) {}
