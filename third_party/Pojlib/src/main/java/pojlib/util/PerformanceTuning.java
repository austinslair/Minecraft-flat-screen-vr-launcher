package pojlib.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Performance settings applied to an instance just before Minecraft starts.
 *
 * One-time defaults are written once per {@link #DEFAULTS_VERSION}, so instances installed
 * before they existed get them, and later changes made in-game are kept.
 */
public final class PerformanceTuning {
    static final int DEFAULTS_VERSION = 4;
    static final String MARKER = "config/voxyquest-performance-defaults";
    /** Minecraft rejects lower values and silently falls back to its default of 12. */
    static final int MIN_SIMULATION_DISTANCE = 5;
    /**
     * The Quest gives Java 3 CPU threads, shared by the render thread, the integrated server,
     * chunk builders and the collector. Minecraft's default of 12 ticks 625 chunks; 8 ticks 289.
     * Render distance is separate and unaffected.
     */
    static final int DEFAULT_MAX_SIMULATION_DISTANCE = 8;
    /**
     * Biome colour blending runs per vertex while chunks are meshed; the default 5x5 (radius 2)
     * costs far more than 3x3 for a barely visible difference.
     */
    static final int DEFAULT_MAX_BIOME_BLEND = 1;
    /**
     * Sodium meshes chunks on max(cores / 3, cores - 6) threads, one on the Quest's three. A
     * second builder roughly doubles how fast terrain fills in; builders run below the render
     * thread's priority, so frames still come first.
     */
    static final int SODIUM_CHUNK_BUILDERS = 2;

    private PerformanceTuning() {}

    public static void apply(File gameDir, boolean vr) throws IOException {
        File marker = new File(gameDir, MARKER);
        int version = readVersion(marker);
        File optionsFile = new File(gameDir, "options.txt");
        // Minecraft writes options.txt on its first run. Until then, keep the defaults pending
        // so a new instance gets them on its second launch instead of never.
        boolean haveOptions = optionsFile.isFile();

        Map<String, String> options = new LinkedHashMap<>();
        if (version < 1) {
            // GL debug output makes the GLES driver validate and report on every call.
            options.put("glDebugVerbosity", "0");
            // Rebuild nearby chunks on worker threads instead of stalling the render thread.
            options.put("prioritizeChunkUpdates", "0");
        }
        if (version < 2) {
            // "Unlimited". A 90 fps cap held VR below a 120 Hz headset and flat mode below
            // fast displays; vsync (flat) and OpenXR (VR) already pace frames to the display.
            options.put("maxFps", "260");
        }
        // With a swap interval of 0 Android discards frames that never reach the display, so
        // flat mode renders (and heats the device) for nothing. OpenXR paces VR frames itself.
        options.put("enableVsync", Boolean.toString(!vr));
        Map<String, Integer> maxima = new LinkedHashMap<>();
        if (version < 3) maxima.put("simulationDistance", DEFAULT_MAX_SIMULATION_DISTANCE);
        if (version < 4) maxima.put("biomeBlendRadius", DEFAULT_MAX_BIOME_BLEND);
        updateOptions(optionsFile, options, maxima);

        if (version < DEFAULTS_VERSION) {
            File vivecraft = new File(gameDir, "config/vivecraft-client-config.json");
            if (version < 1) {
                // The VR menu world is a downloaded, fully rendered level; the panorama is not.
                updateJson(gameDir, vivecraft, "menuWorldSelection", "NONE");
            }
            if (version < 3) {
                // The desktop mirror is invisible on a headset, but Vivecraft still copies an
                // eye to it every frame, and its first/third person modes render the world again.
                updateJson(gameDir, vivecraft, "displayMirrorMode", "OFF");
            }
            if (version < 4) {
                // Only "auto" (0) is replaced; a thread count chosen in Sodium's settings stays.
                updateSodiumBuilders(new File(gameDir, "config/sodium-options.json"));
            }
            if (haveOptions) {
                Files.createDirectories(marker.getParentFile().toPath());
                writeAtomically(marker, Integer.toString(DEFAULTS_VERSION));
            }
        }
    }

    static int readVersion(File marker) {
        if (!marker.isFile()) return 0;
        try {
            return Integer.parseInt(new String(Files.readAllBytes(marker.toPath()),
                    StandardCharsets.UTF_8).trim());
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Replaces or appends {@code key:value} lines, keeping every other line as it was. Numeric
     * options named in {@code maxima} are lowered to that value, and simulationDistance is kept
     * at or above {@link #MIN_SIMULATION_DISTANCE}.
     */
    static void updateOptions(File file, Map<String, String> overrides, Map<String, Integer> maxima)
            throws IOException {
        // Minecraft creates a missing options.txt itself; a partial one would lose its defaults.
        if (!file.isFile()) return;
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        Map<String, String> pending = new LinkedHashMap<>(overrides);
        List<String> result = new ArrayList<>(lines.size() + pending.size());
        boolean changed = false;
        for (String line : lines) {
            int colon = line.indexOf(':');
            String key = colon < 0 ? null : line.substring(0, colon);
            String value = key == null ? null : pending.remove(key);
            if (value == null && key != null) {
                int floor = "simulationDistance".equals(key) ? MIN_SIMULATION_DISTANCE : Integer.MIN_VALUE;
                Integer ceiling = maxima.get(key);
                if (ceiling != null || floor != Integer.MIN_VALUE) {
                    int current = parseInt(line.substring(colon + 1), Math.max(floor, 0));
                    int clamped = Math.max(floor, Math.min(current, ceiling == null ? Integer.MAX_VALUE : ceiling));
                    if (clamped != current) value = Integer.toString(clamped);
                }
            }
            String updated = value == null ? line : key + ":" + value;
            changed |= !updated.equals(line);
            result.add(updated);
        }
        for (Map.Entry<String, String> entry : pending.entrySet()) {
            result.add(entry.getKey() + ":" + entry.getValue());
            changed = true;
        }
        if (changed) writeAtomically(file, String.join("\n", result) + "\n");
    }

    static void updateJson(File gameDir, File file, String key, String value) throws IOException {
        if (!file.isFile()) {
            // Vivecraft only imports its legacy profile file when this one is missing.
            if (new File(gameDir, "optionsviveprofiles.txt").exists()) return;
            Files.createDirectories(file.getParentFile().toPath());
            JsonObject created = new JsonObject();
            created.addProperty(key, value);
            writeAtomically(file, GsonUtils.GLOBAL_GSON.toJson(created));
            return;
        }
        JsonElement parsed;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            parsed = JsonParser.parseReader(reader);
        } catch (RuntimeException e) {
            return; // Leave a config we cannot parse for the mod to handle.
        }
        if (!parsed.isJsonObject()) return;
        JsonObject object = parsed.getAsJsonObject();
        object.addProperty(key, value);
        writeAtomically(file, GsonUtils.GLOBAL_GSON.toJson(object));
    }

    static void updateSodiumBuilders(File file) throws IOException {
        if (!file.isFile()) return;
        JsonElement parsed;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            parsed = JsonParser.parseReader(reader);
        } catch (RuntimeException e) {
            return; // Sodium rewrites a config it cannot parse.
        }
        if (!parsed.isJsonObject()) return;
        JsonObject root = parsed.getAsJsonObject();
        JsonObject performance = root.has("performance") && root.get("performance").isJsonObject()
                ? root.getAsJsonObject("performance") : new JsonObject();
        JsonElement current = performance.get("chunk_builder_threads");
        if (current != null && !(current.isJsonPrimitive() && current.getAsJsonPrimitive().isNumber()
                && current.getAsInt() == 0)) return;
        performance.addProperty("chunk_builder_threads", SODIUM_CHUNK_BUILDERS);
        root.add("performance", performance);
        writeAtomically(file, GsonUtils.GLOBAL_GSON.toJson(root));
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void writeAtomically(File destination, String text) throws IOException {
        File temp = File.createTempFile(destination.getName() + "-", ".tmp", destination.getParentFile());
        try {
            Files.write(temp.toPath(), text.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp.toPath(), destination.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }
}
