package za.co.trademesh.shared.storage.scanner;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.shared.storage.FileScanStatus;
import za.co.trademesh.shared.storage.FileScanner;

/**
 * Rejects every upload. This is the default when no scanner provider is configured, so an
 * environment that forgets to choose one refuses uploads rather than accepting unscanned files.
 */
@Component
@ConditionalOnProperty(
        prefix = "trademesh.storage.file-scanner",
        name = "provider",
        havingValue = FileScannerProperties.FAIL_CLOSED,
        matchIfMissing = true)
class FailClosedFileScanner implements FileScanner {

    @Override
    public FileScanStatus scan(String filename, String contentType, byte[] content) {
        return FileScanStatus.ERROR;
    }
}
