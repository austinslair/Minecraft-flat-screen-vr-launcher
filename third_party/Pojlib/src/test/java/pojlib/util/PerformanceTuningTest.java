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
                "prioritizeChunkUpdates:1", "simulationDistance:4", "enableVsync:false");
        PerformanceTuning.apply(dir.toFile(), false);
        assertEquals(Arrays.asList("version:3218", "renderDistance:9", "glDebugVerbosity:0",
                "prioritizeChunkUpdates:0", "simulationDistance:5", "enableVsync:true"), options(dir));
        JsonObject vivecraft = JsonParser.parseString(new String(Files.readAllBytes(
                dir.resolve("config/vivecraft-client-config.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("NONE", vivecraft.get("menuWorldSelection").getAsString());
        assertEquals("true", vivecraft.get("vrEnabled").getAsString());
        assertEquals(PerformanceTuning.DEFAULTS_VERSION,
                PerformanceTuning.readVersion(dir.resolve(PerformanceTuning.MARKER).toFile()));
    }

    @Test public void laterLaunchesKeepPlayerChoices() throws Exception {
        Path dir = instance("glDebugVerbosity:1");
        PerformanceTuning.apply(dir.toFile(), true);
        Files.write(dir.resolve("options.txt"), Arrays.asList("glDebugVerbosity:2", "simulationDistance:8"),
                StandardCharsets.UTF_8);
        PerformanceTuning.apply(dir.toFile(), true);
        assertEquals(Arrays.asList("glDebugVerbosity:2", "simulationDistance:8", "enableVsync:false"), options(dir));
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
