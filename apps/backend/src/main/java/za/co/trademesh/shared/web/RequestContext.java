package za.co.trademesh.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public final class RequestContext {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    static final String REQUEST_ID_ATTRIBUTE = RequestContext.class.getName() + ".requestId";

    private RequestContext() {}

    public static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        return value instanceof UUID requestId ? requestId.toString() : "unavailable";
    }
}
