package pojlib.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import org.junit.Test;

public class ClasspathUtilsTest {
    @Test
    public void neoForgeAndVanillaShareGsonWithoutDuplicatingIt() {
        String separator = File.pathSeparator;
        String patched = "/libraries/neoforge-client.jar";
        String gson = "/libraries/gson-2.11.0.jar";
        String asm = "/libraries/asm-9.7.jar";
        String minecraft = "/versions/minecraft-client.jar";
        String neoLibraries = gson + separator + asm + separator + gson;
        String vanillaLibraries = gson + separator + asm;
        String expected = patched + separator + gson + separator + asm + separator + minecraft;

        assertEquals(expected, ClasspathUtils.unique(patched, neoLibraries, minecraft, vanillaLibraries));
        // Older installed profiles store the duplicate paths in one classpath string.
        assertEquals(expected, ClasspathUtils.unique(patched + separator + neoLibraries + separator
                + minecraft + separator + vanillaLibraries));
    }
}
