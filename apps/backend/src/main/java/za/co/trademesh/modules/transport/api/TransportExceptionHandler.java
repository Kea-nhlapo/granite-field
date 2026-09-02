package za.co.trademesh.modules.transport.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.modules.transport.application.CapacityMatchingException;
import za.co.trademesh.modules.transport.application.TransportException;

@RestControllerAdvice(assignableTypes = {TransportController.class, CapacityMatchingController.class})
public class TransportExceptionHandler {

    @ExceptionHandler(TransportException.class)
    ProblemDetail handle(TransportException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Transport request failed");
        problem.setProperty("code", exception.code());
        return problem;
    }

    @ExceptionHandler(CapacityMatchingException.class)
    ProblemDetail handle(CapacityMatchingException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Capacity matching request failed");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
