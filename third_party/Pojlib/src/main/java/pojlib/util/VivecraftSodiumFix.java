package pojlib.util;

import org.objectweb.asm.*;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Makes Vivecraft's VR arms use the right part of the skin with Sodium 0.8.
 *
 * Vivecraft's SodiumHelper knows only older Sodium model layouts. This adds VoxyQuestSodium
 * (third_party/Pojlib/compat/vivecraft) to Vivecraft and lets copyModelCuboidUV try it first;
 * it does the copy for Sodium 0.8 and declines for anything else, leaving Vivecraft's own code.
 */
public final class VivecraftSodiumFix {
    static final String CLASS = "org/vivecraft/mod_compat_vr/sodium/SodiumHelper.class";
    static final String HELPER = "org/vivecraft/mod_compat_vr/sodium/VoxyQuestSodium";
    static final String METHOD = "copyModelCuboidUV";
    static final String HELPER_DESCRIPTOR = "(Ljava/lang/Object;Ljava/lang/Object;II)Z";

    private VivecraftSodiumFix() {}

    /** @return whether Vivecraft.jar was changed by this launch */
    public static boolean apply(File gameDir, byte[] helper) throws IOException {
        File jar = new File(gameDir, "mods/Vivecraft.jar");
        if (!jar.isFile() || helper == null) return false;
        byte[] original = ModJarPatcher.readEntry(jar, CLASS);
        if (original == null || isPatched(original)) return false;
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(CLASS, patch(original));
        entries.put(HELPER + ".class", helper);
        ModJarPatcher.rewriteFile(gameDir, jar, entries);
        return true;
    }

    /** Starts copyModelCuboidUV with {@code if (VoxyQuestSodium.copyUV(...)) return;}. */
    static byte[] patch(byte[] original) throws IOException {
        ClassReader reader = new ClassReader(original);
        int[] patched = {0};
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                Type[] arguments = Type.getArgumentTypes(descriptor);
                if (!name.equals(METHOD) || (access & Opcodes.ACC_STATIC) == 0 || arguments.length != 4 ||
                        arguments[0].getSort() != Type.OBJECT || arguments[1].getSort() != Type.OBJECT ||
                        arguments[2] != Type.INT_TYPE || arguments[3] != Type.INT_TYPE ||
                        Type.getReturnType(descriptor) != Type.VOID_TYPE) return method;
                patched[0]++;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override public void visitCode() {
                        super.visitCode();
                        Label original = new Label();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitVarInsn(Opcodes.ILOAD, 2);
                        super.visitVarInsn(Opcodes.ILOAD, 3);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "copyUV", HELPER_DESCRIPTOR, false);
                        super.visitJumpInsn(Opcodes.IFEQ, original);
                        super.visitInsn(Opcodes.RETURN);
                        super.visitLabel(original);
                        // Same locals as on entry and an empty stack.
                        super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    }
                };
            }
        }, 0);
        if (patched[0] != 1) throw new IOException("Unexpected Vivecraft Sodium helper");
        return writer.toByteArray();
    }

    static boolean isPatched(byte[] helperClass) {
        boolean[] found = {false};
        new ClassReader(helperClass).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                            String methodDescriptor, boolean isInterface) {
                        if (owner.equals(HELPER)) found[0] = true;
                    }
                };
            }
        }, 0);
        return found[0];
    }
}
