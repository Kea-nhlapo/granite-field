package za.co.trademesh.shared.storage;

import java.net.URI;
import java.time.Duration;

public interface ObjectStorage {

    void put(String objectKey, byte[] content, String contentType);

    void delete(String objectKey);

    URI presignDownload(String objectKey, Duration timeToLive);
}
