package za.co.trademesh.shared.storage;

import java.time.Instant;
import java.util.UUID;

public record StoredFile(
        UUID id,
        UUID businessId,
        FileCategory category,
        String originalFilename,
        String objectKey,
        String contentType,
        String extension,
        long sizeBytes,
        String sha256,
        FileScanStatus scanStatus,
        FileStorageStatus storageStatus,
        UUID uploadedByUserId,
        Instant createdAt,
        Instant storedAt) {}
