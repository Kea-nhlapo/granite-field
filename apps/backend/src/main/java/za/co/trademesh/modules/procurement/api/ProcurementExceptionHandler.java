package za.co.trademesh.modules.procurement.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.modules.procurement.application.ProcurementException;

@RestControllerAdvice(assignableTypes = ProcurementController.class)
public class ProcurementExceptionHandler {

    @ExceptionHandler(ProcurementException.class)
    ProblemDetail handle(ProcurementException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Procurement request failed");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
