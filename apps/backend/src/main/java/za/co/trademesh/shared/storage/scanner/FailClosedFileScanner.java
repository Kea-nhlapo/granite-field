package za.co.trademesh.shared.storage.scanner;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import za.co.trademesh.shared.storage.FileScanStatus;
import za.co.trademesh.shared.storage.FileScanner;

/** Production stays fail-closed until a real malware-scanning adapter is configured. */
@Component
@Profile("!local")
class FailClosedFileScanner implements FileScanner {

    @Override
    public FileScanStatus scan(String filename, String contentType, byte[] content) {
        return FileScanStatus.ERROR;
    }
}
