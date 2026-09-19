package pojlib.util.download;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;

import pojlib.API;
import pojlib.util.Logger;

import javax.net.ssl.SSLException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Objects;
import javax.annotation.Nullable;

public class DownloadUtils {
    public static void downloadFile(String url, File out) throws IOException {
        downloadFile(url, out, -1);
    }

    /** Retry into a fresh temporary file; never append retries or publish partial bytes. */
    public static void downloadFile(String url, File out, long size) throws IOException {
        Objects.requireNonNull(out.getParentFile()).mkdirs();
        IOException failure = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            File partial = File.createTempFile("download-", ".part", out.getParentFile());
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestProperty("User-Agent", "VoxyQuest");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Download returned HTTP " + conn.getResponseCode());
                }
                long expected = size > 0 ? size : conn.getContentLengthLong();
                try (InputStream input = new StreamDL(conn.getInputStream(), Math.max(0, expected));
                     OutputStream output = new BufferedOutputStream(Files.newOutputStream(partial.toPath()))) {
                    IOUtils.copy(input, output);
                }
                if (expected >= 0 && partial.length() != expected) throw new IOException("Incomplete download");
                Files.move(partial.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                failure = e;
                if (e instanceof SSLException) throw e;
            } finally {
                if (conn != null) conn.disconnect();
                Files.deleteIfExists(partial.toPath());
            }
        }
        throw new IOException("Download failed after three attempts", failure);
    }

    public static boolean compareSHA1(File f, @Nullable String sourceSHA) {
        try {
            String sha1_dst;
            try (InputStream is = Files.newInputStream(f.toPath())) {
                sha1_dst = new String(Hex.encodeHex(DigestUtils.sha1(is)));
            }
            if (sourceSHA != null) return sha1_dst.equalsIgnoreCase(sourceSHA);
            else return true; // No hash provided

        } catch (IOException e) {
            Logger.getInstance().appendToLog("Issue while comparing SHA1: " + e);
            return false;
        }
    }
}
