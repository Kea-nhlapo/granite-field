package za.co.trademesh.modules.routing.application;

public class RouteProviderException extends Exception {

    private final String code;
    private final String safeMessage;
    private final boolean retryable;

    public RouteProviderException(String code, String safeMessage, boolean retryable) {
        super(safeMessage);
        this.code = code;
        this.safeMessage = safeMessage;
        this.retryable = retryable;
    }

    public RouteProviderException(String code, String safeMessage, boolean retryable, Throwable cause) {
        super(safeMessage, cause);
        this.code = code;
        this.safeMessage = safeMessage;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public String safeMessage() {
        return safeMessage;
    }

    public boolean retryable() {
        return retryable;
    }
}
