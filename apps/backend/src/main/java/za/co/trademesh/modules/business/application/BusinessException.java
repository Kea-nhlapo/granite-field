package za.co.trademesh.modules.business.application;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BusinessException invalidRegistrationNumber() {
        return new BusinessException(
                HttpStatus.BAD_REQUEST,
                "INVALID_REGISTRATION_NUMBER",
                "Use a 12-digit South African company registration number");
    }

    public static BusinessException companyNotFound() {
        return new BusinessException(
                HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "The company registry did not return this business");
    }

    public static BusinessException registrationAlreadyOnboarded() {
        return new BusinessException(
                HttpStatus.CONFLICT,
                "REGISTRATION_ALREADY_ONBOARDED",
                "This company registration number is already being onboarded or has been confirmed");
    }

    public static BusinessException onboardingNotFound() {
        return new BusinessException(
                HttpStatus.NOT_FOUND, "ONBOARDING_NOT_FOUND", "The registered-business onboarding was not found");
    }

    public static BusinessException businessNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "BUSINESS_NOT_FOUND", "The business was not found");
    }

    public static BusinessException onboardingAccessDenied() {
        return new BusinessException(
                HttpStatus.FORBIDDEN,
                "ONBOARDING_ACCESS_DENIED",
                "Only the account that started this onboarding may continue it");
    }

    public static BusinessException onboardingStateChanged() {
        return new BusinessException(
                HttpStatus.CONFLICT,
                "ONBOARDING_STATE_CHANGED",
                "The onboarding changed while the confirmation was being processed");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
