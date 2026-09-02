package za.co.trademesh.modules.evidence.application;

public final class EvidenceException extends RuntimeException {

    private final String code;

    private EvidenceException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }

    static EvidenceException invalid(String message) {
        return new EvidenceException("INVALID_EVIDENCE", message);
    }

    static EvidenceException originalNotFound() {
        return new EvidenceException("EVIDENCE_NOT_FOUND", "The evidence being corrected does not exist");
    }

    static EvidenceException fileNotFound() {
        return new EvidenceException("EVIDENCE_FILE_NOT_FOUND", "An evidence file does not exist");
    }

    static EvidenceException fileChecksumMismatch() {
        return new EvidenceException("EVIDENCE_FILE_CHECKSUM_MISMATCH", "An evidence file checksum does not match");
    }
}
