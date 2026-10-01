package pojlib.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class PerformanceTuningTest {
    private static List<String> options(Path dir) throws Exception {
        return Files.readAllLines(dir.resolve("options.txt"), StandardCharsets.UTF_8);
    }

    private static Path instance(String... options) throws Exception {
        Path dir = Files.createTempDirectory("voxyquest-tuning-");
        Files.write(dir.resolve("options.txt"), Arrays.asList(options), StandardCharsets.UTF_8);
        Files.createDirectories(dir.resolve("config"));
        Files.write(dir.resolve("config/vivecraft-client-config.json"),
                "{\"menuWorldSelection\": \"BOTH\", \"vrEnabled\": \"true\"}".getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    @Test public void firstLaunchAppliesDefaultsAndKeepsOtherSettings() throws Exception {
        Path dir = instance("version:3218", "renderDistance:9", "glDebugVerbosity:1",
                "prioritizeChunkUpdates:1", "simulationDistance:4", "maxFps:90", "enableVsync:false");
        PerformanceTuning.apply(dir.toFile(), false);
        assertEquals(Arrays.asList("version:3218", "renderDistance:9", "glDebugVerbosity:0",
                "prioritizeChunkUpdates:0", "simulationDistance:5", "maxFps:260", "enableVsync:true"), options(dir));
        JsonObject vivecraft = JsonParser.parseString(new String(Files.readAllBytes(
                dir.resolve("config/vivecraft-client-config.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("NONE", vivecraft.get("menuWorldSelection").getAsString());
        assertEquals("true", vivecraft.get("vrEnabled").getAsString());
        assertEquals("OFF", vivecraft.get("displayMirrorMode").getAsString());
        assertEquals(PerformanceTuning.DEFAULTS_VERSION,
                PerformanceTuning.readVersion(dir.resolve(PerformanceTuning.MARKER).toFile()));
    }

    @Test public void laterLaunchesKeepPlayerChoices() throws Exception {
        Path dir = instance("glDebugVerbosity:1");
        PerformanceTuning.apply(dir.toFile(), true);
        Files.write(dir.resolve("options.txt"), Arrays.asList("glDebugVerbosity:2", "simulationDistance:12", "maxFps:60"),
                StandardCharsets.UTF_8);
        PerformanceTuning.apply(dir.toFile(), true);
        assertEquals(Arrays.asList("glDebugVerbosity:2", "simulationDistance:12", "maxFps:60", "enableVsync:false"), options(dir));
    }

    @Test public void instancesFromTheFirstUpgradeOnlyGetLaterDefaults() throws Exception {
        Path dir = instance("glDebugVerbosity:2", "maxFps:90", "simulationDistance:12");
        Files.write(dir.resolve(PerformanceTuning.MARKER), "1".getBytes(StandardCharsets.UTF_8));
        PerformanceTuning.apply(dir.toFile(), false);
        assertEquals(Arrays.asList("glDebugVerbosity:2", "maxFps:260", "simulationDistance:8", "enableVsync:true"),
                options(dir));
        JsonObject vivecraft = vivecraft(dir);
        assertEquals("BOTH", vivecraft.get("menuWorldSelection").getAsString());
        assertEquals("OFF", vivecraft.get("displayMirrorMode").getAsString());
        assertEquals(PerformanceTuning.DEFAULTS_VERSION, PerformanceTuning.readVersion(dir.resolve(PerformanceTuning.MARKER).toFile()));
    }

    @Test public void newInstancesGetDefaultsOnceMinecraftHasWrittenOptions() throws Exception {
        Path dir = Files.createTempDirectory("voxyquest-tuning-");
        PerformanceTuning.apply(dir.toFile(), true);
        assertFalse(new File(dir.toFile(), "options.txt").exists());
        assertEquals("OFF", vivecraft(dir).get("displayMirrorMode").getAsString());
        assertEquals("NONE", vivecraft(dir).get("menuWorldSelection").getAsString());
        assertEquals(0, PerformanceTuning.readVersion(dir.resolve(PerformanceTuning.MARKER).toFile()));

        Files.write(dir.resolve("options.txt"), Arrays.asList("version:3955", "simulationDistance:12", "maxFps:120"),
                StandardCharsets.UTF_8);
        PerformanceTuning.apply(dir.toFile(), true);
        assertEquals(Arrays.asList("version:3955", "simulationDistance:8", "maxFps:260",
                "glDebugVerbosity:0", "prioritizeChunkUpdates:0", "enableVsync:false"), options(dir));
        assertEquals(PerformanceTuning.DEFAULTS_VERSION, PerformanceTuning.readVersion(dir.resolve(PerformanceTuning.MARKER).toFile()));
    }

    @Test public void legacyVivecraftProfilesAreLeftForVivecraftToImport() throws Exception {
        Path dir = Files.createTempDirectory("voxyquest-tuning-");
        Files.write(dir.resolve("optionsviveprofiles.txt"), "{}".getBytes(StandardCharsets.UTF_8));
        PerformanceTuning.apply(dir.toFile(), true);
        assertFalse(dir.resolve("config/vivecraft-client-config.json").toFile().exists());
    }

    @Test public void chunkLoadingDefaultsApplyOnceAndKeepChoices() throws Exception {
        Path dir = instance("biomeBlendRadius:2", "renderDistance:16");
        Files.write(dir.resolve(PerformanceTuning.MARKER), "3".getBytes(StandardCharsets.UTF_8));
        Path sodium = dir.resolve("config/sodium-options.json");
        Files.write(sodium, "{\"quality\":{\"enable_vignette\":false},\"performance\":{\"chunk_builder_threads\":0}}"
                .getBytes(StandardCharsets.UTF_8));
        PerformanceTuning.apply(dir.toFile(), true);
        assertEquals(Arrays.asList("biomeBlendRadius:1", "renderDistance:16", "enableVsync:false"), options(dir));
        JsonObject options = JsonParser.parseString(new String(Files.readAllBytes(sodium), StandardCharsets.UTF_8))
                .getAsJsonObject();
        assertEquals(2, options.getAsJsonObject("performance").get("chunk_builder_threads").getAsInt());
        assertFalse(options.getAsJsonObject("quality").get("enable_vignette").getAsBoolean());

        // Choices made afterwards survive later launches.
        Files.write(dir.resolve("options.txt"), Arrays.asList("biomeBlendRadius:3"), StandardCharsets.UTF_8);
        Files.write(sodium, "{\"performance\":{\"chunk_builder_threads\":0}}".getBytes(StandardCharsets.UTF_8));
        PerformanceTuning.apply(dir.toFile(), true);
        assertEquals(Arrays.asList("biomeBlendRadius:3", "enableVsync:false"), options(dir));
        assertTrue(new String(Files.readAllBytes(sodium), StandardCharsets.UTF_8).contains("\"chunk_builder_threads\":0"));
    }

    @Test public void chosenSodiumThreadCountIsKept() throws Exception {
        Path dir = instance("renderDistance:16");
        Path sodium = dir.resolve("config/sodium-options.json");
        Files.write(sodium, "{\"performance\":{\"chunk_builder_threads\":1}}".getBytes(StandardCharsets.UTF_8));
        PerformanceTuning.apply(dir.toFile(), true);
        assertTrue(new String(Files.readAllBytes(sodium), StandardCharsets.UTF_8).contains("\"chunk_builder_threads\":1"));
    }

    private static JsonObject vivecraft(Path dir) throws Exception {
        return JsonParser.parseString(new String(Files.readAllBytes(
                dir.resolve("config/vivecraft-client-config.json")), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test public void vsyncFollowsPlayModeEveryLaunch() throws Exception {
        Path dir = instance("enableVsync:true");
        PerformanceTuning.apply(dir.toFile(), true);
        assertTrue(options(dir).contains("enableVsync:false"));
        PerformanceTuning.apply(dir.toFile(), false);
        assertTrue(options(dir).contains("enableVsync:true"));
    }

    @Test public void missingOptionsFileIsLeftForMinecraftToCreate() throws Exception {
        Path dir = Files.createTempDirectory("voxyquest-tuning-");
        PerformanceTuning.apply(dir.toFile(), false);
        assertFalse(new File(dir.toFile(), "options.txt").exists());
    }
}
