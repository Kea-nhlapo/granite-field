package za.co.trademesh.modules.routing.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.modules.routing.application.RoutingException;

@RestControllerAdvice(assignableTypes = RoutingController.class)
public class RoutingExceptionHandler {

    @ExceptionHandler(RoutingException.class)
    ProblemDetail handle(RoutingException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Route calculation failed");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
