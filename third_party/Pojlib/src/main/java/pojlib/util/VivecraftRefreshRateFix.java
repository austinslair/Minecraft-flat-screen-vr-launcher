package pojlib.util;

import org.objectweb.asm.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Compatibility fix for the verified Quest OpenXR releases in the runtime catalog.
 * Refresh-rate selection is optional: Vivecraft still asks the headset for its highest
 * rate (120 Hz on Quest 3, so VR can run above 72/90 fps), but a failure keeps the
 * runtime default instead of an empty enumeration aborting the entire OpenXR session.
 */
public final class VivecraftRefreshRateFix {
    // These are the exact Fabric OpenXR archives advertised by runtime_mods.json.
    // Leave unknown/user-supplied Vivecraft builds untouched.
    private static final Set<String> SUPPORTED_SHA256 = new HashSet<>(Arrays.asList(
            "22eb7e3057b458d5d1eb0873b8298a3621e29be7544fa86c25ae7a457700d7dd", // 1.21.11
            "60958a8ab2cfb49f7f74dfd9075c5111468aa34af5b5e59b230e9836bf36b495", // 1.21.10
            "75f76e8b10470505d88d4aa1ff088c67850f1e0f865bba9a7628ae3266c8517e", // 1.21.8
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
    static final String TEXTURE_CLASS = "org/vivecraft/client_vr/VRTextureTarget.class";
    static final String REFRESH_METHOD = "initDisplayRefreshRate";
    static final String ORIGINAL_REFRESH_METHOD = "voxyquest$initDisplayRefreshRate";
    /** Bumped whenever the patch changes, so installs verified by an older build are checked again. */
    static final String STAMP = "voxyquest-backups/vivecraft-openxr-v3.stamp";
    private VivecraftRefreshRateFix() {}

    public static boolean apply(File gameDir) throws IOException {
        return apply(gameDir, null);
    }

    /** Accept the bundled NeoForge class even when an earlier APK packed the JAR differently. */
    public static boolean apply(File gameDir, InputStream bundledNeoForge) throws IOException {
        File jar = new File(gameDir, "mods/Vivecraft.jar");
        if (!jar.isFile()) return false;
        // A verified patch stays valid until the JAR changes. Avoid hashing the
        // whole archive and inflating the bundled NeoForge archive on each launch.
        File stamp = new File(gameDir, STAMP);
        String identity = jar.length() + ":" + jar.lastModified();
        try {
            if (stamp.isFile() && identity.equals(new String(Files.readAllBytes(stamp.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8))) return false;
        } catch (IOException ignored) {
            // A damaged cache must not stop game startup.
        }
        // Earlier versions removed the refresh-rate request outright, pinning VR to the
        // headset's default rate. Start again from the untouched JAR kept in the backups.
        restoreUnstubbedBackup(gameDir, jar);
        String originalSha = sha256(jar);
        int neoForgeState = bundledNeoForge == null ? 0 : bundledNeoForgeState(jar, bundledNeoForge);
        if (!SUPPORTED_SHA256.contains(originalSha) && neoForgeState == 0) return false;
        boolean patchRefresh = neoForgeState != 2 && neoForgeState != 5;
        boolean patchTexture = neoForgeState == 1 || neoForgeState == 2;
        if (neoForgeState == 3 || neoForgeState == 5) {
            rememberVerified(stamp, jar);
            return false;
        }
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
                            if (CLASS.equals(entry.getName()) && patchRefresh) {
                                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                copy(input, bytes);
                                output.write(patchClass(bytes.toByteArray()));
                                patched = true;
                            } else if (TEXTURE_CLASS.equals(entry.getName()) && patchTexture) {
                                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                                copy(input, bytes);
                                output.write(patchSwapchainClass(bytes.toByteArray()));
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
            rememberVerified(stamp, jar);
            return true;
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }

    private static void rememberVerified(File stamp, File jar) {
        try {
            Files.createDirectories(stamp.getParentFile().toPath());
            Files.write(stamp.toPath(), (jar.length() + ":" + jar.lastModified())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // The patch is already verified; a cache write failure only costs a future check.
        }
    }

    /**
     * Moves Vivecraft's refresh-rate selection into a private method and replaces it with a
     * wrapper that calls it inside a catch-all. The original body, including its own
     * try-with-resources handler for the LWJGL MemoryStack, is kept byte for byte.
     */
    static byte[] patchClass(byte[] original) throws IOException {
        ClassReader reader = new ClassReader(original);
        if (!(reader.getClassName() + ".class").equals(CLASS))
            throw new IOException("Unexpected refresh-rate patch target");
        String owner = reader.getClassName();
        ClassWriter writer = new ClassWriter(0);
        int[] refreshAccess = {-1};
        boolean[] alreadyWrapped = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name,
                    String descriptor, String signature, String[] exceptions) {
                if (name.equals(ORIGINAL_REFRESH_METHOD)) alreadyWrapped[0] = true;
                if (name.equals(REFRESH_METHOD) && descriptor.equals("()V") &&
                        (access & Opcodes.ACC_STATIC) == 0 && refreshAccess[0] == -1) {
                    refreshAccess[0] = access;
                    int hidden = (access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED))
                            | Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC;
                    return super.visitMethod(hidden, ORIGINAL_REFRESH_METHOD, descriptor, signature, exceptions);
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }

            @Override public void visitEnd() {
                if (refreshAccess[0] != -1 && !alreadyWrapped[0]) {
                    MethodVisitor wrapper = super.visitMethod(refreshAccess[0], REFRESH_METHOD, "()V", null, null);
                    Label start = new Label(), end = new Label(), handler = new Label();
                    wrapper.visitCode();
                    wrapper.visitTryCatchBlock(start, end, handler, "java/lang/Throwable");
                    wrapper.visitLabel(start);
                    wrapper.visitVarInsn(Opcodes.ALOAD, 0);
                    wrapper.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, ORIGINAL_REFRESH_METHOD, "()V", false);
                    wrapper.visitLabel(end);
                    wrapper.visitInsn(Opcodes.RETURN);
                    wrapper.visitLabel(handler);
                    wrapper.visitFrame(Opcodes.F_FULL, 1, new Object[]{owner}, 1, new Object[]{"java/lang/Throwable"});
                    wrapper.visitInsn(Opcodes.POP);
                    wrapper.visitInsn(Opcodes.RETURN);
                    wrapper.visitMaxs(1, 1);
                    wrapper.visitEnd();
                }
                super.visitEnd();
            }
        }, 0);
        if (refreshAccess[0] == -1 || alreadyWrapped[0])
            throw new IOException("Unexpected Vivecraft refresh-rate method");
        return writer.toByteArray();
    }

    /** True for the earlier patch, which replaced the method with a bare return (maxStack 0). */
    static boolean hasRefreshStub(byte[] bytes) {
        boolean[] stub = {false};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                if (!name.equals(REFRESH_METHOD) || !descriptor.equals("()V")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMaxs(int maxStack, int maxLocals) {
                        if (maxStack == 0) stub[0] = true;
                    }
                };
            }
        }, 0);
        return stub[0];
    }

    /** Replaces a JAR carrying the earlier stub with the newest backup of the same build. */
    static boolean restoreUnstubbedBackup(File gameDir, File jar) throws IOException {
        byte[] current = readEntry(jar, CLASS);
        if (current == null || !hasRefreshStub(current)) return false;
        File[] backups = new File(gameDir, "voxyquest-backups").listFiles(
                (dir, name) -> name.startsWith("Vivecraft-") && name.endsWith(".jar"));
        if (backups == null) return false;
        Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed());
        Set<String> entries = entryNames(jar);
        for (File backup : backups) {
            byte[] candidate = readEntry(backup, CLASS);
            if (candidate == null || hasRefreshStub(candidate) || !entries.equals(entryNames(backup))) continue;
            File temporary = File.createTempFile("vivecraft-restore-", ".tmp", jar.getParentFile());
            try {
                Files.copy(backup.toPath(), temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(temporary.toPath(), jar.toPath(), StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary.toPath(), jar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } finally { Files.deleteIfExists(temporary.toPath()); }
            return true;
        }
        return false;
    }

    private static byte[] readEntry(File archive, String name) {
        try (ZipFile zip = new ZipFile(archive)) {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null) return null;
            try (InputStream input = zip.getInputStream(entry)) { return input.readAllBytes(); }
        } catch (IOException e) {
            return null;
        }
    }

    private static Set<String> entryNames(File archive) throws IOException {
        Set<String> names = new HashSet<>();
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> all = zip.entries();
            while (all.hasMoreElements()) names.add(all.nextElement().getName());
        }
        return names;
    }

    /** OpenXR owns and allocates swapchain images; allocating them again raises GL_INVALID_OPERATION. */
    static byte[] patchSwapchainClass(byte[] original) throws IOException {
        ClassReader reader = new ClassReader(original);
        if (!(reader.getClassName() + ".class").equals(TEXTURE_CLASS))
            throw new IOException("Unexpected swapchain patch target");
        ClassWriter writer = new ClassWriter(0);
        int[] patched = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name,
                    String descriptor, String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("<init>")) return method;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override public void visitMethodInsn(int opcode, String owner, String methodName,
                            String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKEINTERFACE && isInterface &&
                                owner.equals("org/vivecraft/client/extensions/GlDeviceExtension") &&
                                methodName.equals("vivecraft$createFixedIdTexture")) {
                            methodName = "vivecraft$precreatedFixedIdTexture";
                            patched[0]++;
                        }
                        super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                    }
                };
            }
        }, 0);
        if (patched[0] != 1) throw new IOException("Unexpected Vivecraft swapchain constructor");
        return writer.toByteArray();
    }

    // 0: unknown, 1: original, 2: refresh fixed, 3: both fixed;
    // 4/5: older renderer with original/fixed refresh and no swapchain allocation call.
    private static int bundledNeoForgeState(File installed, InputStream bundled) throws IOException {
        try (ZipFile source = new ZipFile(installed);
             ZipInputStream asset = new ZipInputStream(bundled)) {
            ZipEntry descriptor = source.getEntry("META-INF/neoforge.mods.toml");
            ZipEntry target = source.getEntry(CLASS);
            ZipEntry texture = source.getEntry(TEXTURE_CLASS);
            if (descriptor == null || target == null || texture == null) return 0;
            byte[] metadata;
            try (InputStream input = source.getInputStream(descriptor)) {
                metadata = input.readNBytes(65537);
            }
            if (metadata.length > 65536 || !new String(metadata, java.nio.charset.StandardCharsets.UTF_8)
                    .contains("modId = \"vivecraft\"")) return 0;
            byte[] refreshBytes = null;
            byte[] textureBytes = null;
            ZipEntry entry;
            while ((entry = asset.getNextEntry()) != null) {
                if (CLASS.equals(entry.getName())) refreshBytes = asset.readAllBytes();
                if (TEXTURE_CLASS.equals(entry.getName())) textureBytes = asset.readAllBytes();
            }
            if (refreshBytes == null || textureBytes == null) return 0;
            byte[] actualRefresh, actualTexture;
            try (InputStream input = source.getInputStream(target)) { actualRefresh = input.readAllBytes(); }
            try (InputStream input = source.getInputStream(texture)) { actualTexture = input.readAllBytes(); }
            boolean refreshOriginal = Arrays.equals(actualRefresh, refreshBytes);
            boolean refreshPatched = Arrays.equals(actualRefresh, patchClass(refreshBytes));
            boolean textureOriginal = Arrays.equals(actualTexture, textureBytes);
            boolean hasSwapchainAllocation = hasSwapchainAllocation(textureBytes);
            boolean texturePatched = hasSwapchainAllocation &&
                    Arrays.equals(actualTexture, patchSwapchainClass(textureBytes));
            if (!hasSwapchainAllocation && textureOriginal) {
                if (refreshOriginal) return 4;
                if (refreshPatched) return 5;
            }
            if (refreshOriginal && textureOriginal) return 1;
            if (refreshPatched && textureOriginal) return 2;
            if (refreshPatched && texturePatched) return 3;
            return 0;
        }
    }

    private static boolean hasSwapchainAllocation(byte[] bytes) {
        boolean[] found = {false};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                            String descriptor, boolean isInterface) {
                        if (owner.equals("org/vivecraft/client/extensions/GlDeviceExtension") &&
                                method.equals("vivecraft$createFixedIdTexture")) found[0] = true;
                    }
                };
            }
        }, 0);
        return found[0];
    }

    private static String sha256(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) { return sha256(input); }
    }

    private static String sha256(InputStream input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[32768];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
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
