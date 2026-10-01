package pojlib.util;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Runs the patched Simulated tick against the stand-ins in compat/simulated/fakes. CI builds
 * them with scripts/build_simulated_vr_compat.sh and passes their directory and the helper.
 */
public class SimulatedVrFixTest {
    static final String MANAGER = "dev.simulated_team.simulated.util.hold_interaction.HoldInteractionManager";
    static final String HELPER = "dev.simulated_team.simulated.util.hold_interaction.VoxyQuestVrHold";
    Path fakes;
    byte[] helper;

    @Before public void locate() throws IOException {
        String dir = System.getenv("VOXYQUEST_SIMULATED_FAKES");
        String helperPath = System.getenv("VOXYQUEST_SIMULATED_HELPER");
        Assume.assumeTrue(dir != null && helperPath != null);
        fakes = Paths.get(dir);
        helper = Files.readAllBytes(Paths.get(helperPath));
    }

    byte[] originalManager() throws IOException {
        return Files.readAllBytes(fakes.resolve(SimulatedVrFix.MANAGER_CLASS));
    }

    /** Loads the fakes, with the patched manager and the helper in place of the originals. */
    ClassLoader game(byte[] manager) {
        Map<String, byte[]> defined = new HashMap<>();
        defined.put(MANAGER, manager);
        defined.put(HELPER, helper);
        return new ClassLoader(ClassLoader.getPlatformClassLoader()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = defined.get(name);
                try {
                    if (bytes == null) bytes = Files.readAllBytes(fakes.resolve(name.replace('.', '/') + ".class"));
                } catch (IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
    }

    static Object get(Object target, String field) throws ReflectiveOperationException {
        Field f = target.getClass().getField(field);
        return f.get(target);
    }

    static void set(Object target, String field, Object value) throws ReflectiveOperationException {
        target.getClass().getField(field).set(target, value);
    }

    @Test public void controllerRotationMovesHeldControlsAndReleaseLetsGo() throws Exception {
        ClassLoader game = game(SimulatedVrFix.patchManager(originalManager()));
        Class<?> manager = game.loadClass(MANAGER);
        Method tick = manager.getMethod("tick", Object.class, Object.class);
        Class<?> interactionType = game.loadClass("dev.simulated_team.simulated.util.hold_interaction.BlockHoldInteraction");
        Object interaction = game.loadClass("dev.simulated_team.simulated.util.hold_interaction.RecordingInteraction")
                .getConstructor().newInstance();
        game.loadClass("org.vivecraft.client_vr.VRState").getField("VR_RUNNING").setBoolean(null, true);
        Object minecraft = game.loadClass("net.minecraft.client.Minecraft").getMethod("getInstance").invoke(null);
        Object keyUse = get(get(minecraft, "options"), "keyUse");
        Object holder = game.loadClass("org.vivecraft.client_vr.ClientDataHolderVR").getMethod("getInstance").invoke(null);
        Object hand = get(get(get(holder, "vrPlayer"), "vrdata_room_pre"), "c0");
        set(keyUse, "down", true);
        manager.getMethod("start", interactionType).invoke(null, interaction);

        tick.invoke(null, null, null); // first tick only records where the hand is
        assertEquals(0, get(interaction, "moves"));
        set(hand, "pitch", 30f);  // tilt the controller up
        set(hand, "yaw", 170f);
        tick.invoke(null, null, null);
        set(hand, "yaw", -170f);  // and on across the ±180° seam
        tick.invoke(null, null, null);
        // Mouse units: 0.15° each, pitch positive downward as in Minecraft.
        assertEquals(-200.0, (double) get(interaction, "pitch"), 1e-3);
        assertEquals(170 / 0.15 + 20 / 0.15, (double) get(interaction, "yaw"), 1e-3);
        assertEquals(3, manager.getField("ticks").getInt(null));

        set(keyUse, "down", false);
        tick.invoke(null, null, null);
        assertEquals(1, get(interaction, "releases"));
        assertFalse((Boolean) manager.getMethod("isActive").invoke(null));
    }

    @Test public void desktopPlayIsUnchanged() throws Exception {
        ClassLoader game = game(SimulatedVrFix.patchManager(originalManager()));
        Class<?> manager = game.loadClass(MANAGER);
        Object interaction = game.loadClass("dev.simulated_team.simulated.util.hold_interaction.RecordingInteraction")
                .getConstructor().newInstance();
        manager.getMethod("start", game.loadClass("dev.simulated_team.simulated.util.hold_interaction.BlockHoldInteraction"))
                .invoke(null, interaction);
        manager.getMethod("tick", Object.class, Object.class).invoke(null, null, null);
        assertEquals(0, get(interaction, "releases"));
        assertTrue((Boolean) manager.getMethod("isActive").invoke(null));
    }

    static byte[] zip(Map<String, byte[]> entries, boolean stored) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                if (stored) {
                    CRC32 crc = new CRC32();
                    crc.update(e.getValue());
                    entry.setMethod(ZipEntry.STORED);
                    entry.setSize(e.getValue().length);
                    entry.setCompressedSize(e.getValue().length);
                    entry.setCrc(crc.getValue());
                }
                out.putNextEntry(entry);
                out.write(e.getValue());
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test public void patchesSimulatedNestedInTheAeronauticsBundleOnce() throws Exception {
        File game = Files.createTempDirectory("simulated-vr").toFile();
        File mods = new File(game, "mods");
        assertTrue(mods.mkdirs());
        String nestedName = "META-INF/jarjar/simulated-neoforge-1.21.1-1.3.2.jar";
        Map<String, byte[]> simulated = new LinkedHashMap<>();
        simulated.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes());
        simulated.put(SimulatedVrFix.MANAGER_CLASS, originalManager());
        Map<String, byte[]> bundle = new LinkedHashMap<>();
        bundle.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes());
        bundle.put(nestedName, zip(simulated, false));
        File jar = new File(mods, "aeronautics-bundled-1.21.1-1.3.2.jar");
        Files.write(jar.toPath(), zip(bundle, true));

        SimulatedVrFix.Result first = SimulatedVrFix.apply(game, helper);
        assertEquals(Collections.singletonList(jar.getName()), first.patched);
        byte[] nested = ModJarPatcher.readEntry(jar, nestedName);
        assertArrayEquals(helper, ModJarPatcher.readEntry(nested, SimulatedVrFix.HELPER + ".class"));
        assertTrue(SimulatedVrFix.isPatched(ModJarPatcher.readEntry(nested, SimulatedVrFix.MANAGER_CLASS)));
        try (ZipFile zip = new ZipFile(jar)) {
            assertEquals(ZipEntry.STORED, zip.getEntry(nestedName).getMethod());
        }
        assertTrue(new File(game, "voxyquest-backups/" + jar.getName()).isFile());

        assertTrue(SimulatedVrFix.apply(game, helper).patched.isEmpty());
        assertTrue(new File(game, SimulatedVrFix.STAMP).delete());
        assertTrue(SimulatedVrFix.apply(game, helper).patched.isEmpty());
    }

    @Test public void unfamiliarSimulatedIsLeftAlone() throws Exception {
        File game = Files.createTempDirectory("simulated-vr").toFile();
        File mods = new File(game, "mods");
        assertTrue(mods.mkdirs());
        // A manager without the static tick this patch hooks into.
        org.objectweb.asm.ClassWriter w = new org.objectweb.asm.ClassWriter(0);
        w.visit(org.objectweb.asm.Opcodes.V17, org.objectweb.asm.Opcodes.ACC_PUBLIC,
                SimulatedVrFix.MANAGER_CLASS.replace(".class", ""), null, "java/lang/Object", null);
        w.visitEnd();
        File jar = new File(mods, "simulated-1.21.1-9.9.9.jar");
        Files.write(jar.toPath(), zip(Collections.singletonMap(SimulatedVrFix.MANAGER_CLASS, w.toByteArray()), false));
        byte[] before = Files.readAllBytes(jar.toPath());
        SimulatedVrFix.Result result = SimulatedVrFix.apply(game, helper);
        assertTrue(result.patched.isEmpty());
        assertEquals(Collections.singletonList(jar.getName()), result.unsupported);
        assertArrayEquals(before, Files.readAllBytes(jar.toPath()));
    }
}
