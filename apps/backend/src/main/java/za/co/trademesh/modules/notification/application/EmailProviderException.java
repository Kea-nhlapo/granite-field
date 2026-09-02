package za.co.trademesh.modules.notification.application;

public class EmailProviderException extends Exception {

    private final String code;
    private final boolean retryable;

    public EmailProviderException(String code, String safeMessage, boolean retryable) {
        super(safeMessage);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
