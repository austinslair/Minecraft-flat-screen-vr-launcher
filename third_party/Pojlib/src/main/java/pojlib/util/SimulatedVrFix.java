package pojlib.util;

import org.objectweb.asm.*;
import java.io.*;
import java.util.*;

/**
 * Makes Create Aeronautics' held controls usable in VR.
 *
 * Simulated, the library Create Aeronautics is built on, moves the physics assembler lever,
 * throttle lever and steering wheel with mouse movement while use is held, and lets go when
 * the mouse button is released. Vivecraft provides neither, so in VR the assembler lever could
 * not be pulled and contraptions could not be assembled. This adds VoxyQuestVrHold
 * (third_party/Pojlib/compat/simulated) to Simulated and calls it at the start of every
 * HoldInteractionManager tick; it translates the main controller's rotation into that movement.
 */
public final class SimulatedVrFix {
    static final String PACKAGE = "dev/simulated_team/simulated/util/hold_interaction/";
    static final String MANAGER_CLASS = PACKAGE + "HoldInteractionManager.class";
    static final String INTERACTION = PACKAGE + "BlockHoldInteraction";
    static final String HELPER = PACKAGE + "VoxyQuestVrHold";
    static final String STAMP = "voxyquest-backups/simulated-vr-v1.stamp";

    private SimulatedVrFix() {}

    /** Result of one launch's check: mod JARs patched now, and ones whose Simulated looked unfamiliar. */
    public static final class Result {
        public final List<String> patched = new ArrayList<>();
        public final List<String> unsupported = new ArrayList<>();
    }

    public static Result apply(File gameDir, byte[] helper) {
        Result result = new Result();
        File stamp = new File(gameDir, STAMP);
        Set<String> verified = ModJarPatcher.readStamp(stamp);
        List<String> identities = new ArrayList<>();
        for (File jar : ModJarPatcher.modJars(gameDir)) {
            String identity = ModJarPatcher.identity(jar);
            if (!verified.contains(identity)) {
                try {
                    if (patchJar(gameDir, jar, helper)) {
                        result.patched.add(jar.getName());
                        identity = ModJarPatcher.identity(jar);
                    }
                } catch (IOException e) {
                    // Another Simulated release; leave it as the mod shipped it.
                    result.unsupported.add(jar.getName());
                }
            }
            identities.add(identity);
        }
        ModJarPatcher.writeStamp(stamp, identities);
        return result;
    }

    /** Patches Simulated whether it is the mod JAR itself or nested in a bundle such as Aeronautics. */
    private static boolean patchJar(File gameDir, File jar, byte[] helper) throws IOException {
        byte[] manager = ModJarPatcher.readEntry(jar, MANAGER_CLASS);
        if (manager != null) {
            if (isPatched(manager)) return false;
            ModJarPatcher.rewriteFile(gameDir, jar, patchedEntries(manager, helper));
            return true;
        }
        Map<String, byte[]> nestedReplacements = new LinkedHashMap<>();
        for (String nestedName : ModJarPatcher.nestedJars(jar, "simulated")) {
            byte[] nested = ModJarPatcher.readEntry(jar, nestedName);
            byte[] nestedManager = ModJarPatcher.readEntry(nested, MANAGER_CLASS);
            if (nestedManager == null || isPatched(nestedManager)) continue;
            Map<String, byte[]> entries = patchedEntries(nestedManager, helper);
            byte[] helperEntry = entries.remove(HELPER + ".class");
            nestedReplacements.put(nestedName, ModJarPatcher.rewrite(nested, entries,
                    Collections.singletonMap(HELPER + ".class", helperEntry)));
        }
        if (nestedReplacements.isEmpty()) return false;
        ModJarPatcher.rewriteFile(gameDir, jar, nestedReplacements);
        return true;
    }

    private static Map<String, byte[]> patchedEntries(byte[] manager, byte[] helper) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(MANAGER_CLASS, patchManager(manager));
        entries.put(HELPER + ".class", helper);
        return entries;
    }

    /** Calls {@code VoxyQuestVrHold.tick(active)} first thing in {@code HoldInteractionManager.tick}. */
    static byte[] patchManager(byte[] original) throws IOException {
        ClassReader reader = new ClassReader(original);
        String owner = reader.getClassName();
        if (!(owner + ".class").equals(MANAGER_CLASS)) throw new IOException("Unexpected Simulated class");
        boolean[] hasActive = {false};
        int[] patched = {0};
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public FieldVisitor visitField(int access, String name, String descriptor,
                    String signature, Object value) {
                if (name.equals("active") && descriptor.equals("L" + INTERACTION + ";") &&
                        (access & Opcodes.ACC_STATIC) != 0) hasActive[0] = true;
                return super.visitField(access, name, descriptor, signature, value);
            }

            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("tick") || (access & Opcodes.ACC_STATIC) == 0 ||
                        Type.getArgumentTypes(descriptor).length != 2 ||
                        Type.getReturnType(descriptor) != Type.VOID_TYPE) return method;
                patched[0]++;
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override public void visitCode() {
                        super.visitCode();
                        super.visitFieldInsn(Opcodes.GETSTATIC, owner, "active", "L" + INTERACTION + ";");
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "tick",
                                "(L" + INTERACTION + ";)V", false);
                    }
                };
            }
        }, 0);
        if (patched[0] != 1 || !hasActive[0]) throw new IOException("Unexpected Simulated hold interaction manager");
        return writer.toByteArray();
    }

    static boolean isPatched(byte[] manager) {
        boolean[] found = {false};
        new ClassReader(manager).accept(new ClassVisitor(Opcodes.ASM9) {
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
