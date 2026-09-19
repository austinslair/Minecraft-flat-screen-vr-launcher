package pojlib.util.download;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class DownloadUtilsTest {
    @Test public void retriesDoNotPublishPartialBytes() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/file", exchange -> {
            int attempt = requests.incrementAndGet();
            byte[] data = (attempt == 1 ? "bad" : "complete").getBytes();
            exchange.sendResponseHeaders(200, 8);
            exchange.getResponseBody().write(data);
            exchange.close();
        });
        server.start();
        Path directory = Files.createTempDirectory("voxyquest-download-");
        Path output = directory.resolve("client.jar");
        try {
            DownloadUtils.downloadFile("http://127.0.0.1:" + server.getAddress().getPort() + "/file", output.toFile());
            assertEquals("complete", new String(Files.readAllBytes(output)));
            assertEquals(2, requests.get());
            try (java.util.stream.Stream<Path> files = Files.list(directory)) { assertEquals(1L, files.count()); }
        } finally { server.stop(0); Files.deleteIfExists(output); Files.delete(directory); }
    }

    @Test public void httpErrorsStopAfterThreeAttemptsAndPreserveExistingFile() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/missing", exchange -> { requests.incrementAndGet(); exchange.sendResponseHeaders(404, -1); exchange.close(); });
        server.start();
        Path directory = Files.createTempDirectory("voxyquest-download-");
        Path output = directory.resolve("client.jar");
        Files.write(output, "existing".getBytes());
        try {
            try {
                DownloadUtils.downloadFile("http://127.0.0.1:" + server.getAddress().getPort() + "/missing", output.toFile());
                fail("HTTP errors must fail the installation");
            } catch (java.io.IOException expected) { }
            assertEquals(3, requests.get());
            assertEquals("existing", new String(Files.readAllBytes(output)));
        } finally { server.stop(0); Files.deleteIfExists(output); Files.delete(directory); }
    }
}
