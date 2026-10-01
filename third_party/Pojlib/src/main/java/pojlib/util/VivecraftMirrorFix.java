package pojlib.util;

import org.objectweb.asm.*;
import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
 * Stops Vivecraft redrawing a desktop mirror nobody sees.
 *
 * With the mirror off (PerformanceTuning sets it for VR), ShaderHelper.drawMirror posts a
 * "Mirror is OFF" notification every frame, and MirrorNotification.render then clears the whole
 * window-sized main target (2960x1440 on Quest 3, colour and depth) and draws that text into it.
 * The Quest never shows that target in VR, so this was a full-size clear and store per frame of
 * memory bandwidth taken from the eye images. This removes just that notify call; every other
 * mirror mode and notification is unchanged.
 */
public final class VivecraftMirrorFix {
    static final String CLASS = "org/vivecraft/client_vr/render/helpers/ShaderHelper.class";
    static final String NOTIFICATION = "org/vivecraft/client_vr/render/MirrorNotification";
    static final String TEXT = "Mirror is OFF";

    private VivecraftMirrorFix() {}

    /** @return whether Vivecraft.jar was changed by this launch */
    public static boolean apply(File gameDir) throws IOException {
        File jar = new File(gameDir, "mods/Vivecraft.jar");
        if (!jar.isFile()) return false;
        byte[] original = ModJarPatcher.readEntry(jar, CLASS);
        if (original == null || !postsOffNotice(original)) return false;
        ModJarPatcher.rewriteFile(gameDir, jar, Collections.singletonMap(CLASS, patch(original)));
        return true;
    }

    static byte[] patch(byte[] original) throws IOException {
        int[] removed = {0};
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(original).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("drawMirror") || !descriptor.equals("()V")) return method;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    private boolean offText;

                    @Override public void visitLdcInsn(Object value) {
                        offText = TEXT.equals(value);
                        super.visitLdcInsn(value);
                    }

                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                            String methodDescriptor, boolean isInterface) {
                        if (offText && opcode == Opcodes.INVOKESTATIC && owner.equals(NOTIFICATION) &&
                                method.equals("notify") && methodDescriptor.equals("(Ljava/lang/String;ZI)V")) {
                            // Drop the text, clear flag and duration instead of posting the notice.
                            super.visitInsn(Opcodes.POP2);
                            super.visitInsn(Opcodes.POP);
                            removed[0]++;
                        } else {
                            super.visitMethodInsn(opcode, owner, method, methodDescriptor, isInterface);
                        }
                        offText = false;
                    }
                };
            }
        }, 0);
        if (removed[0] != 1) throw new IOException("Unexpected Vivecraft mirror code");
        return writer.toByteArray();
    }

    /** True while drawMirror still posts the "Mirror is OFF" notice. */
    static boolean postsOffNotice(byte[] shaderHelper) {
        boolean[] found = {false};
        new ClassReader(shaderHelper).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                if (!name.equals("drawMirror")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    private boolean offText;

                    @Override public void visitLdcInsn(Object value) {
                        offText = TEXT.equals(value);
                    }

                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                            String methodDescriptor, boolean isInterface) {
                        if (offText && owner.equals(NOTIFICATION) && method.equals("notify")) found[0] = true;
                        offText = false;
                    }
                };
            }
        }, 0);
        return found[0];
    }
}
