package pojlib.util;

import org.junit.Test;
import static org.junit.Assert.*;
import org.objectweb.asm.*;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

public class SableNativeFixTest {
    static final String EXTRACTED = "/storage/emulated/0/game/.sable/natives/sable_rapier_aarch64_linux.so";
    static final String ANDROID_BUILD = "/data/app/voxyquest/lib/arm64/libsable_rapier.so";

    /** Like Sable's Rapier3D: loads the library it extracted into the game directory. */
    static byte[] loader() {
        String owner = SableNativeFix.LOADER_CLASS.replace(".class", "");
        ClassWriter w = new ClassWriter(0);
        w.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "loadLibrary", "()V", null, null);
        m.visitCode();
        m.visitLdcInsn(EXTRACTED);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "load", "(Ljava/lang/String;)V", false);
        m.visitInsn(Opcodes.RETURN);
        m.visitMaxs(1, 0);
        m.visitEnd();
        w.visitEnd();
        return w.toByteArray();
    }

    static String loadedPath(byte[] bytes) throws Exception {
        Class<?> type = new ClassLoader() {
            Class<?> define() { return defineClass(null, bytes, 0, bytes.length); }
        }.define();
        try {
            type.getMethod("loadLibrary").invoke(null);
            fail("fixture paths do not exist");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertTrue(e.getCause() instanceof UnsatisfiedLinkError);
            return e.getCause().getMessage();
        }
        return null;
    }

    static byte[] zip(String name, byte[] contents, boolean stored) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            out.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            out.write("Manifest-Version: 1.0\n".getBytes());
            out.closeEntry();
            ZipEntry entry = new ZipEntry(name);
            if (stored) {
                CRC32 crc = new CRC32();
                crc.update(contents);
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(contents.length);
                entry.setCompressedSize(contents.length);
                entry.setCrc(crc.getValue());
            }
            out.putNextEntry(entry);
            out.write(contents);
            out.closeEntry();
        }
        return bytes.toByteArray();
    }

    static File sableJar(File mods, String version) throws IOException {
        byte[] rapier = zip(SableNativeFix.LOADER_CLASS, loader(), false);
        File jar = new File(mods, "sable-neoforge-1.21.1-" + version + ".jar");
        Files.write(jar.toPath(), zip("META-INF/jarjar/dev.ryanhcode.sable.sable-sable_rapier-1.21.1-"
                + version + ".jar", rapier, true));
        return jar;
    }

    static byte[] entry(File archive, String name) throws IOException {
        try (ZipFile zip = new ZipFile(archive); InputStream in = zip.getInputStream(zip.getEntry(name))) {
            return in.readAllBytes();
        }
    }

    static byte[] entry(byte[] archive, String name) throws IOException {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) if (e.getName().equals(name)) return in.readAllBytes();
        }
        return null;
    }

    @Test public void patchedLoaderLoadsTheAndroidBuild() throws Exception {
        System.setProperty(SableNativeFix.PROPERTY, ANDROID_BUILD);
        assertTrue(loadedPath(loader()).contains(EXTRACTED));
        byte[] patched = SableNativeFix.patchLoader(loader());
        String message = loadedPath(patched);
        assertTrue(message, message.contains(ANDROID_BUILD));
        assertFalse(message.contains(EXTRACTED));
        assertTrue(SableNativeFix.isPatched(patched));
        assertFalse(SableNativeFix.isPatched(loader()));
    }

    @Test public void patchesSupportedSableJarOnce() throws Exception {
        File game = Files.createTempDirectory("sable-fix").toFile();
        File mods = new File(game, "mods");
        assertTrue(mods.mkdirs());
        File jar = sableJar(mods, SableNativeFix.SUPPORTED_VERSION);
        File other = new File(mods, "create-1.21.1-6.0.10.jar");
        Files.write(other.toPath(), zip("com/simibubi/create/Create.class", new byte[] {1}, false));

        SableNativeFix.Result first = SableNativeFix.apply(game);
        assertEquals(java.util.Collections.singletonList(jar.getName()), first.patched);
        assertTrue(first.unsupported.isEmpty());
        String nested = "META-INF/jarjar/dev.ryanhcode.sable.sable-sable_rapier-1.21.1-"
                + SableNativeFix.SUPPORTED_VERSION + ".jar";
        try (ZipFile zip = new ZipFile(jar)) {
            assertEquals(ZipEntry.STORED, zip.getEntry(nested).getMethod());
            assertNotNull(zip.getEntry("META-INF/MANIFEST.MF"));
        }
        assertTrue(SableNativeFix.isPatched(entry(entry(jar, nested), SableNativeFix.LOADER_CLASS)));
        assertTrue(new File(game, "voxyquest-backups/" + jar.getName()).isFile());

        assertTrue(SableNativeFix.apply(game).patched.isEmpty());
        assertTrue(new File(game, SableNativeFix.STAMP).delete());
        assertTrue(SableNativeFix.apply(game).patched.isEmpty());
    }

    @Test public void leavesOtherSableReleasesAlone() throws Exception {
        File game = Files.createTempDirectory("sable-fix").toFile();
        File mods = new File(game, "mods");
        assertTrue(mods.mkdirs());
        File jar = sableJar(mods, "9.9.9");
        byte[] before = Files.readAllBytes(jar.toPath());
        SableNativeFix.Result result = SableNativeFix.apply(game);
        assertTrue(result.patched.isEmpty());
        assertEquals(java.util.Collections.singletonList(jar.getName()), result.unsupported);
        assertArrayEquals(before, Files.readAllBytes(jar.toPath()));
    }
}
