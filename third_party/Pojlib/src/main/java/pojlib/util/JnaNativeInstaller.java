package pojlib.util;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Use the native JNA library bundled with the Java JNA jar for this instance. */
public final class JnaNativeInstaller {
    private static final String ENTRY = "com/sun/jna/linux-aarch64/libjnidispatch.so";

    private JnaNativeInstaller() {}

    public static String prepare(Context context, String classpath) throws IOException {
        if (classpath == null) throw new IOException("Game classpath is missing");
        for (String path : classpath.split(Pattern.quote(File.pathSeparator))) {
            File jar = new File(path);
            if (!jar.getName().matches("jna-[0-9][A-Za-z0-9._-]*\\.jar")) continue;
            try (JarFile archive = new JarFile(jar)) {
                JarEntry entry = archive.getJarEntry(ENTRY);
                if (entry == null || entry.getSize() <= 0 || entry.getSize() > 5_000_000)
                    throw new IOException("JNA jar has no ARM64 native library: " + jar.getName());
                File root = new File(context.getFilesDir(), "runtimes/jna");
                Files.createDirectories(root.toPath());
                File temp = File.createTempFile("jnidispatch-", ".so", root);
                try {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    long bytes = 0;
                    try (InputStream input = archive.getInputStream(entry);
                            java.io.OutputStream output = Files.newOutputStream(temp.toPath())) {
                        byte[] chunk = new byte[8192];
                        int count;
                        while ((count = input.read(chunk)) != -1) {
                            bytes += count;
                            if (bytes > 5_000_000) throw new IOException("JNA native exceeds size limit");
                            output.write(chunk, 0, count);
                            digest.update(chunk, 0, count);
                        }
                    }
                    if (bytes < 20) throw new IOException("JNA native is incomplete");
                    byte[] header = new byte[20];
                    try (InputStream input = Files.newInputStream(temp.toPath())) {
                        if (input.read(header) != header.length || header[0] != 0x7f ||
                                header[1] != 'E' || header[2] != 'L' || header[3] != 'F' ||
                                header[4] != 2 || (header[18] & 0xff) != 183 || header[19] != 0)
                            throw new IOException("JNA native is not an ARM64 library");
                    }
                    StringBuilder hash = new StringBuilder();
                    for (byte value : digest.digest()) hash.append(String.format("%02x", value & 0xff));
                    File targetDir = new File(root, hash.toString());
                    Files.createDirectories(targetDir.toPath());
                    Files.move(temp.toPath(), new File(targetDir, "libjnidispatch.so").toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    Logger.getInstance().appendToLog("VoxyQuest launch: using matching JNA native from " + jar.getName());
                    return targetDir.getAbsolutePath();
                } catch (NoSuchAlgorithmException e) {
                    throw new IOException("SHA-256 is unavailable", e);
                } finally {
                    Files.deleteIfExists(temp.toPath());
                }
            }
        }
        // Older profiles without a JNA jar keep using the native bundled with the APK.
        return context.getApplicationInfo().nativeLibraryDir;
    }
}
