package za.co.trademesh.modules.business.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.modules.business.application.BusinessException;

@RestControllerAdvice(assignableTypes = BusinessController.class)
public class BusinessExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusinessException(BusinessException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Business onboarding request failed");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
