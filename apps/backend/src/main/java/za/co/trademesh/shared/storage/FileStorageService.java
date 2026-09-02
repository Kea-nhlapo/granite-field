package za.co.trademesh.shared.storage;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileStorageService {

    private static final DateTimeFormatter KEY_DATE =
            DateTimeFormatter.ofPattern("uuuu/MM").withZone(ZoneOffset.UTC);

    private final FileUploadValidator validator;
    private final FileScanner scanner;
    private final ObjectStorage objectStorage;
    private final StoredFileRepository files;
    private final ObjectStorageProperties properties;
    private final Clock clock;

    public FileStorageService(
            FileUploadValidator validator,
            FileScanner scanner,
            ObjectStorage objectStorage,
            StoredFileRepository files,
            ObjectStorageProperties properties,
            Clock clock) {
        this.validator = validator;
        this.scanner = scanner;
        this.objectStorage = objectStorage;
        this.files = files;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = StorageException.class)
    public StoredFile upload(
            UUID businessId,
            UUID uploadedByUserId,
            FileCategory category,
            String originalFilename,
            String declaredContentType,
            byte[] content) {
        FileUploadValidator.ValidatedFile validated =
                validator.validate(originalFilename, declaredContentType, content);
        FileScanStatus scanStatus =
                scanner.scan(validated.originalFilename(), validated.contentType(), validated.content());
        if (scanStatus != FileScanStatus.CLEAN) {
            throw StorageException.scanRejected();
        }

        Instant now = clock.instant();
        String objectKey = objectKey(now, validated.extension());
        StoredFile pending = new StoredFile(
                UUID.randomUUID(),
                businessId,
                category,
                validated.originalFilename(),
                objectKey,
                validated.contentType(),
                validated.extension(),
                validated.content().length,
                sha256(validated.content()),
                scanStatus,
                FileStorageStatus.UPLOADING,
                uploadedByUserId,
                now,
                null);
        files.save(pending);

        boolean objectWritten = false;
        try {
            objectStorage.put(objectKey, validated.content(), validated.contentType());
            objectWritten = true;
            if (!files.markAvailable(pending.id(), now)) {
                throw StorageException.fileUnavailable();
            }
            return files.findByIdAndBusinessId(pending.id(), businessId).orElseThrow(StorageException::fileNotFound);
        } catch (RuntimeException failure) {
            if (objectWritten) {
                deleteQuietly(objectKey);
            }
            files.markFailed(pending.id());
            if (failure instanceof StorageException storageFailure) {
                throw storageFailure;
            }
            throw StorageException.storageUnavailable(failure);
        }
    }

    @Transactional(readOnly = true)
    public StoredFile getMetadata(UUID businessId, UUID fileId) {
        return files.findByIdAndBusinessId(fileId, businessId).orElseThrow(StorageException::fileNotFound);
    }

    @Transactional(readOnly = true)
    public DownloadAccess createDownloadAccess(UUID businessId, UUID fileId) {
        StoredFile file = getMetadata(businessId, fileId);
        if (file.storageStatus() != FileStorageStatus.AVAILABLE || file.scanStatus() != FileScanStatus.CLEAN) {
            throw StorageException.fileUnavailable();
        }
        URI downloadUrl = objectStorage.presignDownload(file.objectKey(), properties.downloadTtl());
        return new DownloadAccess(downloadUrl, clock.instant().plus(properties.downloadTtl()));
    }

    private void deleteQuietly(String objectKey) {
        try {
            objectStorage.delete(objectKey);
        } catch (RuntimeException ignored) {
            // The failed metadata row remains available to a later orphan cleanup job.
        }
    }

    private static String objectKey(Instant now, String extension) {
        return "objects/" + KEY_DATE.format(now) + "/" + UUID.randomUUID() + "." + extension;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record DownloadAccess(URI url, Instant expiresAt) {}
}
