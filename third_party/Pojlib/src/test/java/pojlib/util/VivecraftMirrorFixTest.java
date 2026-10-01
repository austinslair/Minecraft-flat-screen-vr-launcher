package pojlib.util;

import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.File;
import java.nio.file.*;

/** Patches a real Quest Vivecraft release; CI points VOXYQUEST_VIVECRAFT_TEST_JAR at it. */
public class VivecraftMirrorFixTest {
    @Test public void dropsOnlyTheMirrorOffNoticeOnce() throws Exception {
        String jar = System.getenv("VOXYQUEST_VIVECRAFT_TEST_JAR");
        Assume.assumeTrue(jar != null);
        File game = Files.createTempDirectory("mirror-test").toFile();
        File vivecraft = new File(game, "mods/Vivecraft.jar");
        assertTrue(vivecraft.getParentFile().mkdirs());
        Files.copy(Paths.get(jar), vivecraft.toPath());

        assertTrue(VivecraftMirrorFix.postsOffNotice(ModJarPatcher.readEntry(vivecraft, VivecraftMirrorFix.CLASS)));
        assertTrue(VivecraftMirrorFix.apply(game));
        assertFalse(VivecraftMirrorFix.postsOffNotice(ModJarPatcher.readEntry(vivecraft, VivecraftMirrorFix.CLASS)));
        assertFalse("second launch must not rewrite the jar", VivecraftMirrorFix.apply(game));
    }
}
