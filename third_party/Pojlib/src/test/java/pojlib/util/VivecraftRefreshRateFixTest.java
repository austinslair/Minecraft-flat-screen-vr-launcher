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
    @Test public void swapchainTextureUsesExistingOpenXRStorage() throws Exception {
        ClassWriter w = new ClassWriter(0);
        w.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, VivecraftRefreshRateFix.TEXTURE_CLASS.replace(".class", ""), null, "java/lang/Object", null);
        MethodVisitor m = w.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        m.visitCode();
        m.visitInsn(Opcodes.ACONST_NULL);
        m.visitInsn(Opcodes.ACONST_NULL);
        m.visitInsn(Opcodes.ACONST_NULL);
        for (int i = 0; i < 4; i++) m.visitInsn(Opcodes.ICONST_1);
        m.visitMethodInsn(Opcodes.INVOKEINTERFACE, "org/vivecraft/client/extensions/GlDeviceExtension",
                "vivecraft$createFixedIdTexture", "(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/TextureFormat;IIII)Lcom/mojang/blaze3d/textures/GpuTexture;", true);
        m.visitInsn(Opcodes.POP); m.visitInsn(Opcodes.RETURN); m.visitMaxs(7, 1); m.visitEnd(); w.visitEnd();
        byte[] changed = VivecraftRefreshRateFix.patchSwapchainClass(w.toByteArray());
        int[] changedCalls = {0};
        new ClassReader(changed).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
                        assertEquals("vivecraft$precreatedFixedIdTexture", method);
                        changedCalls[0]++;
                    }
                };
            }
        }, 0);
        assertEquals(1, changedCalls[0]);
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

    @Test public void bundledNeoForgePatchesButAnotherJarDoesNot() throws Exception {
        String source = System.getenv("VOXYQUEST_NEOFORGE_TEST_JAR");
        org.junit.Assume.assumeNotNull(source);
        Path game = Files.createTempDirectory("vivecraft-neoforge-test");
        Path jar = game.resolve("mods/Vivecraft.jar");
        Files.createDirectories(jar.getParent());
        // Existing installs can have a different ZIP hash from the freshly built APK.
        try (java.util.zip.ZipFile original = new java.util.zip.ZipFile(source);
             java.util.zip.ZipOutputStream repacked = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = original.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                repacked.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                if (!entry.isDirectory()) {
                    try (InputStream input = original.getInputStream(entry)) { input.transferTo(repacked); }
                }
                repacked.closeEntry();
            }
            repacked.putNextEntry(new java.util.zip.ZipEntry("META-INF/old-apk-marker.txt"));
            repacked.write(1);
            repacked.closeEntry();
        }
        assertFalse(java.util.Arrays.equals(Files.readAllBytes(Paths.get(source)), Files.readAllBytes(jar)));
        try (InputStream wrong = new ByteArrayInputStream(new byte[]{1, 2, 3})) {
            assertFalse(VivecraftRefreshRateFix.apply(game.toFile(), wrong));
        }
        try (InputStream bundled = Files.newInputStream(Paths.get(source))) {
            assertTrue(VivecraftRefreshRateFix.apply(game.toFile(), bundled));
        }
        try (InputStream bundled = Files.newInputStream(Paths.get(source))) {
            assertFalse(VivecraftRefreshRateFix.apply(game.toFile(), bundled));
        }
        Path stamp = game.resolve("voxyquest-backups/vivecraft-openxr-v2.stamp");
        assertTrue(Files.isRegularFile(stamp));
        Files.writeString(stamp, "damaged cache");
        try (InputStream bundled = Files.newInputStream(Paths.get(source))) {
            assertFalse(VivecraftRefreshRateFix.apply(game.toFile(), bundled));
        }
        try (java.util.zip.ZipFile patched = new java.util.zip.ZipFile(jar.toFile())) {
            assertNotNull(patched.getEntry("META-INF/neoforge.mods.toml"));
            byte[] target = patched.getInputStream(patched.getEntry(VivecraftRefreshRateFix.TEXTURE_CLASS)).readAllBytes();
            assertThrows(IOException.class, () -> VivecraftRefreshRateFix.patchSwapchainClass(target));
        }
        // Reproduce an alpha.18 install: only the refresh-rate class was changed.
        try (java.util.zip.ZipFile original = new java.util.zip.ZipFile(source);
             java.util.zip.ZipOutputStream old = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = original.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                old.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                if (!entry.isDirectory()) {
                    try (InputStream input = original.getInputStream(entry)) {
                        byte[] bytes = input.readAllBytes();
                        old.write(VivecraftRefreshRateFix.CLASS.equals(entry.getName()) ?
                                VivecraftRefreshRateFix.patchClass(bytes) : bytes);
                    }
                }
                old.closeEntry();
            }
        }
        Files.setLastModifiedTime(jar, java.nio.file.attribute.FileTime.fromMillis(
                System.currentTimeMillis() + 2000));
        try (InputStream bundled = Files.newInputStream(Paths.get(source))) {
            assertTrue(VivecraftRefreshRateFix.apply(game.toFile(), bundled));
        }
        try (InputStream bundled = Files.newInputStream(Paths.get(source))) {
            assertFalse(VivecraftRefreshRateFix.apply(game.toFile(), bundled));
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(game)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.delete(path); } catch (IOException e) { throw new RuntimeException(e); }
            });
        }
    }

    @Test public void olderNeoForgeOpenXrBuildsPatchRefreshOnly() throws Exception {
        String sources = System.getenv("VOXYQUEST_NEOFORGE_LEGACY_TEST_JARS");
        org.junit.Assume.assumeTrue(sources != null && !sources.isEmpty());
        for (String source : sources.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            Path game = Files.createTempDirectory("vivecraft-older-neoforge-test");
            try {
                Path jar = game.resolve("mods/Vivecraft.jar");
                Files.createDirectories(jar.getParent());
                Files.copy(Paths.get(source), jar);
                byte[] textureBefore, refreshBefore;
                try (java.util.zip.ZipFile original = new java.util.zip.ZipFile(source)) {
                    textureBefore = original.getInputStream(original.getEntry(VivecraftRefreshRateFix.TEXTURE_CLASS)).readAllBytes();
                    refreshBefore = original.getInputStream(original.getEntry(VivecraftRefreshRateFix.CLASS)).readAllBytes();
                }
                try (InputStream bundled = Files.newInputStream(Paths.get(source))) {
                    assertTrue(source, VivecraftRefreshRateFix.apply(game.toFile(), bundled));
                }
                try (InputStream bundled = Files.newInputStream(Paths.get(source))) {
                    assertFalse(source, VivecraftRefreshRateFix.apply(game.toFile(), bundled));
                }
                try (java.util.zip.ZipFile patched = new java.util.zip.ZipFile(jar.toFile())) {
                    assertArrayEquals(source, textureBefore,
                            patched.getInputStream(patched.getEntry(VivecraftRefreshRateFix.TEXTURE_CLASS)).readAllBytes());
                    assertArrayEquals(source, VivecraftRefreshRateFix.patchClass(refreshBefore),
                            patched.getInputStream(patched.getEntry(VivecraftRefreshRateFix.CLASS)).readAllBytes());
                }
            } finally {
                try (java.util.stream.Stream<Path> paths = Files.walk(game)) {
                    paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                        try { Files.delete(path); } catch (IOException e) { throw new RuntimeException(e); }
                    });
                }
            }
        }
    }
}
