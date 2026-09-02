package za.co.trademesh.shared.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.storage")
public record ObjectStorageProperties(
        String endpoint, String accessKey, String secretKey, String bucket, long maxUploadBytes, Duration downloadTtl) {

    private static final long DEFAULT_MAX_UPLOAD_BYTES = 10L * 1024L * 1024L;
    private static final Duration DEFAULT_DOWNLOAD_TTL = Duration.ofMinutes(5);
    private static final Duration MINIMUM_DOWNLOAD_TTL = Duration.ofSeconds(1);
    private static final Duration MAXIMUM_DOWNLOAD_TTL = Duration.ofMinutes(15);

    public ObjectStorageProperties {
        if (bucket == null || bucket.isBlank()) {
            bucket = "trademesh";
        }
        if (maxUploadBytes <= 0) {
            maxUploadBytes = DEFAULT_MAX_UPLOAD_BYTES;
        }
        if (downloadTtl == null || downloadTtl.compareTo(MINIMUM_DOWNLOAD_TTL) < 0) {
            downloadTtl = DEFAULT_DOWNLOAD_TTL;
        }
        if (downloadTtl.compareTo(MAXIMUM_DOWNLOAD_TTL) > 0) {
            downloadTtl = MAXIMUM_DOWNLOAD_TTL;
        }
    }

    public void requireConfigured() {
        requireValue(endpoint, "OBJECT_STORAGE_ENDPOINT");
        requireValue(accessKey, "OBJECT_STORAGE_ACCESS_KEY");
        requireValue(secretKey, "OBJECT_STORAGE_SECRET_KEY");
    }

    private static void requireValue(String value, String setting) {
        if (value == null || value.isBlank() || value.startsWith("${")) {
            throw StorageException.storageNotConfigured(setting);
        }
    }

    @Override
    public String toString() {
        return "ObjectStorageProperties[endpoint=" + endpoint + ", accessKey=<redacted>, secretKey=<redacted>, bucket="
                + bucket + ", maxUploadBytes=" + maxUploadBytes + ", downloadTtl=" + downloadTtl + "]";
    }
}
