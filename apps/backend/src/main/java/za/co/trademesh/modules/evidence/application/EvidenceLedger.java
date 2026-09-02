package za.co.trademesh.modules.evidence.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.evidence.domain.EvidenceDraft;
import za.co.trademesh.modules.evidence.domain.EvidenceFileReference;
import za.co.trademesh.modules.evidence.domain.EvidenceRecord;
import za.co.trademesh.modules.evidence.domain.EvidenceRepository;
import za.co.trademesh.shared.events.PublishedEvent;
import za.co.trademesh.shared.storage.FileScanStatus;
import za.co.trademesh.shared.storage.FileStorageStatus;
import za.co.trademesh.shared.storage.StoredFile;
import za.co.trademesh.shared.storage.StoredFileRepository;

@Service
public class EvidenceLedger implements ShipmentEvidenceCatalog {

    private static final Pattern TYPE = Pattern.compile("[A-Za-z0-9._-]{1,128}");
    private static final Pattern SUBJECT_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern METADATA_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final int MAX_METADATA_ENTRIES = 50;
    private static final int MAX_METADATA_VALUE = 500;
    private static final int MAX_FILES = 20;

    private final EvidenceRepository evidence;
    private final StoredFileRepository storedFiles;
    private final EvidenceCanonicalizer canonicalizer;
    private final EvidenceChainStrategy chainStrategy;
    private final Clock clock;

    public EvidenceLedger(
            EvidenceRepository evidence,
            StoredFileRepository storedFiles,
            EvidenceCanonicalizer canonicalizer,
            EvidenceChainStrategy chainStrategy,
            Clock clock) {
        this.evidence = evidence;
        this.storedFiles = storedFiles;
        this.canonicalizer = canonicalizer;
        this.chainStrategy = chainStrategy;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public EvidenceRecord record(PublishedEvent<?> published, EvidenceProjection projection) {
        Optional<EvidenceRecord> existing =
                evidence.findByEventId(published.envelope().eventId());
        if (existing.isPresent()) {
            return existing.get();
        }
        return append(
                published.envelope().eventId(),
                published.envelope().type(),
                projection.subjectType(),
                projection.subjectId(),
                projection.shipmentId(),
                published.envelope().occurredAt(),
                published.envelope().actor().orElse(null),
                published.envelope().source(),
                published.envelope().correlationId(),
                published.envelope().schemaVersion(),
                null,
                projection.metadata(),
                projection.files());
    }

    @Transactional
    public EvidenceRecord correct(EvidenceCorrection correction) {
        if (correction == null || correction.originalEvidenceId() == null) {
            throw EvidenceException.invalid("A correction must identify the original evidence");
        }
        EvidenceRecord original =
                evidence.findById(correction.originalEvidenceId()).orElseThrow(EvidenceException::originalNotFound);
        Optional<EvidenceRecord> existing = evidence.findByEventId(correction.eventId());
        if (existing.isPresent()) {
            return existing.get();
        }
        return append(
                correction.eventId(),
                correction.type(),
                original.subjectType(),
                original.subjectId(),
                original.shipmentId(),
                correction.occurredAt(),
                correction.actor(),
                correction.source(),
                correction.correlationId(),
                correction.schemaVersion(),
                original.id(),
                correction.metadata(),
                correction.files());
    }

    @Override
    @Transactional(readOnly = true)
    public ShipmentEvidencePackage packageFor(UUID shipmentId) {
        if (shipmentId == null) {
            throw EvidenceException.invalid("A shipment is required");
        }
        List<Entry> entries =
                evidence.findByShipmentId(shipmentId).stream().map(this::entry).toList();
        return new ShipmentEvidencePackage(shipmentId, entries);
    }

    private EvidenceRecord append(
            UUID eventId,
            String type,
            String subjectType,
            UUID subjectId,
            UUID shipmentId,
            Instant occurredAt,
            String actor,
            String source,
            UUID correlationId,
            int schemaVersion,
            UUID correctionOfId,
            Map<String, String> requestedMetadata,
            List<EvidenceFile> requestedFiles) {
        require(eventId != null, "An event ID is required");
        require(TYPE.matcher(text(type)).matches(), "The evidence type is invalid");
        require(SUBJECT_TYPE.matcher(text(subjectType)).matches(), "The subject type is invalid");
        require(subjectId != null, "A subject ID is required");
        require(occurredAt != null, "An occurrence time is required");
        require(TYPE.matcher(text(source)).matches(), "The source is invalid");
        require(correlationId != null, "A correlation ID is required");
        require(schemaVersion > 0, "The schema version must be positive");
        require(actor == null || actor.length() <= 255, "The actor is too long");

        Map<String, String> metadata = normalizeMetadata(requestedMetadata);
        List<EvidenceFileReference> files = normalizeFiles(requestedFiles);
        Instant databaseOccurredAt = occurredAt.truncatedTo(ChronoUnit.MICROS);
        Instant recordedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        EvidenceDraft draft = new EvidenceDraft(
                evidence.nextSequence(),
                UUID.randomUUID(),
                eventId,
                type,
                subjectType,
                subjectId,
                shipmentId,
                databaseOccurredAt,
                actor,
                source,
                correlationId,
                schemaVersion,
                correctionOfId,
                metadata,
                files,
                recordedAt);
        String checksum = canonicalizer.checksum(draft);
        return evidence.append(draft, checksum, chainStrategy.link(shipmentId, checksum))
                .record();
    }

    private Map<String, String> normalizeMetadata(Map<String, String> requested) {
        Map<String, String> metadata = requested == null ? Map.of() : requested;
        require(metadata.size() <= MAX_METADATA_ENTRIES, "There are too many metadata fields");
        Map<String, String> normalized = new TreeMap<>();
        metadata.forEach((key, value) -> {
            require(key != null && METADATA_KEY.matcher(key).matches(), "A metadata key is invalid");
            require(value != null && value.length() <= MAX_METADATA_VALUE, "A metadata value is invalid");
            normalized.put(key, value);
        });
        return Map.copyOf(normalized);
    }

    private List<EvidenceFileReference> normalizeFiles(List<EvidenceFile> requested) {
        List<EvidenceFile> files = requested == null ? List.of() : requested;
        require(files.size() <= MAX_FILES, "There are too many evidence files");
        HashSet<UUID> seen = new HashSet<>();
        List<EvidenceFileReference> normalized = new ArrayList<>();
        for (EvidenceFile reference : files) {
            require(reference != null && reference.fileId() != null, "An evidence file ID is required");
            require(
                    reference.sha256() != null
                            && SHA256.matcher(reference.sha256()).matches(),
                    "An evidence file checksum is invalid");
            require(seen.add(reference.fileId()), "An evidence file is duplicated");
            StoredFile stored = storedFiles.findById(reference.fileId()).orElseThrow(EvidenceException::fileNotFound);
            if (!stored.sha256().equals(reference.sha256())) {
                throw EvidenceException.fileChecksumMismatch();
            }
            require(
                    stored.scanStatus() == FileScanStatus.CLEAN
                            && stored.storageStatus() == FileStorageStatus.AVAILABLE,
                    "An evidence file is not available");
            normalized.add(new EvidenceFileReference(reference.fileId(), reference.sha256()));
        }
        normalized.sort(Comparator.comparing(file -> file.fileId().toString()));
        return List.copyOf(normalized);
    }

    private Entry entry(EvidenceRecord record) {
        Integrity integrity = canonicalizer.checksum(record.asDraft()).equals(record.payloadChecksum())
                ? Integrity.VERIFIED
                : Integrity.CHECKSUM_MISMATCH;
        return new Entry(
                record.ledgerSequence(),
                record.id(),
                record.eventId(),
                record.type(),
                record.subjectType(),
                record.subjectId(),
                record.occurredAt(),
                record.actor(),
                record.source(),
                record.correlationId(),
                record.schemaVersion(),
                record.correctionOfId(),
                record.metadata(),
                record.files().stream()
                        .map(file -> new FileReference(file.fileId(), file.sha256()))
                        .toList(),
                record.payloadChecksum(),
                integrity);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw EvidenceException.invalid(message);
        }
    }
}
