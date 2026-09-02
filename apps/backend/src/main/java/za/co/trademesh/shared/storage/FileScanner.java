package za.co.trademesh.shared.storage;

public interface FileScanner {

    FileScanStatus scan(String filename, String contentType, byte[] content);
}
