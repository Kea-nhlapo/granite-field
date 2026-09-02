package za.co.trademesh.modules.document.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.modules.document.application.DocumentException;

@RestControllerAdvice(assignableTypes = DocumentController.class)
public class DocumentExceptionHandler {

    @ExceptionHandler(DocumentException.class)
    ProblemDetail handle(DocumentException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Document request failed");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
