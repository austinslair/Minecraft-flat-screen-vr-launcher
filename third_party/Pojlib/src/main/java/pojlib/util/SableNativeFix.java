package pojlib.util;

import org.objectweb.asm.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Lets Sable, the physics library Create 6 requires, load its Rapier natives on Android.
 *
 * Sable unpacks a desktop Linux build into {@code <game dir>/.sable/natives} and loads it
 * from there. The game directory is on shared storage, where Android refuses to load native
 * code, and the library links against glibc anyway, so every world load crashes. The APK
 * carries an Android build of the same Sable release (scripts/build_sable_rapier_android.sh).
 * This patches the {@code System.load} call in Sable's loader to read that library's path
 * from the {@link #PROPERTY} system property instead.
 */
public final class SableNativeFix {
    /** The Sable release CI builds natives for. Its JNI interface changes between releases. */
    public static final String SUPPORTED_VERSION = "2.0.5";
    public static final String PROPERTY = "voxyquest.sable.native";
    static final String LOADER_CLASS = "dev/ryanhcode/sable/physics/impl/rapier/Rapier3D.class";
    static final String LOADER_METHOD = "loadLibrary";
    static final String STAMP = "voxyquest-backups/sable-natives-v1.stamp";

    private SableNativeFix() {}

    /** Result of one launch's check: the mod JARs patched now and Sable builds left alone. */
    public static final class Result {
        public final List<String> patched = new ArrayList<>();
        public final List<String> unsupported = new ArrayList<>();
    }

    public static Result apply(File gameDir) throws IOException {
        Result result = new Result();
        File[] jars = new File(gameDir, "mods").listFiles((dir, name) -> name.endsWith(".jar"));
        if (jars == null) return result;
        Arrays.sort(jars);
        // Checked JARs stay valid until they change. Avoid reopening every mod on each launch.
        File stamp = new File(gameDir, STAMP);
        Set<String> verified = readStamp(stamp);
        List<String> identities = new ArrayList<>();
        for (File jar : jars) {
            String identity = jar.getName() + ":" + jar.length() + ":" + jar.lastModified();
            if (!verified.contains(identity)) {
                String nested = rapierJar(jar);
                if (nested != null) {
                    if (!nested.endsWith("-" + SUPPORTED_VERSION + ".jar")) {
                        result.unsupported.add(jar.getName());
                    } else if (patchJar(gameDir, jar, nested)) {
                        result.patched.add(jar.getName());
                        identity = jar.getName() + ":" + jar.length() + ":" + jar.lastModified();
                    }
                }
            }
            identities.add(identity);
        }
        writeStamp(stamp, identities);
        return result;
    }

    /** The nested Rapier JAR inside a Sable mod JAR, found by name under either loader's layout. */
    static String rapierJar(File jar) {
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if ((name.startsWith("META-INF/jarjar/") || name.startsWith("META-INF/jars/")) &&
                        name.endsWith(".jar") && name.contains("sable_rapier")) return name;
            }
        } catch (IOException ignored) {
            // Not a readable archive; the mod loader will report it.
        }
        return null;
    }

    private static boolean patchJar(File gameDir, File jar, String nestedName) throws IOException {
        byte[] nested;
        try (ZipFile zip = new ZipFile(jar); InputStream input = zip.getInputStream(zip.getEntry(nestedName))) {
            nested = readAll(input);
        }
        byte[] loader = readEntry(nested, LOADER_CLASS);
        if (loader == null || isPatched(loader)) return false;
        byte[] patchedNested = replaceEntry(nested, LOADER_CLASS, patchLoader(loader));

        File temporary = File.createTempFile("sable-natives-", ".tmp", jar.getParentFile());
        try {
            try (ZipFile source = new ZipFile(jar);
                 ZipOutputStream output = new ZipOutputStream(new FileOutputStream(temporary))) {
                Enumeration<? extends ZipEntry> entries = source.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.getName().equals(nestedName)) {
                        // Keep the nested JAR stored if it was, so loaders can still map it directly.
                        ZipEntry replacement = new ZipEntry(nestedName);
                        if (entry.getMethod() == ZipEntry.STORED) {
                            CRC32 crc = new CRC32();
                            crc.update(patchedNested);
                            replacement.setMethod(ZipEntry.STORED);
                            replacement.setSize(patchedNested.length);
                            replacement.setCompressedSize(patchedNested.length);
                            replacement.setCrc(crc.getValue());
                        }
                        output.putNextEntry(replacement);
                        output.write(patchedNested);
                    } else {
                        output.putNextEntry(new ZipEntry(entry.getName()));
                        if (!entry.isDirectory()) {
                            try (InputStream input = source.getInputStream(entry)) { copy(input, output); }
                        }
                    }
                    output.closeEntry();
                }
            }
            // Keep the original outside mods; replace it only after a full write.
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
            return true;
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }

    /** Replaces the path Sable extracted with the property's value, just before it is loaded. */
    static byte[] patchLoader(byte[] original) throws IOException {
        ClassReader reader = new ClassReader(original);
        if (!(reader.getClassName() + ".class").equals(LOADER_CLASS))
            throw new IOException("Unexpected Sable native loader");
        ClassWriter writer = new ClassWriter(0);
        int[] patched = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals(LOADER_METHOD) || !descriptor.equals("()V")) return method;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override public void visitMethodInsn(int opcode, String owner, String methodName,
                            String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC && owner.equals("java/lang/System") &&
                                methodName.equals("load") && methodDescriptor.equals("(Ljava/lang/String;)V")) {
                            super.visitInsn(Opcodes.POP);
                            super.visitLdcInsn(PROPERTY);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperty",
                                    "(Ljava/lang/String;)Ljava/lang/String;", false);
                            patched[0]++;
                        }
                        super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                    }
                };
            }
        }, 0);
        if (patched[0] != 1) throw new IOException("Unexpected Sable native loader method");
        return writer.toByteArray();
    }

    static boolean isPatched(byte[] loader) {
        boolean[] found = {false};
        new ClassReader(loader).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitLdcInsn(Object value) {
                        if (PROPERTY.equals(value)) found[0] = true;
                    }
                };
            }
        }, 0);
        return found[0];
    }

    private static byte[] readEntry(byte[] archive, String name) throws IOException {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (entry.getName().equals(name)) return readAll(input);
            }
        }
        return null;
    }

    private static byte[] replaceEntry(byte[] archive, String name, byte[] contents) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(archive.length);
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive));
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                output.putNextEntry(new ZipEntry(entry.getName()));
                if (entry.getName().equals(name)) output.write(contents);
                else if (!entry.isDirectory()) copy(input, output);
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        copy(input, bytes);
        return bytes.toByteArray();
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[32768];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }

    private static Set<String> readStamp(File stamp) {
        try {
            return new HashSet<>(Files.readAllLines(stamp.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Collections.emptySet();
        }
    }

    private static void writeStamp(File stamp, List<String> identities) {
        try {
            Files.createDirectories(stamp.getParentFile().toPath());
            Files.write(stamp.toPath(), identities, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Only costs a recheck on the next launch.
        }
    }
}
