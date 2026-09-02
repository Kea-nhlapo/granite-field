package za.co.trademesh.shared.storage.support;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import za.co.trademesh.shared.storage.ObjectStorage;
import za.co.trademesh.shared.storage.StorageException;

public class InMemoryObjectStorage implements ObjectStorage {

    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
    private volatile boolean failWrites;

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        if (failWrites) {
            throw StorageException.storageUnavailable(new IllegalStateException("test write failure"));
        }
        objects.put(objectKey, new StoredObject(Arrays.copyOf(content, content.length), contentType));
    }

    @Override
    public void delete(String objectKey) {
        objects.remove(objectKey);
    }

    @Override
    public URI presignDownload(String objectKey, Duration timeToLive) {
        if (!objects.containsKey(objectKey)) {
            throw StorageException.fileNotFound();
        }
        return URI.create("https://storage.test/download/" + objectKey + "?ttl=" + timeToLive.toSeconds());
    }

    public byte[] content(String objectKey) {
        StoredObject object = objects.get(objectKey);
        return object == null ? null : Arrays.copyOf(object.content(), object.content().length);
    }

    public int size() {
        return objects.size();
    }

    public void failWrites(boolean failWrites) {
        this.failWrites = failWrites;
    }

    public void clear() {
        objects.clear();
        failWrites = false;
    }

    private record StoredObject(byte[] content, String contentType) {}
}
