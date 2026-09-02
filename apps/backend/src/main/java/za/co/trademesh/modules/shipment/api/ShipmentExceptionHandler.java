package za.co.trademesh.modules.shipment.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.modules.shipment.application.ShipmentException;

@RestControllerAdvice(assignableTypes = ShipmentController.class)
public class ShipmentExceptionHandler {

    @ExceptionHandler(ShipmentException.class)
    ProblemDetail handle(ShipmentException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Shipment request failed");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
