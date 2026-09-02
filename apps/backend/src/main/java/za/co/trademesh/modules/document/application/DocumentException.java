package za.co.trademesh.modules.document.application;

import org.springframework.http.HttpStatus;

public class DocumentException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private DocumentException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static DocumentException notFound() {
        return new DocumentException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "The document was not found");
    }

    public static DocumentException sourceFileUnavailable() {
        return new DocumentException(
                HttpStatus.CONFLICT,
                "DOCUMENT_SOURCE_UNAVAILABLE",
                "The source file must be clean and available before it can be processed");
    }

    public static DocumentException sourceFileNotFound() {
        return new DocumentException(
                HttpStatus.NOT_FOUND, "DOCUMENT_SOURCE_NOT_FOUND", "The source file was not found");
    }

    public static DocumentException requestConflict() {
        return new DocumentException(
                HttpStatus.CONFLICT,
                "DOCUMENT_REQUEST_CONFLICT",
                "The request identifier has already been used for different document data");
    }

    public static DocumentException sourceAlreadyRegistered() {
        return new DocumentException(
                HttpStatus.CONFLICT,
                "DOCUMENT_SOURCE_ALREADY_REGISTERED",
                "The source file is already registered as a document");
    }

    public static DocumentException notReadyForConfirmation() {
        return new DocumentException(
                HttpStatus.CONFLICT,
                "DOCUMENT_NOT_READY_FOR_CONFIRMATION",
                "The document must be parsed before its fields can be confirmed");
    }

    public static DocumentException invalidConfirmation() {
        return new DocumentException(
                HttpStatus.BAD_REQUEST,
                "INVALID_DOCUMENT_CONFIRMATION",
                "Confirmation fields must contain unique, non-empty paths and values");
    }

    public static DocumentException processingNotReady() {
        return new DocumentException(
                HttpStatus.CONFLICT,
                "DOCUMENT_PROCESSING_NOT_READY",
                "The document is not ready to be claimed for processing");
    }

    public static DocumentException invalidProviderResult() {
        return new DocumentException(
                HttpStatus.BAD_GATEWAY,
                "INVALID_EXTRACTION_RESULT",
                "The extraction provider returned an invalid result");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
