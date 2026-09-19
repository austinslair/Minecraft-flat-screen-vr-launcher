package pojlib.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GsonUtils {
    public static final Gson GLOBAL_GSON = new GsonBuilder().setPrettyPrinting().create();

    private GsonUtils() {}

    public static <T> T jsonFileToObject(String path, Class<T> tClass) {
        Path file = Paths.get(path);
        if (!Files.isRegularFile(file)) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return GLOBAL_GSON.fromJson(reader, tClass);
        } catch (IOException | RuntimeException e) {
            Logger.getInstance().appendToLog("WARN! Unable to read JSON cache " + path + ": " + e.getMessage());
            return null;
        }
    }

    public static void objectToJsonFile(String path, Object object) {
        File dir = new File(path).getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            Logger.getInstance().appendToLog("WARN! Unable to create JSON cache directory: " + dir);
            return;
        }

        try (Writer writer = Files.newBufferedWriter(Paths.get(path), StandardCharsets.UTF_8)) {
            GLOBAL_GSON.toJson(object, writer);
        } catch (IOException e) {
            Logger.getInstance().appendToLog("WARN! Unable to write JSON cache " + path + ": " + e.getMessage());
        }
    }
}
