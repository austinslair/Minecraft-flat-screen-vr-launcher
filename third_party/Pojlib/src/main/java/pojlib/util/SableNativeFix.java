package pojlib.util;

import org.objectweb.asm.*;
import java.io.*;
import java.util.*;

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
        // Checked JARs stay valid until they change. Avoid reopening every mod on each launch.
        File stamp = new File(gameDir, STAMP);
        Set<String> verified = ModJarPatcher.readStamp(stamp);
        List<String> identities = new ArrayList<>();
        for (File jar : ModJarPatcher.modJars(gameDir)) {
            String identity = ModJarPatcher.identity(jar);
            if (!verified.contains(identity)) {
                String nested = rapierJar(jar);
                if (nested != null) {
                    if (!nested.endsWith("-" + SUPPORTED_VERSION + ".jar")) {
                        result.unsupported.add(jar.getName());
                    } else if (patchJar(gameDir, jar, nested)) {
                        result.patched.add(jar.getName());
                        identity = ModJarPatcher.identity(jar);
                    }
                }
            }
            identities.add(identity);
        }
        ModJarPatcher.writeStamp(stamp, identities);
        return result;
    }

    /** The nested Rapier JAR inside a Sable mod JAR, found by name under either loader's layout. */
    static String rapierJar(File jar) {
        List<String> nested = ModJarPatcher.nestedJars(jar, "sable_rapier");
        return nested.isEmpty() ? null : nested.get(0);
    }

    private static boolean patchJar(File gameDir, File jar, String nestedName) throws IOException {
        byte[] nested = ModJarPatcher.readEntry(jar, nestedName);
        byte[] loader = ModJarPatcher.readEntry(nested, LOADER_CLASS);
        if (loader == null || isPatched(loader)) return false;
        byte[] patchedNested = ModJarPatcher.rewrite(nested,
                Collections.singletonMap(LOADER_CLASS, patchLoader(loader)), Collections.emptyMap());
        ModJarPatcher.rewriteFile(gameDir, jar, Collections.singletonMap(nestedName, patchedNested));
        return true;
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
}
