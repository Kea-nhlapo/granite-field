package za.co.trademesh.modules.transport.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.modules.transport.application.TransportException;

@RestControllerAdvice(assignableTypes = TransportController.class)
public class TransportExceptionHandler {

    @ExceptionHandler(TransportException.class)
    ProblemDetail handle(TransportException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Transport request failed");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
