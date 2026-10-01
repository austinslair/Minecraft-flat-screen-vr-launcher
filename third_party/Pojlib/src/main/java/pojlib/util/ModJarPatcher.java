package pojlib.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Shared JAR plumbing for launch-time compatibility patches to mods in an instance's mods folder. */
final class ModJarPatcher {
    private ModJarPatcher() {}

    /** Mod JARs in the instance, sorted so stamps are stable. */
    static File[] modJars(File gameDir) {
        File[] jars = new File(gameDir, "mods").listFiles((dir, name) -> name.endsWith(".jar"));
        if (jars == null) return new File[0];
        Arrays.sort(jars);
        return jars;
    }

    static String identity(File jar) {
        return jar.getName() + ":" + jar.length() + ":" + jar.lastModified();
    }

    /** JARs nested by NeoForge (META-INF/jarjar) or Fabric (META-INF/jars) whose names contain {@code part}. */
    static List<String> nestedJars(File jar, String part) {
        List<String> names = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if ((name.startsWith("META-INF/jarjar/") || name.startsWith("META-INF/jars/")) &&
                        name.endsWith(".jar") && name.contains(part)) names.add(name);
            }
        } catch (IOException ignored) {
            // Not a readable archive; the mod loader will report it.
        }
        return names;
    }

    static byte[] readEntry(File jar, String name) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null) return null;
            try (InputStream input = zip.getInputStream(entry)) { return readAll(input); }
        }
    }

    static byte[] readEntry(byte[] archive, String name) throws IOException {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.getName().equals(name)) return readAll(input);
            }
        }
        return null;
    }

    /** Rewrites an in-memory JAR with entries replaced by name and new entries appended. */
    static byte[] rewrite(byte[] archive, Map<String, byte[]> replaced, Map<String, byte[]> added) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(archive.length);
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive));
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (added.containsKey(entry.getName())) continue;
                output.putNextEntry(new ZipEntry(entry.getName()));
                byte[] replacement = replaced.get(entry.getName());
                if (replacement != null) output.write(replacement);
                else if (!entry.isDirectory()) copy(input, output);
                output.closeEntry();
            }
            for (Map.Entry<String, byte[]> extra : added.entrySet()) {
                output.putNextEntry(new ZipEntry(extra.getKey()));
                output.write(extra.getValue());
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    /**
     * Replaces entries of a mod JAR on disk. The original is kept in voxyquest-backups and the
     * JAR is only replaced after a complete write. Replaced entries keep their compression
     * method, so a stored nested JAR stays stored and loaders can still map it directly.
     */
    static void rewriteFile(File gameDir, File jar, Map<String, byte[]> replaced) throws IOException {
        File temporary = File.createTempFile("voxyquest-mod-", ".tmp", jar.getParentFile());
        try {
            try (ZipFile source = new ZipFile(jar);
                 ZipOutputStream output = new ZipOutputStream(new FileOutputStream(temporary))) {
                Set<String> names = new HashSet<>();
                Enumeration<? extends ZipEntry> entries = source.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    byte[] replacement = replaced.get(entry.getName());
                    if (replacement != null) {
                        ZipEntry written = new ZipEntry(entry.getName());
                        if (entry.getMethod() == ZipEntry.STORED) {
                            CRC32 crc = new CRC32();
                            crc.update(replacement);
                            written.setMethod(ZipEntry.STORED);
                            written.setSize(replacement.length);
                            written.setCompressedSize(replacement.length);
                            written.setCrc(crc.getValue());
                        }
                        output.putNextEntry(written);
                        output.write(replacement);
                    } else {
                        output.putNextEntry(new ZipEntry(entry.getName()));
                        if (!entry.isDirectory()) {
                            try (InputStream input = source.getInputStream(entry)) { copy(input, output); }
                        }
                    }
                    output.closeEntry();
                    names.add(entry.getName());
                }
                // Entries the JAR does not have yet, such as a helper class a fix adds.
                for (Map.Entry<String, byte[]> extra : replaced.entrySet()) {
                    if (names.contains(extra.getKey())) continue;
                    output.putNextEntry(new ZipEntry(extra.getKey()));
                    output.write(extra.getValue());
                    output.closeEntry();
                }
            }
            File backups = new File(gameDir, "voxyquest-backups");
            Files.createDirectories(backups.toPath());
            File backup = new File(backups, jar.getName());
            if (!backup.exists()) Files.copy(jar.toPath(), backup.toPath());
            try {
                Files.move(temporary.toPath(), jar.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary.toPath(), jar.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }

    static Set<String> readStamp(File stamp) {
        try {
            return new HashSet<>(Files.readAllLines(stamp.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Collections.emptySet();
        }
    }

    static void writeStamp(File stamp, List<String> identities) {
        try {
            Files.createDirectories(stamp.getParentFile().toPath());
            Files.write(stamp.toPath(), identities, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Only costs a recheck on the next launch.
        }
    }

    static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        copy(input, bytes);
        return bytes.toByteArray();
    }

    static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[32768];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }
}
