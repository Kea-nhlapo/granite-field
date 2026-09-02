package za.co.trademesh.shared.storage.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.shared.storage.FileCategory;
import za.co.trademesh.shared.storage.FileScanStatus;
import za.co.trademesh.shared.storage.FileStorageStatus;
import za.co.trademesh.shared.storage.StoredFile;
import za.co.trademesh.shared.storage.StoredFileRepository;

@Repository
class JdbcStoredFileRepository implements StoredFileRepository {

    private final JdbcTemplate jdbcTemplate;

    JdbcStoredFileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(StoredFile file) {
        jdbcTemplate.update(
                """
            INSERT INTO stored_file (
                id, business_id, category, original_filename, object_key,
                content_type, extension, size_bytes, sha256, scan_status,
                storage_status, uploaded_by_user_id, created_at, stored_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                file.id(),
                file.businessId(),
                file.category().name(),
                file.originalFilename(),
                file.objectKey(),
                file.contentType(),
                file.extension(),
                file.sizeBytes(),
                file.sha256(),
                file.scanStatus().name(),
                file.storageStatus().name(),
                file.uploadedByUserId(),
                time(file.createdAt()),
                time(file.storedAt()));
    }

    @Override
    public boolean markAvailable(UUID fileId, Instant storedAt) {
        return jdbcTemplate.update("""
            UPDATE stored_file
            SET storage_status = 'AVAILABLE', stored_at = ?
            WHERE id = ? AND storage_status = 'UPLOADING'
            """, time(storedAt), fileId) == 1;
    }

    @Override
    public void markFailed(UUID fileId) {
        jdbcTemplate.update("""
            UPDATE stored_file
            SET storage_status = 'FAILED', stored_at = NULL
            WHERE id = ? AND storage_status = 'UPLOADING'
            """, fileId);
    }

    @Override
    public Optional<StoredFile> findByIdAndBusinessId(UUID fileId, UUID businessId) {
        return one("""
            SELECT id, business_id, category, original_filename, object_key,
                   content_type, extension, size_bytes, sha256, scan_status,
                   storage_status, uploaded_by_user_id, created_at, stored_at
            FROM stored_file
            WHERE id = ? AND business_id = ?
            """, fileId, businessId);
    }

    @Override
    public Optional<StoredFile> findById(UUID fileId) {
        return one("""
            SELECT id, business_id, category, original_filename, object_key,
                   content_type, extension, size_bytes, sha256, scan_status,
                   storage_status, uploaded_by_user_id, created_at, stored_at
            FROM stored_file
            WHERE id = ?
            """, fileId);
    }

    private Optional<StoredFile> one(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapFile, parameters).stream().findFirst();
    }

    private StoredFile mapFile(ResultSet resultSet, int rowNumber) throws SQLException {
        return new StoredFile(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("business_id", UUID.class),
                FileCategory.valueOf(resultSet.getString("category")),
                resultSet.getString("original_filename"),
                resultSet.getString("object_key"),
                resultSet.getString("content_type"),
                resultSet.getString("extension"),
                resultSet.getLong("size_bytes"),
                resultSet.getString("sha256"),
                FileScanStatus.valueOf(resultSet.getString("scan_status")),
                FileStorageStatus.valueOf(resultSet.getString("storage_status")),
                resultSet.getObject("uploaded_by_user_id", UUID.class),
                instant(resultSet, "created_at"),
                instant(resultSet, "stored_at"));
    }

    private static OffsetDateTime time(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
