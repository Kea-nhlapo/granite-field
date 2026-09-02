package za.co.trademesh.modules.evidence.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import za.co.trademesh.modules.evidence.domain.EvidenceDraft;

@Component
public class EvidenceCanonicalizer {

    private final ObjectMapper objectMapper;

    public EvidenceCanonicalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String checksum(EvidenceDraft draft) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ledgerSequence", draft.ledgerSequence());
        payload.put("id", draft.id().toString());
        payload.put("eventId", draft.eventId().toString());
        payload.put("type", draft.type());
        payload.put("subjectType", draft.subjectType());
        payload.put("subjectId", draft.subjectId().toString());
        payload.put("shipmentId", value(draft.shipmentId()));
        payload.put("occurredAt", draft.occurredAt().toString());
        payload.put("actor", draft.actor());
        payload.put("source", draft.source());
        payload.put("correlationId", draft.correlationId().toString());
        payload.put("schemaVersion", draft.schemaVersion());
        payload.put("correctionOfId", value(draft.correctionOfId()));
        payload.put("metadata", new TreeMap<>(draft.metadata()));

        var files = new ArrayList<>(draft.files());
        files.sort(Comparator.comparing(file -> file.fileId().toString()));
        payload.put(
                "files",
                files.stream()
                        .map(file -> {
                            Map<String, String> canonicalFile = new LinkedHashMap<>();
                            canonicalFile.put("fileId", file.fileId().toString());
                            canonicalFile.put("sha256", file.sha256());
                            return canonicalFile;
                        })
                        .toList());
        payload.put("recordedAt", draft.recordedAt().toString());

        byte[] canonicalJson = objectMapper.writeValueAsBytes(payload);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalJson));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String value(Object value) {
        return value == null ? null : value.toString();
    }
}
