package za.co.trademesh.shared.storage;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class FileUploadValidator {

    private static final int MAX_FILENAME_LENGTH = 255;
    private static final Map<String, Set<String>> ALLOWED_EXTENSIONS = Map.of(
            "application/pdf", Set.of("pdf"),
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"));

    private final ObjectStorageProperties properties;

    public FileUploadValidator(ObjectStorageProperties properties) {
        this.properties = properties;
    }

    public ValidatedFile validate(String originalFilename, String declaredContentType, byte[] content) {
        validateFilename(originalFilename);
        if (content == null || content.length == 0) {
            throw StorageException.emptyFile();
        }
        if (content.length > properties.maxUploadBytes()) {
            throw StorageException.fileTooLarge(properties.maxUploadBytes());
        }

        String contentType = normalizeContentType(declaredContentType);
        Set<String> extensions = ALLOWED_EXTENSIONS.get(contentType);
        if (extensions == null) {
            throw StorageException.unsupportedContentType();
        }

        String extension = extensionOf(originalFilename);
        if (!extensions.contains(extension)) {
            throw StorageException.extensionMismatch();
        }
        if (!signatureMatches(contentType, content)) {
            throw StorageException.signatureMismatch();
        }
        return new ValidatedFile(originalFilename, contentType, extension, Arrays.copyOf(content, content.length));
    }

    private static void validateFilename(String filename) {
        if (filename == null
                || filename.isBlank()
                || filename.length() > MAX_FILENAME_LENGTH
                || !filename.equals(filename.strip())
                || filename.contains("/")
                || filename.contains("\\")
                || filename.contains("..")
                || filename.chars().anyMatch(Character::isISOControl)) {
            throw StorageException.invalidFilename();
        }
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            throw StorageException.unsupportedContentType();
        }
        int parameters = contentType.indexOf(';');
        String value = parameters >= 0 ? contentType.substring(0, parameters) : contentType;
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            throw StorageException.extensionMismatch();
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean signatureMatches(String contentType, byte[] content) {
        return switch (contentType) {
            case "application/pdf" -> startsWith(content, new byte[] {'%', 'P', 'D', 'F', '-'});
            case "image/jpeg" -> startsWith(content, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
            case "image/png" ->
                startsWith(
                        content,
                        new byte[] {(byte) 0x89, 'P', 'N', 'G', (byte) 0x0d, (byte) 0x0a, (byte) 0x1a, (byte) 0x0a});
            default -> false;
        };
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    public record ValidatedFile(String originalFilename, String contentType, String extension, byte[] content) {
        public ValidatedFile {
            content = Arrays.copyOf(content, content.length);
        }

        @Override
        public byte[] content() {
            return Arrays.copyOf(content, content.length);
        }
    }
}
