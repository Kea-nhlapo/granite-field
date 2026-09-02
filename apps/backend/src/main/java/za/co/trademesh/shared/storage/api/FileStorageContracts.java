package za.co.trademesh.shared.storage.api;

import java.time.Instant;
import java.util.UUID;
import za.co.trademesh.shared.storage.FileCategory;
import za.co.trademesh.shared.storage.FileScanStatus;
import za.co.trademesh.shared.storage.FileStorageService;
import za.co.trademesh.shared.storage.FileStorageStatus;
import za.co.trademesh.shared.storage.StoredFile;

public final class FileStorageContracts {

    private FileStorageContracts() {}

    public record FileMetadataResponse(
            UUID fileId,
            UUID businessId,
            FileCategory category,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String sha256,
            FileScanStatus scanStatus,
            FileStorageStatus storageStatus,
            UUID uploadedByUserId,
            Instant createdAt,
            Instant storedAt) {
        static FileMetadataResponse from(StoredFile file) {
            return new FileMetadataResponse(
                    file.id(),
                    file.businessId(),
                    file.category(),
                    file.originalFilename(),
                    file.contentType(),
                    file.sizeBytes(),
                    file.sha256(),
                    file.scanStatus(),
                    file.storageStatus(),
                    file.uploadedByUserId(),
                    file.createdAt(),
                    file.storedAt());
        }
    }

    public record DownloadAccessResponse(String url, Instant expiresAt) {
        static DownloadAccessResponse from(FileStorageService.DownloadAccess access) {
            return new DownloadAccessResponse(access.url().toASCIIString(), access.expiresAt());
        }
    }
}
