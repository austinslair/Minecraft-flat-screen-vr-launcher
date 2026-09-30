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

/** Installs packaged NeoForge clients and their matching Quest OpenXR builds. */
final class NeoForgeInstaller {
    private static final String ASSET_ROOT = "voxyquest/neoforge/";
    private static final Bundle[] BUNDLES = {
            // CI installs the newest 21.8.x NeoForge; the bundled profile names the exact build.
            new Bundle("1.21.8", "21.8.*", ASSET_ROOT + "1.21.8/", "1.3.4"),
            new Bundle("1.21.5", "21.5.2-beta", ASSET_ROOT, "1.3.4"),
            new Bundle("1.21.4", "21.4.150", ASSET_ROOT + "1.21.4/", "1.2.5"),
            new Bundle("1.21.1", "21.1.228", ASSET_ROOT + "1.21.1/", "1.2.5")
    };

    private static final class Bundle {
        final String version, assets;
        final boolean vr;
        final String vivecraftVersion;
        /** An exact NeoForge version, or a {@code major.minor.*} pattern read from the profile. */
        private final String loaderPattern;
        private volatile String loader;
        Bundle(String version, String loader, String assets, String vivecraftVersion) {
            this.version = version;
            this.loaderPattern = loader;
            this.loader = loader.endsWith("*") ? null : loader;
            this.assets = assets;
            this.vr = vivecraftVersion != null;
            this.vivecraftVersion = vivecraftVersion;
        }

        boolean accepts(String candidate) {
            return loaderPattern.endsWith("*")
                    ? candidate.startsWith(loaderPattern.substring(0, loaderPattern.length() - 1))
                            && candidate.length() > loaderPattern.length() - 1
                    : candidate.equals(loaderPattern);
        }

        String loader(Activity activity) throws IOException {
            if (loader == null) loader = profile(activity, this).id.substring("neoforge-".length());
            return loader;
        }
    }

    static String[] versions() {
        String[] result = new String[BUNDLES.length];
        for (int i = 0; i < result.length; i++) result[i] = BUNDLES[i].version;
        return result;
    }

    static String[] vrVersions() {
        ArrayList<String> versions = new ArrayList<>();
        for (Bundle bundle : BUNDLES) if (bundle.vr) versions.add(bundle.version);
        return versions.toArray(new String[0]);
    }

    static boolean supportsVr(String version) {
        return bundle(version).vr;
    }

    static String vivecraftAsset(String version) {
        Bundle selected = bundle(version);
        if (!selected.vr) throw new IllegalArgumentException("No NeoForge VR build for " + version);
        return selected.assets + "vivecraft.jar";
    }

    private static Bundle bundle(String version) {
        for (Bundle candidate : BUNDLES) if (candidate.version.equals(version)) return candidate;
        throw new IllegalArgumentException("Unsupported NeoForge Minecraft version: " + version);
    }

    private static VersionInfo profile(Activity activity, Bundle bundle) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(
                activity.getAssets().open(bundle.assets + "version.json"), StandardCharsets.UTF_8)) {
            VersionInfo info = GsonUtils.GLOBAL_GSON.fromJson(reader, VersionInfo.class);
            if (info == null || info.id == null || info.libraries == null || info.arguments == null ||
                    !info.id.startsWith("neoforge-") || !bundle.accepts(info.id.substring("neoforge-".length())) ||
                    !"cpw.mods.bootstraplauncher.BootstrapLauncher".equals(info.mainClass))
                throw new IOException("Packaged NeoForge " + bundle.version + " profile is incomplete");
            return info;
        }
    }

    private static String neoFormVersion(VersionInfo info) throws IOException {
        Object[] arguments = info.arguments.game;
        if (arguments != null) for (int i = 0; i + 1 < arguments.length; i++) {
            if ("--fml.neoFormVersion".equals(arguments[i]) && arguments[i + 1] instanceof String)
                return (String) arguments[i + 1];
        }
        throw new IOException("NeoForge profile has no NeoForm version");
    }

    private NeoForgeInstaller() {}

    static void prepareAndDownload(Activity activity, MinecraftInstances.Instance instance,
            String name, String version, String directory, Consumer<String> progress) throws Exception {
        Bundle bundle = bundle(version);
        progress.accept("Checking NeoForge and Minecraft metadata…");
        VersionInfo minecraft = MinecraftMeta.getVersionInfo(version);
        if (minecraft == null || minecraft.assetIndex == null)
            throw new IOException("Minecraft " + version + " metadata unavailable");
        VersionInfo neoforge = profile(activity, bundle);
        String neoForm = neoFormVersion(neoforge);

        File root = new File(Constants.USER_HOME, "instances").getCanonicalFile();
        Files.createDirectories(root.toPath());
        File game = instance.gameDir == null || instance.gameDir.isEmpty()
                ? new File(root, directory).getCanonicalFile() : new File(instance.gameDir).getCanonicalFile();
        if (game.equals(root) || !game.toPath().startsWith(root.toPath()))
            throw new IOException("Instance directory is outside VoxyQuest storage");
        Files.createDirectories(game.toPath());

        instance.instanceName = name;
        instance.versionName = version;
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
        String neoLwjgl = PojlibRuntime.installNeoForgeLWJGL(activity);
        String loader = bundle.loader(activity);
        File neoRoot = new File(Constants.USER_HOME,
                "libraries/net/neoforged/neoforge/" + loader);
        File universal = copyJar(activity, bundle, "universal.jar", new File(neoRoot,
                "neoforge-" + loader + "-universal.jar"));
        File patched = copyJar(activity, bundle, "client.jar", new File(neoRoot,
                "neoforge-" + loader + "-client.jar"));
        ensureSystemJars(activity, bundle, neoForm);
        // NeoForge's production locators discover both the processed Minecraft
        // client and NeoForge universal by Maven path. Boot classpath entries for
        // any of these game modules either duplicate packages or hide the mod.
        instance.classpath = ClasspathUtils.excluding(ClasspathUtils.unique(
                neoLibraries, libraries, lwjgl),
                patched.toString(), client, universal.toString()).replace(lwjgl, neoLwjgl);
        instance.jvmLaunchArgs = expandArguments(neoforge.arguments.jvm, neoforge.id);
        instance.gameLaunchArgs = expandArguments(neoforge.arguments.game, neoforge.id);

        progress.accept("Downloading Minecraft assets…");
        instance.assetsDir = Installer.installAssets(minecraft, Constants.USER_HOME).get();
        if (instance.assetsDir == null) throw new IOException("Minecraft asset download failed");
        instance.assetIndex = minecraft.assetIndex.id;
        Installer.moveLocalAssets(activity, instance);

        File mods = new File(game, "mods");
        Files.createDirectories(mods.toPath());
        if (bundle.vr) {
            progress.accept("Installing Vivecraft OpenXR for NeoForge…");
            File vivecraft = copyJar(activity, bundle, "vivecraft.jar", new File(mods, "Vivecraft.jar"));
            try (JarFile jar = new JarFile(vivecraft)) {
                if (jar.getJarEntry("META-INF/neoforge.mods.toml") == null)
                    throw new IOException("Packaged Vivecraft is not a NeoForge build");
            }
            ProjectInfo project = new ProjectInfo();
            project.slug = "Vivecraft";
            project.type = "mod";
            project.version = version + "-" + bundle.vivecraftVersion + "-neoforge";
            instance.extProjects = new ProjectInfo[] {project};
        } else {
            instance.extProjects = new ProjectInfo[0];
        }
        instance.defaultMods = false;

        VoxyQuestJavaRuntime.install(activity, progress);
        File server = new File(activity.getFilesDir(), "runtimes/JRE/lib/server/libjvm.so");
        File clientJvm = new File(activity.getFilesDir(), "runtimes/JRE/lib/client/libjvm.so");
        if ((!server.isFile() && !clientJvm.isFile()) || !VoxyQuestInstaller.isInstalled(instance))
            throw new IOException("NeoForge runtime installation is incomplete");
    }

    /**
     * True when the instance was installed with an older bundled NeoForge build.
     * Its saved classpath and launch arguments still name that build, so mods that
     * require the bundled version (Create's Sable needs 21.1.228) would refuse to load.
     */
    static boolean needsLoaderUpgrade(Activity activity, MinecraftInstances.Instance instance) throws IOException {
        if (instance.gameLaunchArgs == null) return false;
        return !java.util.Arrays.asList(instance.gameLaunchArgs).contains(bundle(instance.versionName).loader(activity));
    }

    /** The production NeoForge locator loads these by Maven path, not from -cp. */
    static void ensureSystemJars(Activity activity, String minecraftVersion) throws IOException {
        Bundle bundle = bundle(minecraftVersion);
        ensureSystemJars(activity, bundle, neoFormVersion(profile(activity, bundle)));
    }

    private static void ensureSystemJars(Activity activity, Bundle bundle, String neoForm) throws IOException {
        String version = bundle.version + "-" + neoForm;
        File root = new File(Constants.USER_HOME, "libraries/net/minecraft/client/" + version);
        ensureJar(activity, bundle, "minecraft-srg.jar", new File(root, "client-" + version + "-srg.jar"),
                "net/minecraft/client/Minecraft.class");
        ensureJar(activity, bundle, "minecraft-extra.jar", new File(root, "client-" + version + "-extra.jar"),
                "assets/.mcassetsroot");
        String loader = bundle.loader(activity);
        File neoRoot = new File(Constants.USER_HOME,
                "libraries/net/neoforged/neoforge/" + loader);
        ensureJar(activity, bundle, "client.jar", new File(neoRoot,
                "neoforge-" + loader + "-client.jar"), "net/minecraft/client/Minecraft.class");
        ensureJar(activity, bundle, "universal.jar", new File(neoRoot,
                "neoforge-" + loader + "-universal.jar"), "META-INF/neoforge.mods.toml");
    }

    static void useNeoForgeGlfw(Activity activity, MinecraftInstances.Instance instance) throws IOException {
        Bundle bundle = bundle(instance.versionName);
        String loader = bundle.loader(activity);
        String original = Constants.USER_HOME + "/lwjgl3/lwjgl-glfw-classes.jar";
        String replacement = PojlibRuntime.installNeoForgeLWJGL(activity);
        instance.classpath = ClasspathUtils.excluding(instance.classpath.replace(original, replacement),
                new File(Constants.USER_HOME, "libraries/net/neoforged/neoforge/" + loader
                        + "/neoforge-" + loader + "-client.jar").getPath(),
                new File(Constants.USER_HOME, "libraries/net/neoforged/neoforge/" + loader
                        + "/neoforge-" + loader + "-universal.jar").getPath(),
                new File(Constants.USER_HOME, "versions/" + bundle.version + "/client.jar").getPath());
    }

    private static void ensureJar(Activity activity, Bundle bundle, String assetName, File target, String marker) throws IOException {
        if (target.isFile()) {
            try (JarFile jar = new JarFile(target)) {
                if (jar.getJarEntry(marker) != null) return;
            } catch (IOException ignored) {
                // Replace an interrupted or corrupt install from the APK.
            }
        }
        copyJar(activity, bundle, assetName, target);
        try (JarFile jar = new JarFile(target)) {
            if (jar.getJarEntry(marker) == null) throw new IOException("Bundled NeoForge game JAR is incomplete: " + assetName);
        }
    }

    private static File copyJar(Activity activity, Bundle bundle, String assetName, File destination) throws IOException {
        Files.createDirectories(destination.getParentFile().toPath());
        File temp = File.createTempFile("neoforge-", ".jar", destination.getParentFile());
        try {
            try (InputStream stream = activity.getAssets().open(bundle.assets + assetName)) {
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
