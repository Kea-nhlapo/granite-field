package za.co.trademesh.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        MethodArgumentTypeMismatchException.class,
        MissingRequestHeaderException.class,
        MissingServletRequestParameterException.class,
        HttpMessageNotReadableException.class
    })
    ProblemDetail invalidRequest(Exception ignored) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                "The request is missing required data or contains an invalid value.",
                "INVALID_REQUEST");
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    ProblemDetail uploadTooLarge(Exception ignored) {
        return problem(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Request is too large",
                "The request exceeds the configured upload limit.",
                "REQUEST_TOO_LARGE");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail notFound(NoResourceFoundException ignored) {
        return problem(
                HttpStatus.NOT_FOUND, "Resource not found", "The requested resource was not found.", "NOT_FOUND");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail methodNotAllowed(HttpRequestMethodNotSupportedException ignored) {
        return problem(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Method not allowed",
                "The requested method is not supported for this resource.",
                "METHOD_NOT_ALLOWED");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied(AccessDeniedException ignored) {
        return problem(
                HttpStatus.FORBIDDEN,
                "Access denied",
                "This account is not allowed to perform the requested operation.",
                "ACCESS_DENIED");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception failure, HttpServletRequest request) {
        // Deliberately do not log the exception message, request path, headers,
        // body, query string, or stack trace. Any of those may hold credentials,
        // invitation tokens, identity data, document text, or GPS coordinates.
        log.error(
                "Unhandled API failure requestId={} exceptionType={}",
                RequestContext.requestId(request),
                failure.getClass().getName());
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Request could not be completed",
                "An unexpected error occurred. Use the request ID when asking for help.",
                "INTERNAL_ERROR");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
