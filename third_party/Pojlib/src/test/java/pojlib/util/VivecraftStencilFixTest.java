package pojlib.util;

import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.*;

/**
 * Patches a real Quest Vivecraft release. CI points VOXYQUEST_VIVECRAFT_TEST_JAR at it and
 * VOXYQUEST_VIVECRAFT_STENCIL_HELPER at the helper scripts/build_vivecraft_compat.sh compiled.
 */
public class VivecraftStencilFixTest {
    @Test public void routesOpenXrStencilMethodsToTheHelperOnce() throws Exception {
        String jar = System.getenv("VOXYQUEST_VIVECRAFT_TEST_JAR");
        String helperPath = System.getenv("VOXYQUEST_VIVECRAFT_STENCIL_HELPER");
        Assume.assumeTrue(jar != null && helperPath != null);
        byte[] helper = Files.readAllBytes(Paths.get(helperPath));
        File game = Files.createTempDirectory("stencil-test").toFile();
        File mods = new File(game, "mods");
        assertTrue(mods.mkdirs());
        File vivecraft = new File(mods, "Vivecraft.jar");
        Files.copy(Paths.get(jar), vivecraft.toPath());

        assertTrue(VivecraftStencilFix.apply(game, null, helper));
        byte[] renderer = ModJarPatcher.readEntry(vivecraft, VivecraftStencilFix.RENDERER + ".class");
        assertTrue(VivecraftStencilFix.isPatched(renderer));
        assertArrayEquals(helper, ModJarPatcher.readEntry(vivecraft, VivecraftStencilFix.HELPER + ".class"));
        assertFalse("second launch must not rewrite the jar", VivecraftStencilFix.apply(game, null, helper));
        assertTrue(new File(game, "voxyquest-backups/Vivecraft.jar").isFile());
    }
}
