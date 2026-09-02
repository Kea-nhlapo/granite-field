package za.co.trademesh.modules.telemetry.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.modules.telemetry.application.TelemetryException;

@RestControllerAdvice(assignableTypes = TelemetryController.class)
public class TelemetryExceptionHandler {

    @ExceptionHandler(TelemetryException.class)
    ProblemDetail handle(TelemetryException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Telemetry request failed");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
