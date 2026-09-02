package za.co.trademesh.modules.aggregation.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.modules.aggregation.application.DemandAggregationException;

@RestControllerAdvice(assignableTypes = DemandAggregationController.class)
public class DemandAggregationExceptionHandler {

    @ExceptionHandler(DemandAggregationException.class)
    ProblemDetail handle(DemandAggregationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Demand aggregation request failed");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
