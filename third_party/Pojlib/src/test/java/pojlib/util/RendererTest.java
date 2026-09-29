package pojlib.util;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.Assert.*;

public class RendererTest {
    @Test public void unsetOrUnknownIdsUseMobileGlues() {
        assertEquals(Renderer.MOBILEGLUES, Renderer.fromId(null));
        assertEquals(Renderer.MOBILEGLUES, Renderer.fromId("vulkan"));
        assertEquals(Renderer.LTW, Renderer.fromId("ltw"));
    }

    @Test public void ltwKeepsItsOriginalEnvironment() throws Exception {
        Path data = Files.createTempDirectory("voxyquest-renderer-");
        Map<String, String> env = Renderer.LTW.environment(data.toFile());
        assertEquals("LightThinWrapper", env.get("POJLIB_RENDERER"));
        assertEquals("libltw.so", env.get("POJAVEXEC_EGL"));
        assertEquals("2", Renderer.LTW.glesVersion);
        assertFalse(env.containsKey("MG_DIR_PATH"));
        assertFalse(Files.exists(data.resolve("mobileglues")));
    }

    @Test public void mobileGluesGetsAPersistentShaderCacheWithoutOverwritingEdits() throws Exception {
        Path data = Files.createTempDirectory("voxyquest-renderer-");
        Map<String, String> env = Renderer.MOBILEGLUES.environment(data.toFile());
        Path config = data.resolve("mobileglues/config.json");
        assertEquals(data.resolve("mobileglues").toString(), env.get("MG_DIR_PATH"));
        assertEquals("libmobileglues.so", env.get("POJAVEXEC_EGL"));
        assertEquals("1", env.get("TGS_FAST_UPLOADS"));
        assertTrue(new String(Files.readAllBytes(config), StandardCharsets.UTF_8)
                .contains("\"maxGlslCacheSize\": " + Renderer.SHADER_CACHE_MB));
        Files.write(config, "{\"maxGlslCacheSize\": 8}".getBytes(StandardCharsets.UTF_8));
        Renderer.MOBILEGLUES.environment(data.toFile());
        assertEquals("{\"maxGlslCacheSize\": 8}", new String(Files.readAllBytes(config), StandardCharsets.UTF_8));
    }
}
