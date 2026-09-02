package za.co.trademesh.shared.storage;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository {

    void save(StoredFile file);

    boolean markAvailable(UUID fileId, Instant storedAt);

    void markFailed(UUID fileId);

    Optional<StoredFile> findByIdAndBusinessId(UUID fileId, UUID businessId);
}
