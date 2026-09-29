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
    static final int DEFAULTS_VERSION = 2;
    static final String MARKER = "config/voxyquest-performance-defaults";
    /** Minecraft rejects lower values and silently falls back to its default of 12. */
    static final int MIN_SIMULATION_DISTANCE = 5;

    private PerformanceTuning() {}

    public static void apply(File gameDir, boolean vr) throws IOException {
        File marker = new File(gameDir, MARKER);
        int version = readVersion(marker);

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
        updateOptions(new File(gameDir, "options.txt"), options);

        if (version < DEFAULTS_VERSION) {
            if (version < 1) {
                // The VR menu world is a downloaded, fully rendered level; the panorama is not.
                updateJson(new File(gameDir, "config/vivecraft-client-config.json"),
                        "menuWorldSelection", "NONE");
            }
            Files.createDirectories(marker.getParentFile().toPath());
            writeAtomically(marker, Integer.toString(DEFAULTS_VERSION));
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

    /** Replaces or appends {@code key:value} lines, keeping every other line as it was. */
    static void updateOptions(File file, Map<String, String> overrides) throws IOException {
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
            if (value == null && "simulationDistance".equals(key)
                    && parseInt(line.substring(colon + 1), MIN_SIMULATION_DISTANCE) < MIN_SIMULATION_DISTANCE) {
                value = Integer.toString(MIN_SIMULATION_DISTANCE);
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

    static void updateJson(File file, String key, String value) throws IOException {
        if (!file.isFile()) return;
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
