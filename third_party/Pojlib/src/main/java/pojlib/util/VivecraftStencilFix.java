package pojlib.util;

import org.objectweb.asm.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stops Vivecraft shading the parts of each eye the Quest's lenses hide.
 *
 * Vivecraft masks the hidden area before drawing each eye when its renderer provides a stencil
 * mesh, but its OpenXR renderer always answers that it has none. This adds VoxyQuestStencil
 * (third_party/Pojlib/compat/vivecraft) to Vivecraft and routes OpenXRStereoRenderer's
 * providesStencilMask and getStencilMask to it; it reads the mesh from XR_KHR_visibility_mask,
 * which VoxyQuestXr enables, so that copy of VoxyQuestXr is brought up to date as well.
 */
public final class VivecraftStencilFix {
    static final String PACKAGE = "org/vivecraft/client_vr/provider/openxr/";
    static final String RENDERER = PACKAGE + "OpenXRStereoRenderer";
    static final String MCOPENXR = "L" + PACKAGE + "MCOpenXR;";
    static final String BASE = "org/vivecraft/client_vr/provider/VRRenderer";
    static final String GET_MASK_DESCRIPTOR = "(Lorg/vivecraft/client_vr/render/RenderPass;)[F";
    static final String HELPER = PACKAGE + "VoxyQuestStencil";
    static final String XR_HELPER = PACKAGE + "VoxyQuestXr.class";

    private VivecraftStencilFix() {}

    /** @return whether Vivecraft.jar was changed by this launch */
    public static boolean apply(File gameDir, byte[] xrHelper, byte[] stencilHelper) throws IOException {
        File jar = new File(gameDir, "mods/Vivecraft.jar");
        if (!jar.isFile() || stencilHelper == null) return false;
        byte[] renderer = ModJarPatcher.readEntry(jar, RENDERER + ".class");
        byte[] base = ModJarPatcher.readEntry(jar, BASE + ".class");
        if (renderer == null || base == null || !declares(base, "getStencilMask", GET_MASK_DESCRIPTOR)) return false;
        Map<String, byte[]> entries = new LinkedHashMap<>();
        if (!isPatched(renderer)) entries.put(RENDERER + ".class", patch(renderer));
        if (!Arrays.equals(stencilHelper, ModJarPatcher.readEntry(jar, HELPER + ".class")))
            entries.put(HELPER + ".class", stencilHelper);
        // Only refresh a VoxyQuestXr the performance patch already wired in.
        byte[] currentXr = ModJarPatcher.readEntry(jar, XR_HELPER);
        if (xrHelper != null && currentXr != null && !Arrays.equals(xrHelper, currentXr)) entries.put(XR_HELPER, xrHelper);
        if (entries.isEmpty()) return false;
        ModJarPatcher.rewriteFile(gameDir, jar, entries);
        return true;
    }

    /** Both stencil methods of OpenXRStereoRenderer delegate to VoxyQuestStencil with its MCOpenXR. */
    static byte[] patch(byte[] original) throws IOException {
        ClassReader reader = new ClassReader(original);
        if (!reader.getClassName().equals(RENDERER) || !reader.getSuperName().equals(BASE))
            throw new IOException("Unexpected Vivecraft OpenXR renderer");
        String[] openxrField = {null};
        int[] replaced = {0};
        boolean[] hadGetMask = {false};
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        try {
            reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public FieldVisitor visitField(int access, String name, String descriptor,
                        String signature, Object value) {
                    if (descriptor.equals(MCOPENXR) && (access & Opcodes.ACC_STATIC) == 0) openxrField[0] = name;
                    return super.visitField(access, name, descriptor, signature, value);
                }

                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    boolean provides = name.equals("providesStencilMask") && descriptor.equals("()Z");
                    boolean getMask = name.equals("getStencilMask") && descriptor.equals(GET_MASK_DESCRIPTOR);
                    if ((!provides && !getMask) || (access & Opcodes.ACC_STATIC) != 0)
                        return super.visitMethod(access, name, descriptor, signature, exceptions);
                    if (openxrField[0] == null) throw new IllegalStateException("no MCOpenXR field");
                    hadGetMask[0] |= getMask;
                    replaced[0]++;
                    emit(super.visitMethod(access & ~Opcodes.ACC_ABSTRACT, name, descriptor, signature, exceptions),
                            getMask, openxrField[0]);
                    return null; // drop the original body
                }

                @Override public void visitEnd() {
                    if (!hadGetMask[0] && openxrField[0] != null) {
                        emit(super.visitMethod(Opcodes.ACC_PUBLIC, "getStencilMask", GET_MASK_DESCRIPTOR, null, null),
                                true, openxrField[0]);
                    }
                    super.visitEnd();
                }
            }, 0);
        } catch (RuntimeException e) {
            throw new IOException("Unexpected Vivecraft OpenXR renderer", e);
        }
        if (replaced[0] < 1 || openxrField[0] == null) throw new IOException("Unexpected Vivecraft OpenXR renderer");
        return writer.toByteArray();
    }

    private static void emit(MethodVisitor method, boolean getMask, String field) {
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, RENDERER, field, MCOPENXR);
        if (getMask) {
            method.visitVarInsn(Opcodes.ALOAD, 1);
            method.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "mask",
                    "(Ljava/lang/Object;Ljava/lang/Object;)[F", false);
            method.visitInsn(Opcodes.ARETURN);
        } else {
            method.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "provides", "(Ljava/lang/Object;)Z", false);
            method.visitInsn(Opcodes.IRETURN);
        }
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    static boolean isPatched(byte[] renderer) {
        boolean[] found = {false};
        new ClassReader(renderer).accept(new ClassVisitor(Opcodes.ASM9) {
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

    private static boolean declares(byte[] type, String method, String descriptor) {
        boolean[] found = {false};
        new ClassReader(type).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc,
                    String signature, String[] exceptions) {
                if (name.equals(method) && desc.equals(descriptor)) found[0] = true;
                return null;
            }
        }, ClassReader.SKIP_CODE);
        return found[0];
    }
}
