package za.co.trademesh.shared.storage.scanner;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.trademesh.shared.storage.FileScanStatus;
import za.co.trademesh.shared.storage.FileScanner;

/**
 * Scans uploads with a clamd daemon over the INSTREAM command.
 *
 * <p>Any transport, protocol, or timeout failure returns {@link FileScanStatus#ERROR} so callers
 * reject the upload. A scanner that cannot reach its daemon must never report CLEAN.
 */
@Component
@ConditionalOnProperty(
        prefix = "trademesh.storage.file-scanner",
        name = "provider",
        havingValue = FileScannerProperties.CLAMAV)
class ClamAvFileScanner implements FileScanner {

    private static final Logger log = LoggerFactory.getLogger(ClamAvFileScanner.class);

    private static final byte[] INSTREAM_COMMAND = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);
    private static final int CHUNK_BYTES = 32 * 1024;
    private static final int MAX_REPLY_BYTES = 1024;

    private final FileScannerProperties.ClamAv settings;

    ClamAvFileScanner(FileScannerProperties properties) {
        this.settings = properties.clamav();
    }

    @Override
    public FileScanStatus scan(String filename, String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            return FileScanStatus.CLEAN;
        }
        int timeoutMillis = (int) Math.min(settings.timeout().toMillis(), Integer.MAX_VALUE);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(settings.host(), settings.port()), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            String reply = exchange(socket, content);
            return interpret(reply, filename);
        } catch (IOException e) {
            log.error("ClamAV scan failed for upload '{}'; rejecting as ERROR", filename, e);
            return FileScanStatus.ERROR;
        }
    }

    private String exchange(Socket socket, byte[] content) throws IOException {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.write(INSTREAM_COMMAND);
        out.flush();
        for (int offset = 0; offset < content.length; offset += CHUNK_BYTES) {
            int length = Math.min(CHUNK_BYTES, content.length - offset);
            out.writeInt(length);
            out.write(content, offset, length);
        }
        out.writeInt(0);
        out.flush();
        return readReply(socket.getInputStream());
    }

    private static String readReply(InputStream in) throws IOException {
        var buffer = new ByteArrayOutputStream();
        int value;
        while ((value = in.read()) != -1) {
            if (value == 0) {
                break;
            }
            buffer.write(value);
            if (buffer.size() > MAX_REPLY_BYTES) {
                throw new IOException("clamd reply exceeded " + MAX_REPLY_BYTES + " bytes");
            }
        }
        return buffer.toString(StandardCharsets.US_ASCII).trim();
    }

    private static FileScanStatus interpret(String reply, String filename) {
        if (reply.endsWith("FOUND")) {
            log.warn("ClamAV reported an infected upload '{}': {}", filename, reply);
            return FileScanStatus.INFECTED;
        }
        if (reply.endsWith("OK")) {
            return FileScanStatus.CLEAN;
        }
        log.error("Unrecognised clamd reply for upload '{}': {}", filename, reply);
        return FileScanStatus.ERROR;
    }
}
