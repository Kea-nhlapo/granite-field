package za.co.trademesh.shared.storage.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.shared.storage.StorageException;

@RestControllerAdvice(assignableTypes = FileStorageController.class)
public class FileStorageExceptionHandler {

    @ExceptionHandler(StorageException.class)
    ProblemDetail handleStorageException(StorageException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("File request failed");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
