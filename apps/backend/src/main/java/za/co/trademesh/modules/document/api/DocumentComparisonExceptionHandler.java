package za.co.trademesh.modules.document.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.modules.document.application.DocumentComparisonException;

@RestControllerAdvice(assignableTypes = DocumentComparisonController.class)
public class DocumentComparisonExceptionHandler {

    @ExceptionHandler(DocumentComparisonException.class)
    ProblemDetail handle(DocumentComparisonException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Document comparison failed");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
