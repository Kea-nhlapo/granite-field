package za.co.trademesh.modules.supplier.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.modules.supplier.application.SupplierException;

@RestControllerAdvice(assignableTypes = SupplierController.class)
public class SupplierExceptionHandler {

    @ExceptionHandler(SupplierException.class)
    ProblemDetail handleSupplierException(SupplierException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Supplier invitation request failed");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
