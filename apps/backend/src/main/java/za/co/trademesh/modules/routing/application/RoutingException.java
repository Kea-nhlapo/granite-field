package za.co.trademesh.modules.routing.application;

import org.springframework.http.HttpStatus;

public class RoutingException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private RoutingException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    static RoutingException invalidRequest() {
        return new RoutingException(
                HttpStatus.BAD_REQUEST,
                "INVALID_ROUTE_REQUEST",
                "The route points, vehicle limits, or avoidance options are invalid.");
    }

    static RoutingException calculationNotFound() {
        return new RoutingException(
                HttpStatus.NOT_FOUND, "ROUTE_CALCULATION_NOT_FOUND", "The route calculation was not found.");
    }

    static RoutingException requestConflict() {
        return new RoutingException(
                HttpStatus.CONFLICT,
                "ROUTE_REQUEST_CONFLICT",
                "The request ID has already been used with different route input.");
    }

    static RoutingException providerUnavailable() {
        return new RoutingException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ROUTE_PROVIDER_UNAVAILABLE",
                "No route provider is currently available. Try again later.");
    }

    static RoutingException invalidProviderResult() {
        return new RoutingException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_ROUTE_PROVIDER_RESULT",
                "The route provider returned data that could not be used safely.");
    }
}
