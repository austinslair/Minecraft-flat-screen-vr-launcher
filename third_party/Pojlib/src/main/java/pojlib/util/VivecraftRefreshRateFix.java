package pojlib.util;

import org.objectweb.asm.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Compatibility fix for the verified Quest OpenXR releases in the runtime catalog.
 * Refresh-rate selection is optional: retain the runtime default instead of
 * allowing an empty enumeration to abort the entire OpenXR session.
 */
public final class VivecraftRefreshRateFix {
    // These are the exact Fabric OpenXR archives advertised by runtime_mods.json.
    // Leave unknown/user-supplied Vivecraft builds untouched.
    private static final Set<String> SUPPORTED_SHA256 = new HashSet<>(Arrays.asList(
            "9208355cebed5f7c11dabc9c4e563549db90404f9690c6569fe2062c0c0f8358", // 1.21.5
            "6c35acbb4c0121541ab48abf101be4d0ed89c40bd0a483915e919fbfdba30567", // 1.21.4
            "82080a982e4457e59768682a6f94c390727c3e10919fc45bd7257356fdf3b41a", // 1.21.1
            "40e36e92d4b9b1801595e00449028a749ff5571e825644f2ad8c97715ce6b175", // 1.20.6
            "cf1329f7eaec3db7f16e8facc350a27add17712c662b4d734ffb160652dba950", // 1.20.4
            "0d38bc1505adf4c7f907e666dc88617a647dc268b8fc51b8cc65a1a69bd4ebc5", // 1.20.1
            "6321df4528226e7f6e217787c54bc93d83311737279c9f1e6f5434e35b544a93", // 1.19.4
            "50abc0fc5fb335980c4b3bd0a36dbab91b7eea95c98eadb9b7f86822205a31d6"  // 1.19.2
    ));
    static final String CLASS = "org/vivecraft/client_vr/provider/openxr/MCOpenXR.class";
    private VivecraftRefreshRateFix() {}

    public static boolean apply(File gameDir) throws IOException {
        File jar = new File(gameDir, "mods/Vivecraft.jar");
        if (!jar.isFile()) return false;
        String originalSha = sha256(jar);
        if (!SUPPORTED_SHA256.contains(originalSha)) return false;
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
            File backup = new File(backups, "Vivecraft-" + originalSha + ".jar");
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
