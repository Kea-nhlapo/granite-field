package za.co.trademesh.modules.payment.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.modules.payment.application.EscrowException;

@RestControllerAdvice
public class EscrowExceptionHandler {

    @ExceptionHandler(EscrowException.class)
    ProblemDetail handle(EscrowException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Escrow request failed");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
