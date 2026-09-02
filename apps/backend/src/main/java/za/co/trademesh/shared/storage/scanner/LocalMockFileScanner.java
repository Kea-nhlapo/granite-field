package za.co.trademesh.shared.storage.scanner;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import za.co.trademesh.shared.storage.FileScanStatus;
import za.co.trademesh.shared.storage.FileScanner;

@Component
@Profile("local")
class LocalMockFileScanner implements FileScanner {

    @Override
    public FileScanStatus scan(String filename, String contentType, byte[] content) {
        return FileScanStatus.CLEAN;
    }
}
