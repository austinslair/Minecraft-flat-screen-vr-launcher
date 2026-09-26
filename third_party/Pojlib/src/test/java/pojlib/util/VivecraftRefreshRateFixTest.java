package pojlib.util;

import org.junit.Test;
import static org.junit.Assert.*;
import org.objectweb.asm.*;
import java.io.*;
import java.nio.file.*;

public class VivecraftRefreshRateFixTest {
    static byte[] fixture() {
        ClassWriter w = new ClassWriter(0);
        w.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, VivecraftRefreshRateFix.CLASS.replace(".class", ""), null, "java/lang/Object", null);
        MethodVisitor c = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        c.visitCode(); c.visitVarInsn(Opcodes.ALOAD, 0);
        c.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        c.visitInsn(Opcodes.RETURN); c.visitMaxs(1, 1); c.visitEnd();
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "initDisplayRefreshRate", "()V", null, null);
        m.visitCode(); m.visitTypeInsn(Opcodes.NEW, "java/lang/IndexOutOfBoundsException"); m.visitInsn(Opcodes.DUP);
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IndexOutOfBoundsException", "<init>", "()V", false);
        m.visitInsn(Opcodes.ATHROW); m.visitMaxs(2, 1); m.visitEnd();
        w.visitEnd(); return w.toByteArray();
    }
    static Class<?> load(byte[] bytes) {
        return new ClassLoader() { Class<?> define() { return defineClass(null, bytes, 0, bytes.length); } }.define();
    }
    @Test public void optionalRefreshSelectionNoLongerAbortsInitialization() throws Exception {
        Class<?> before = load(fixture());
        try {
            before.getMethod("initDisplayRefreshRate").invoke(before.getConstructor().newInstance());
            fail("fixture must reproduce failure");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            assertTrue(expected.getCause() instanceof IndexOutOfBoundsException);
        }
        Class<?> after = load(VivecraftRefreshRateFix.patchClass(fixture()));
        after.getMethod("initDisplayRefreshRate").invoke(after.getConstructor().newInstance());
    }
    @Test public void unknownUserJarIsNotModified() throws Exception {
        Path game = Files.createTempDirectory("vivecraft-test");
        Path jar = game.resolve("mods/Vivecraft.jar");
        Files.createDirectories(jar.getParent());
        byte[] original = new byte[]{1,2,3}; Files.write(jar, original);
        assertFalse(VivecraftRefreshRateFix.apply(game.toFile()));
        assertArrayEquals(original, Files.readAllBytes(jar));
        Files.delete(jar); Files.delete(jar.getParent()); Files.delete(game);
    }
    @Test public void bundledReleasePatchesOnceAndPreservesOriginal() throws Exception {
        String source = System.getenv("VOXYQUEST_VIVECRAFT_TEST_JAR");
        org.junit.Assume.assumeNotNull(source);
        Path game = Files.createTempDirectory("vivecraft-release-test");
        Path jar = game.resolve("mods/Vivecraft.jar");
        Files.createDirectories(jar.getParent());
        Files.copy(Paths.get(source), jar);
        assertTrue(VivecraftRefreshRateFix.apply(game.toFile()));
        assertFalse(VivecraftRefreshRateFix.apply(game.toFile()));
        try (java.util.zip.ZipFile patched = new java.util.zip.ZipFile(jar.toFile());
             java.util.zip.ZipFile original = new java.util.zip.ZipFile(source)) {
            assertEquals(original.size(), patched.size());
            assertNotNull(patched.getEntry("fabric.mod.json"));
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(game)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.delete(path); } catch (IOException e) { throw new RuntimeException(e); }
            });
        }
    }
}
