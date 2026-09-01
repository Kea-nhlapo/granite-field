package za.co.trademesh.modules.routing.domain;

/**
 * The single failure type callers see. Adapter-specific exceptions are wrapped
 * so no provider detail — vendor names, URLs, credentials in a message — reaches
 * the domain or the public API.
 */
public class RouteProviderException extends RuntimeException {

    public RouteProviderException(String message) {
        super(message);
    }

    public RouteProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
