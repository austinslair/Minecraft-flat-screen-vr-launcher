package pojlib.install;

import android.app.Activity;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import pojlib.PojlibRuntime;
import pojlib.util.ClasspathUtils;
import pojlib.util.Constants;
import pojlib.util.GsonUtils;
import pojlib.util.download.DownloadUtils;
import pojlib.util.json.MinecraftInstances;
import pojlib.util.json.ProjectInfo;

/** Installs the pinned Quest OpenXR build and the official patched NeoForge client. */
final class NeoForgeInstaller {
    static final String VERSION = "1.21.5";
    private static final String LOADER_VERSION = "21.5.2-beta";
    private static final String NEOFORM_VERSION = "20250325.162830";
    private static final String ASSET_ROOT = "voxyquest/neoforge/";

    private NeoForgeInstaller() {}

    static void prepareAndDownload(Activity activity, MinecraftInstances.Instance instance,
            String name, String directory, Consumer<String> progress) throws Exception {
        progress.accept("Checking NeoForge and Minecraft metadata…");
        VersionInfo minecraft = MinecraftMeta.getVersionInfo(VERSION);
        if (minecraft == null || minecraft.assetIndex == null)
            throw new IOException("Minecraft " + VERSION + " metadata unavailable");
        VersionInfo neoforge;
        try (InputStreamReader reader = new InputStreamReader(
                activity.getAssets().open(ASSET_ROOT + "version.json"), StandardCharsets.UTF_8)) {
            neoforge = GsonUtils.GLOBAL_GSON.fromJson(reader, VersionInfo.class);
        }
        if (neoforge == null || neoforge.libraries == null || neoforge.arguments == null ||
                !neoforge.id.equals("neoforge-" + LOADER_VERSION) ||
                !"cpw.mods.bootstraplauncher.BootstrapLauncher".equals(neoforge.mainClass))
            throw new IOException("Bundled NeoForge client profile is incomplete");

        File root = new File(Constants.USER_HOME, "instances").getCanonicalFile();
        Files.createDirectories(root.toPath());
        File game = instance.gameDir == null || instance.gameDir.isEmpty()
                ? new File(root, directory).getCanonicalFile() : new File(instance.gameDir).getCanonicalFile();
        if (game.equals(root) || !game.toPath().startsWith(root.toPath()))
            throw new IOException("Instance directory is outside VoxyQuest storage");
        Files.createDirectories(game.toPath());

        instance.instanceName = name;
        instance.versionName = VERSION;
        instance.modLoader = "neoforge";
        instance.versionType = minecraft.type;
        instance.mainClass = neoforge.mainClass;
        instance.gameDir = game.getPath();

        progress.accept("Downloading Minecraft and NeoForge libraries…");
        String client = Installer.installClient(minecraft, Constants.USER_HOME).get();
        String libraries = Installer.installLibraries(minecraft, Constants.USER_HOME).get();
        String neoLibraries = Installer.installLibraries(neoforge, Constants.USER_HOME).get();
        if (client == null || libraries == null || neoLibraries == null)
            throw new IOException("NeoForge library download failed");
        for (VersionInfo.Library library : neoforge.libraries) {
            if (!library.allowedOnAndroid() || library.name.contains("lwjgl")) continue;
            VersionInfo.Library.Artifact artifact = library.downloads == null ? null : library.downloads.artifact;
            if (artifact == null || artifact.path == null || artifact.sha1 == null ||
                    !DownloadUtils.compareSHA1(new File(Constants.USER_HOME, "libraries/" + artifact.path), artifact.sha1))
                throw new IOException("NeoForge library missing or corrupt: " + library.name);
        }
        String lwjgl = PojlibRuntime.installLWJGL(activity);
        File neoRoot = new File(Constants.USER_HOME,
                "libraries/net/neoforged/neoforge/" + LOADER_VERSION);
        File universal = copyJar(activity, "universal.jar", new File(neoRoot,
                "neoforge-" + LOADER_VERSION + "-universal.jar"));
        File patched = copyJar(activity, "client.jar", new File(neoRoot,
                "neoforge-" + LOADER_VERSION + "-client.jar"));
        ensureSystemJars(activity);
        // Both artifacts are produced by NeoForge's client installer processors.
        // The patched NeoForge client contains Minecraft classes also present in the
        // vanilla client. Put it first so the vanilla jar cannot shadow those patches.
        instance.classpath = ClasspathUtils.unique(patched.toString(), universal.toString(),
                neoLibraries, client, libraries, lwjgl);
        instance.jvmLaunchArgs = expandArguments(neoforge.arguments.jvm, neoforge.id);
        instance.gameLaunchArgs = expandArguments(neoforge.arguments.game, neoforge.id);

        progress.accept("Downloading Minecraft assets…");
        instance.assetsDir = Installer.installAssets(minecraft, Constants.USER_HOME).get();
        if (instance.assetsDir == null) throw new IOException("Minecraft asset download failed");
        instance.assetIndex = minecraft.assetIndex.id;
        Installer.moveLocalAssets(activity, instance);

        progress.accept("Installing Vivecraft OpenXR for NeoForge…");
        File mods = new File(game, "mods");
        Files.createDirectories(mods.toPath());
        File vivecraft = copyJar(activity, "vivecraft.jar", new File(mods, "Vivecraft.jar"));
        try (JarFile jar = new JarFile(vivecraft)) {
            if (jar.getJarEntry("META-INF/neoforge.mods.toml") == null)
                throw new IOException("Bundled Vivecraft is not a NeoForge build");
        }
        ProjectInfo project = new ProjectInfo();
        project.slug = "Vivecraft";
        project.type = "mod";
        project.version = VERSION + "-1.3.4-neoforge";
        instance.extProjects = new ProjectInfo[] {project};
        instance.defaultMods = false;

        VoxyQuestJavaRuntime.install(activity, progress);
        File server = new File(activity.getFilesDir(), "runtimes/JRE/lib/server/libjvm.so");
        File clientJvm = new File(activity.getFilesDir(), "runtimes/JRE/lib/client/libjvm.so");
        if ((!server.isFile() && !clientJvm.isFile()) || !VoxyQuestInstaller.isInstalled(instance))
            throw new IOException("NeoForge runtime installation is incomplete");
    }

    /** The production NeoForge locator loads these by Maven path, not from -cp. */
    static void ensureSystemJars(Activity activity) throws IOException {
        String version = VERSION + "-" + NEOFORM_VERSION;
        File root = new File(Constants.USER_HOME, "libraries/net/minecraft/client/" + version);
        ensureJar(activity, "minecraft-srg.jar", new File(root, "client-" + version + "-srg.jar"),
                "net/minecraft/client/Minecraft.class");
        ensureJar(activity, "minecraft-extra.jar", new File(root, "client-" + version + "-extra.jar"),
                "assets/.mcassetsroot");
    }

    private static void ensureJar(Activity activity, String assetName, File target, String marker) throws IOException {
        if (target.isFile()) {
            try (JarFile jar = new JarFile(target)) {
                if (jar.getJarEntry(marker) != null) return;
            } catch (IOException ignored) {
                // Replace an interrupted or corrupt install from the APK.
            }
        }
        copyJar(activity, assetName, target);
        try (JarFile jar = new JarFile(target)) {
            if (jar.getJarEntry(marker) == null) throw new IOException("Bundled NeoForge game JAR is incomplete: " + assetName);
        }
    }

    private static File copyJar(Activity activity, String assetName, File destination) throws IOException {
        Files.createDirectories(destination.getParentFile().toPath());
        File temp = File.createTempFile("neoforge-", ".jar", destination.getParentFile());
        try {
            try (InputStream stream = activity.getAssets().open(ASSET_ROOT + assetName)) {
                Files.copy(stream, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            try (JarFile checked = new JarFile(temp)) {
                if (checked.size() == 0) throw new IOException("Empty NeoForge archive: " + assetName);
            }
            Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp.toPath()); }
        return destination;
    }

    private static String[] expandArguments(Object[] raw, String versionId) throws IOException {
        if (raw == null) throw new IOException("NeoForge launch arguments are missing");
        ArrayList<String> values = new ArrayList<>();
        for (Object entry : raw) {
            if (!(entry instanceof String)) throw new IOException("Unsupported NeoForge launch argument");
            String value = ((String) entry)
                    .replace("${library_directory}", new File(Constants.USER_HOME, "libraries").getPath())
                    .replace("${classpath_separator}", File.pathSeparator)
                    .replace("${version_name}", versionId);
            if (value.contains("${")) throw new IOException("Unknown NeoForge launch placeholder");
            values.add(value);
        }
        return values.toArray(new String[0]);
    }
}
