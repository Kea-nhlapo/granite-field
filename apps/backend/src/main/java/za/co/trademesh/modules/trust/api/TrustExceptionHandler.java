package za.co.trademesh.modules.trust.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.modules.trust.application.TrustException;

@RestControllerAdvice(assignableTypes = TrustController.class)
public class TrustExceptionHandler {

    @ExceptionHandler(TrustException.class)
    ProblemDetail handle(TrustException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Trust summary request failed");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
