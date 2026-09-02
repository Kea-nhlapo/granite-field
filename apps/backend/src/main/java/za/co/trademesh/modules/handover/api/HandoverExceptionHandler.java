package za.co.trademesh.modules.handover.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.modules.handover.application.HandoverException;

@RestControllerAdvice(assignableTypes = {HandoverController.class, DeliveryVerificationController.class})
public class HandoverExceptionHandler {

    @ExceptionHandler(HandoverException.class)
    ProblemDetail handle(HandoverException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Handover request failed");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
