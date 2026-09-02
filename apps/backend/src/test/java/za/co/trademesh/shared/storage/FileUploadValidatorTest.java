package za.co.trademesh.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FileUploadValidatorTest {

    private final ObjectStorageProperties properties =
            new ObjectStorageProperties("endpoint", "key", "secret", "bucket", 8, Duration.ofMinutes(5), null);
    private final FileUploadValidator validator = new FileUploadValidator(properties);

    @Test
    void acceptsKnownContentExtensionsAndMagicBytes() {
        byte[] pdf = "%PDF-1".getBytes(StandardCharsets.US_ASCII);

        var validated = validator.validate("quote.PDF", "Application/PDF; charset=binary", pdf);

        assertThat(validated.contentType()).isEqualTo("application/pdf");
        assertThat(validated.extension()).isEqualTo("pdf");
        assertThat(validated.content()).containsExactly(pdf);
    }

    @Test
    void enforcesTheConfiguredByteLimit() {
        byte[] tooLarge = "%PDF-1234".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> validator.validate("quote.pdf", "application/pdf", tooLarge))
                .isInstanceOf(StorageException.class)
                .extracting(exception -> ((StorageException) exception).code())
                .isEqualTo("FILE_TOO_LARGE");
    }

    @Test
    void rejectsPathLikeNamesAndFalseContentDeclarations() {
        byte[] pdf = "%PDF-1".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> validator.validate("folder/quote.pdf", "application/pdf", pdf))
                .isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> validator.validate("quote.png", "image/png", pdf))
                .isInstanceOf(StorageException.class)
                .extracting(exception -> ((StorageException) exception).code())
                .isEqualTo("FILE_SIGNATURE_MISMATCH");
    }
}
