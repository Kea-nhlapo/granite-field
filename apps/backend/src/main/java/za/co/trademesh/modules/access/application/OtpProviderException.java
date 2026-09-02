package za.co.trademesh.modules.access.application;

public class OtpProviderException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public OtpProviderException(String code, String message, boolean retryable) {
        super(message);
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
