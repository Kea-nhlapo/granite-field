package za.co.trademesh.shared.storage.scanner;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.shared.storage.FileScanStatus;
import za.co.trademesh.shared.storage.FileScanner;

/**
 * Accepts every upload without inspecting it. Development convenience only.
 *
 * <p>Selecting this provider outside a developer machine disables malware scanning entirely. It is
 * deliberately not the default: an unset provider resolves to fail-closed.
 */
@Component
@ConditionalOnProperty(
        prefix = "trademesh.storage.file-scanner",
        name = "provider",
        havingValue = FileScannerProperties.MOCK)
class LocalMockFileScanner implements FileScanner {

    @Override
    public FileScanStatus scan(String filename, String contentType, byte[] content) {
        return FileScanStatus.CLEAN;
    }
}
