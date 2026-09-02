package za.co.trademesh.modules.access.api;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import za.co.trademesh.modules.access.application.AccessException;
import za.co.trademesh.modules.access.application.OtpProviderException;
import za.co.trademesh.modules.payment.application.MomoException;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler(AccessException.class)
    ProblemDetail handleAccessException(AccessException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle("Authentication request failed");
        problem.setProperty("code", exception.code());
        return problem;
    }

    @ExceptionHandler(OtpProviderException.class)
    ProblemDetail handleOtpProviderException(OtpProviderException exception) {
        return externalProblem(exception.retryable(), exception.code());
    }

    @ExceptionHandler(MomoException.class)
    ProblemDetail handleMomoException(MomoException exception) {
        return externalProblem(exception.retryable(), exception.code());
    }

    private static ProblemDetail externalProblem(boolean retryable, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                retryable
                        ? org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE
                        : org.springframework.http.HttpStatus.BAD_GATEWAY,
                "An external identity provider could not complete the request");
        problem.setTitle("External identity provider failed");
        problem.setProperty("code", code);
        return problem;
    }
}
