package za.co.trademesh.modules.delivery.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.modules.delivery.application.DeliveryException;

@RestControllerAdvice(assignableTypes = DeliveryController.class)
public class DeliveryExceptionHandler {

    @ExceptionHandler(DeliveryException.class)
    ProblemDetail handle(DeliveryException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Delivery request failed");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
