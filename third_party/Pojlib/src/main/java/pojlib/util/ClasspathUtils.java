package pojlib.util;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;

/** Keep the first copy of each JAR so NeoForge's securejarhandler sees unique paths. */
public final class ClasspathUtils {
    private ClasspathUtils() {}

    public static String unique(String... segments) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        for (String segment : segments) {
            if (segment == null) continue;
            for (String path : segment.split(Pattern.quote(File.pathSeparator))) {
                if (!path.isEmpty()) paths.add(path);
            }
        }
        return String.join(File.pathSeparator, paths);
    }
}
