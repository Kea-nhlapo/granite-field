package za.co.trademesh.modules.notification.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.modules.notification.application.NotificationException;

@RestControllerAdvice(
        assignableTypes = {
            NotificationContactController.class,
            NotificationPreferenceController.class,
            InfobipWebhookController.class
        })
class NotificationExceptionHandler {

    @ExceptionHandler(NotificationException.class)
    ProblemDetail handle(NotificationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Notification request failed");
        problem.setProperty("code", exception.code());
        return problem;
    }

    @ExceptionHandler(NotificationWebhookException.class)
    ProblemDetail handleWebhook(NotificationWebhookException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Provider callback rejected");
        problem.setProperty("code", exception.code());
        return problem;
    }
}
