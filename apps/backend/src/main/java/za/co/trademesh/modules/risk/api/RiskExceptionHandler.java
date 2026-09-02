package za.co.trademesh.modules.risk.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.modules.risk.application.RiskException;

@RestControllerAdvice(assignableTypes = RiskController.class)
public class RiskExceptionHandler {

    @ExceptionHandler(RiskException.class)
    ProblemDetail handle(RiskException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Risk request failed");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
