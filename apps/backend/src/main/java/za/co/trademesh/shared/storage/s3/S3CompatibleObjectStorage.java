package za.co.trademesh.shared.storage.s3;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http.Method;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import za.co.trademesh.shared.storage.ObjectStorage;
import za.co.trademesh.shared.storage.ObjectStorageProperties;
import za.co.trademesh.shared.storage.StorageException;

@Component
class S3CompatibleObjectStorage implements ObjectStorage {

    private final ObjectStorageProperties properties;
    private volatile MinioClient client;
    private volatile boolean bucketReady;

    S3CompatibleObjectStorage(ObjectStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        try {
            ensureBucket();
            client().putObject(
                            PutObjectArgs.builder()
                                    .bucket(properties.bucket())
                                    .object(objectKey)
                                    .contentType(contentType)
                                    .stream(new ByteArrayInputStream(content), (long) content.length, -1L)
                                    .build());
        } catch (Exception failure) {
            throw StorageException.storageUnavailable(failure);
        }
    }

    @Override
    public byte[] get(String objectKey) {
        try (var content = client().getObject(GetObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectKey)
                .build())) {
            return content.readAllBytes();
        } catch (Exception failure) {
            throw StorageException.storageUnavailable(failure);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client().removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception failure) {
            throw StorageException.storageUnavailable(failure);
        }
    }

    @Override
    public URI presignDownload(String objectKey, Duration timeToLive) {
        try {
            String url = client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .expiry(Math.toIntExact(timeToLive.toSeconds()), TimeUnit.SECONDS)
                    .build());
            return URI.create(url);
        } catch (Exception failure) {
            throw StorageException.storageUnavailable(failure);
        }
    }

    /**
     * Whether the configured bucket answers with the configured credentials. Used by the health
     * indicator, so it must not create anything and must not be cached.
     */
    boolean bucketReachable() throws Exception {
        return client().bucketExists(
                        BucketExistsArgs.builder().bucket(properties.bucket()).build());
    }

    private void ensureBucket() throws Exception {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            boolean exists = client().bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.bucket())
                    .build());
            if (!exists) {
                try {
                    client().makeBucket(MakeBucketArgs.builder()
                            .bucket(properties.bucket())
                            .build());
                } catch (Exception concurrentCreate) {
                    if (!client().bucketExists(BucketExistsArgs.builder()
                            .bucket(properties.bucket())
                            .build())) {
                        throw concurrentCreate;
                    }
                }
            }
            bucketReady = true;
        }
    }

    private MinioClient client() {
        MinioClient current = client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (client == null) {
                properties.requireConfigured();
                var builder = MinioClient.builder()
                        .endpoint(properties.endpoint())
                        .credentials(properties.accessKey(), properties.secretKey());
                // AWS S3 in an opt-in region rejects requests signed for a region it
                // cannot negotiate, so pin it when the deployment supplies one.
                if (properties.region() != null) {
                    builder = builder.region(properties.region());
                }
                client = builder.build();
            }
            return client;
        }
    }
}
