package za.co.trademesh.shared.storage;

import org.springframework.http.HttpStatus;

public class StorageException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private StorageException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    private StorageException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public static StorageException invalidFilename() {
        return new StorageException(HttpStatus.BAD_REQUEST, "INVALID_FILE_NAME", "The file name is not allowed");
    }

    public static StorageException emptyFile() {
        return new StorageException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "The uploaded file is empty");
    }

    public static StorageException fileTooLarge(long maximumBytes) {
        return new StorageException(
                HttpStatus.CONTENT_TOO_LARGE,
                "FILE_TOO_LARGE",
                "The file exceeds the " + maximumBytes + " byte upload limit");
    }

    public static StorageException unsupportedContentType() {
        return new StorageException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_FILE_TYPE",
                "Only PDF, JPEG, and PNG files are accepted");
    }

    public static StorageException extensionMismatch() {
        return new StorageException(
                HttpStatus.BAD_REQUEST,
                "FILE_EXTENSION_MISMATCH",
                "The filename extension does not match the declared content type");
    }

    public static StorageException signatureMismatch() {
        return new StorageException(
                HttpStatus.BAD_REQUEST, "FILE_SIGNATURE_MISMATCH", "The file content does not match its declared type");
    }

    public static StorageException scanRejected() {
        return new StorageException(
                HttpStatus.UNPROCESSABLE_CONTENT, "FILE_SCAN_REJECTED", "The file did not pass the safety scan");
    }

    public static StorageException fileNotFound() {
        return new StorageException(HttpStatus.NOT_FOUND, "FILE_NOT_FOUND", "The file was not found");
    }

    public static StorageException fileUnavailable() {
        return new StorageException(
                HttpStatus.CONFLICT, "FILE_NOT_AVAILABLE", "The file is not available for download");
    }

    public static StorageException unreadableUpload(Throwable cause) {
        return new StorageException(
                HttpStatus.BAD_REQUEST, "FILE_UNREADABLE", "The uploaded file could not be read", cause);
    }

    public static StorageException storageNotConfigured(String setting) {
        return new StorageException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "OBJECT_STORAGE_NOT_CONFIGURED",
                "Object storage is not configured; missing " + setting);
    }

    public static StorageException storageUnavailable(Throwable cause) {
        return new StorageException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "OBJECT_STORAGE_UNAVAILABLE",
                "Object storage is temporarily unavailable",
                cause);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
