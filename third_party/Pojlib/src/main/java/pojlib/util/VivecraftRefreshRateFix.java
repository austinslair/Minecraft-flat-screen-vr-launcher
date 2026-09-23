package pojlib.util;

import org.objectweb.asm.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Compatibility fix for the exact bundled 1.21.5 OpenXR release.
 * Refresh-rate selection is optional: retain the runtime default instead of
 * allowing an empty enumeration to abort the entire OpenXR session.
 */
public final class VivecraftRefreshRateFix {
    private static final String ORIGINAL_SHA256 =
            "9208355cebed5f7c11dabc9c4e563549db90404f9690c6569fe2062c0c0f8358";
    static final String CLASS = "org/vivecraft/client_vr/provider/openxr/MCOpenXR.class";
    private VivecraftRefreshRateFix() {}

    public static boolean apply(File gameDir) throws IOException {
        File jar = new File(gameDir, "mods/Vivecraft.jar");
        if (!jar.isFile() || !ORIGINAL_SHA256.equals(sha256(jar))) return false;
        File temporary = File.createTempFile("vivecraft-refresh-", ".tmp", jar.getParentFile());
        boolean patched = false;
        try {
            try (ZipFile source = new ZipFile(jar);
                 ZipOutputStream output = new ZipOutputStream(new FileOutputStream(temporary))) {
                Enumeration<? extends ZipEntry> entries = source.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    output.putNextEntry(new ZipEntry(entry.getName()));
                    if (!entry.isDirectory()) {
                        try (InputStream input = source.getInputStream(entry)) {
                            if (CLASS.equals(entry.getName())) {
                                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                copy(input, bytes);
                                output.write(patchClass(bytes.toByteArray()));
                                patched = true;
                            } else copy(input, output);
                        }
                    }
                    output.closeEntry();
                }
            }
            if (!patched) throw new IOException("Bundled Vivecraft OpenXR class missing");
            // Keep the original outside mods; atomically replace only after a full write.
            File backups = new File(gameDir, "voxyquest-backups");
            Files.createDirectories(backups.toPath());
            File backup = new File(backups, "Vivecraft-" + ORIGINAL_SHA256 + ".jar");
            if (!backup.exists()) Files.copy(jar.toPath(), backup.toPath());
            Files.move(temporary.toPath(), jar.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            return true;
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }

    static byte[] patchClass(byte[] original) throws IOException {
        ClassReader reader = new ClassReader(original);
        if (!(reader.getClassName() + ".class").equals(CLASS))
            throw new IOException("Unexpected refresh-rate patch target");
        ClassWriter writer = new ClassWriter(0);
        int[] patched = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name,
                    String descriptor, String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (name.equals("initDisplayRefreshRate") && descriptor.equals("()V") &&
                        (access & Opcodes.ACC_STATIC) == 0) {
                    method.visitCode();
                    method.visitInsn(Opcodes.RETURN);
                    method.visitMaxs(0, 1);
                    method.visitEnd();
                    patched[0]++;
                    return null;
                }
                return method;
            }
        }, 0);
        if (patched[0] != 1) throw new IOException("Unexpected Vivecraft refresh-rate method");
        return writer.toByteArray();
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new FileInputStream(file)) {
                byte[] buffer = new byte[32768];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
    }
    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[32768];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }
}
