package pojlib.util;

import org.objectweb.asm.*;
import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
 * Stops Vivecraft redrawing a desktop mirror nobody sees.
 *
 * With the mirror off (PerformanceTuning sets it for VR), ShaderHelper.drawMirror still drew to
 * the window-sized main target every frame (2960x1440 on Quest 3), which the Quest never shows
 * in VR: a full-size clear and store of memory bandwidth taken from the eye images.
 * Older Vivecraft posts a "Mirror is OFF" notice, and MirrorNotification.render then clears the
 * target and draws the text. Vivecraft for 1.21.8 and later posts a translated notice, or with
 * that notice turned off clears the target to black. In the mirror-off branch this drops the
 * notice and the clear; every other mirror mode and notice is unchanged.
 */
public final class VivecraftMirrorFix {
    static final String CLASS = "org/vivecraft/client_vr/render/helpers/ShaderHelper.class";
    static final String NOTIFICATION = "org/vivecraft/client_vr/render/MirrorNotification";
    static final String NOTIFY = "(Ljava/lang/String;ZI)V";
    static final String CLEAR = "clearColorTexture";

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

    /** Calls in the mirror-off branch that draw to the hidden window. */
    static boolean isWindowDraw(String owner, String method, String descriptor) {
        return (owner.equals(NOTIFICATION) && method.equals("notify") && descriptor.equals(NOTIFY)) ||
                (owner.endsWith("/CommandEncoder") && method.equals(CLEAR) && descriptor.endsWith(";I)V"));
    }

    /**
     * Tracks drawMirror's first "displayMirrorMode == OFF" test; the branch it guards runs until
     * the next mirror mode is tested.
     */
    private abstract static class OffBranch extends MethodVisitor {
        private int state; // 0 before the OFF test, 1 inside its branch, 2 after it

        OffBranch(MethodVisitor next) {
            super(Opcodes.ASM9, next);
        }

        boolean inside() {
            return state == 1;
        }

        @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            if (opcode == Opcodes.GETSTATIC && owner.endsWith("VRSettings$MirrorMode")) {
                if (state == 0 && name.equals("OFF")) state = 1;
                else if (state == 1 && !name.equals("OFF")) state = 2;
            }
            super.visitFieldInsn(opcode, owner, name, descriptor);
        }
    }

    static byte[] patch(byte[] original) throws IOException {
        int[] removed = {0};
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(original).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("drawMirror") || !descriptor.equals("()V")) return method;
                return new OffBranch(method) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                            String methodDescriptor, boolean isInterface) {
                        if (inside() && isWindowDraw(owner, method, methodDescriptor)) {
                            // Both take three one-slot values: text, flag, duration or
                            // encoder, texture, colour. Drop them instead of drawing.
                            super.visitInsn(Opcodes.POP2);
                            super.visitInsn(Opcodes.POP);
                            removed[0]++;
                        } else {
                            super.visitMethodInsn(opcode, owner, method, methodDescriptor, isInterface);
                        }
                    }
                };
            }
        }, 0);
        if (removed[0] == 0) throw new IOException("Unexpected Vivecraft mirror code");
        return writer.toByteArray();
    }

    /** True while drawMirror's mirror-off branch still draws to the hidden window. */
    static boolean postsOffNotice(byte[] shaderHelper) {
        boolean[] found = {false};
        new ClassReader(shaderHelper).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                if (!name.equals("drawMirror") || !descriptor.equals("()V")) return null;
                return new OffBranch(null) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                            String methodDescriptor, boolean isInterface) {
                        if (inside() && isWindowDraw(owner, method, methodDescriptor)) found[0] = true;
                    }
                };
            }
        }, 0);
        return found[0];
    }
}
