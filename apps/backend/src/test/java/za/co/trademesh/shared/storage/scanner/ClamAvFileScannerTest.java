package za.co.trademesh.shared.storage.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import za.co.trademesh.shared.storage.FileScanStatus;

class ClamAvFileScannerTest {

    private static final byte[] PAYLOAD = "upload-bytes".getBytes(StandardCharsets.UTF_8);

    @Test
    void reportsCleanAndStreamsTheWholePayload() throws Exception {
        var received = new AtomicReference<byte[]>();
        try (var daemon = new FakeClamd("stream: OK", received)) {
            var scanner = scannerFor(daemon.port());

            assertThat(scanner.scan("quote.pdf", "application/pdf", PAYLOAD)).isEqualTo(FileScanStatus.CLEAN);
            daemon.awaitRequest();
            assertThat(received.get()).isEqualTo(PAYLOAD);
        }
    }

    @Test
    void reportsInfectedWhenTheDaemonFindsASignature() throws Exception {
        try (var daemon = new FakeClamd("stream: Eicar-Test-Signature FOUND", new AtomicReference<>())) {
            var scanner = scannerFor(daemon.port());

            assertThat(scanner.scan("bad.pdf", "application/pdf", PAYLOAD)).isEqualTo(FileScanStatus.INFECTED);
        }
    }

    @Test
    void failsClosedOnAnUnrecognisedReply() throws Exception {
        try (var daemon = new FakeClamd("stream: something unexpected", new AtomicReference<>())) {
            var scanner = scannerFor(daemon.port());

            assertThat(scanner.scan("odd.pdf", "application/pdf", PAYLOAD)).isEqualTo(FileScanStatus.ERROR);
        }
    }

    @Test
    void failsClosedWhenTheDaemonIsUnreachable() throws Exception {
        int unusedPort;
        try (var probe = new ServerSocket(0)) {
            unusedPort = probe.getLocalPort();
        }
        var scanner = scannerFor(unusedPort);

        assertThat(scanner.scan("quote.pdf", "application/pdf", PAYLOAD)).isEqualTo(FileScanStatus.ERROR);
    }

    @Test
    void skipsTheDaemonForEmptyContent() throws Exception {
        var scanner = scannerFor(1);

        assertThat(scanner.scan("empty.pdf", "application/pdf", new byte[0])).isEqualTo(FileScanStatus.CLEAN);
    }

    private static ClamAvFileScanner scannerFor(int port) {
        var clamav = new FileScannerProperties.ClamAv("127.0.0.1", port, Duration.ofSeconds(5));
        return new ClamAvFileScanner(new FileScannerProperties(FileScannerProperties.CLAMAV, clamav));
    }

    /** Minimal clamd stand-in speaking the INSTREAM framing the scanner writes. */
    private static final class FakeClamd implements AutoCloseable {

        private final ServerSocket serverSocket;
        private final Thread thread;

        FakeClamd(String reply, AtomicReference<byte[]> received) throws IOException {
            this.serverSocket = new ServerSocket(0);
            this.thread = new Thread(() -> serve(reply, received));
            this.thread.setDaemon(true);
            this.thread.start();
        }

        private void serve(String reply, AtomicReference<byte[]> received) {
            try (Socket socket = serverSocket.accept()) {
                var in = new DataInputStream(socket.getInputStream());
                readUntilNul(in);
                var payload = new ByteArrayOutputStream();
                int length;
                while ((length = in.readInt()) > 0) {
                    payload.write(in.readNBytes(length));
                }
                received.set(payload.toByteArray());
                OutputStream out = socket.getOutputStream();
                out.write(reply.getBytes(StandardCharsets.US_ASCII));
                out.write(0);
                out.flush();
            } catch (IOException ignored) {
                // The test asserts on the scanner's result, not on the stand-in's shutdown path.
            }
        }

        private static void readUntilNul(DataInputStream in) throws IOException {
            int value;
            while ((value = in.read()) != -1 && value != 0) {
                // discard the zINSTREAM command
            }
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        void awaitRequest() throws InterruptedException {
            thread.join(Duration.ofSeconds(5).toMillis());
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }
    }
}
