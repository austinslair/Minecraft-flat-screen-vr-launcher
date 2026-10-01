package pojlib.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/** OpenGL-on-GLES translation layers bundled with VoxyQuest. */
public enum Renderer {
    /** LightThinWrapper: QuestCraft's original renderer, kept as a fallback. */
    LTW("ltw", "LightThinWrapper", "libltw.so", "2"),
    /**
     * Default for VR and Flatscreen. MobileGlues with the TGS Quest upload patches: desktop
     * GLSL is translated and cached, and texture/chunk uploads avoid the stalls that heavy
     * mod packs trigger. OpenXR swapchain images reach it as foreign texture names, which it
     * passes through to the driver and tracks on first use.
     */
    MOBILEGLUES("mobileglues", "MobileGlues", "libmobileglues.so", "3");

    /** Shader translations kept between launches; MobileGlues disables its cache without one. */
    static final int SHADER_CACHE_MB = 64;

    public final String id;
    public final String displayName;
    public final String library;
    /** LIBGL_ES, applied after custom_env.txt as before. */
    public final String glesVersion;

    Renderer(String id, String displayName, String library, String glesVersion) {
        this.id = id;
        this.displayName = displayName;
        this.library = library;
        this.glesVersion = glesVersion;
    }

    public static final Renderer DEFAULT = MOBILEGLUES;

    public static Renderer fromId(String id) {
        for (Renderer renderer : values()) {
            if (renderer.id.equals(id)) return renderer;
        }
        return DEFAULT;
    }

    /**
     * Environment for this renderer. Entries in custom_env.txt are applied afterwards,
     * so players can still override any of them.
     *
     * @param dataDir app-private directory for renderer state such as the shader cache
     */
    public Map<String, String> environment(File dataDir) throws IOException {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("POJLIB_RENDERER", displayName);
        // Read by the EGL bridge, which creates the context through the renderer's EGL.
        env.put("POJAVEXEC_EGL", library);
        if (this == MOBILEGLUES) {
            File directory = new File(dataDir, "mobileglues");
            Files.createDirectories(directory.toPath());
            File config = new File(directory, "config.json");
            // Absent keys fall back to MobileGlues' defaults; players may edit this file.
            if (!config.exists()) {
                Files.write(config.toPath(), ("{\n  \"maxGlslCacheSize\": " + SHADER_CACHE_MB + "\n}\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
            env.put("MG_DIR_PATH", directory.getAbsolutePath());
            env.put("LIBGL_EGL", library);
            // TGS Quest patches: opaque presentation (no compositor blending of the game
            // window) and stall-free buffer/texture uploads.
            env.put("TGS_OPAQUE_WINDOW", "1");
            env.put("TGS_FAST_UPLOADS", "1");
            env.put("TGS_STREAM_UPLOADS", "1");
            env.put("TGS_CHUNK_STAGING", "1");
        }
        return env;
    }
}
