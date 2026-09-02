package za.co.trademesh.modules.notification.application;

public final class MobileProviderException extends RuntimeException {

    private final String code;
    private final FailureKind kind;

    public MobileProviderException(String code, String safeMessage, FailureKind kind, Throwable cause) {
        super(safeMessage, cause);
        this.code = code;
        this.kind = kind;
    }

    public MobileProviderException(String code, String safeMessage, FailureKind kind) {
        this(code, safeMessage, kind, null);
    }

    public String code() {
        return code;
    }

    public FailureKind kind() {
        return kind;
    }

    public enum FailureKind {
        RETRYABLE,
        PERMANENT,
        SUBMISSION_UNKNOWN
    }
}
