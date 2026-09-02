package za.co.trademesh.modules.insurance.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.modules.insurance.application.InsuranceException;

@RestControllerAdvice(assignableTypes = InsuranceController.class)
public class InsuranceExceptionHandler {

    @ExceptionHandler(InsuranceException.class)
    ProblemDetail handle(InsuranceException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Insurance request failed");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
