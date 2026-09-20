package pojlib.install;

import android.app.Activity;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import pojlib.PojlibRuntime;
import pojlib.util.Constants;
import pojlib.util.GsonUtils;
import pojlib.util.download.DownloadUtils;
import pojlib.util.json.MinecraftInstances;
import pojlib.util.json.ModsJson;
import pojlib.util.json.ProjectInfo;

/**
 * Synchronous worker API for VoxyQuest instance downloads.
 *
 * New installs are only added to the registry after every required stage succeeds.
 * If an already-registered instance is incomplete, downloading the same name/version
 * repairs it in place so worlds and user configuration are preserved.
 */
public final class VoxyQuestInstaller {
    private VoxyQuestInstaller() {}

    public static ModsJson catalog(Activity activity) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(
                activity.getAssets().open("voxyquest/runtime_mods.json"), StandardCharsets.UTF_8)) {
            ModsJson catalog = GsonUtils.GLOBAL_GSON.fromJson(reader, ModsJson.class);
            if (catalog == null || catalog.versions == null) throw new IOException("Runtime catalog unavailable");
            return catalog;
        }
    }

    public static MinecraftInstances readRegistry() throws IOException {
        File registry = new File(Constants.USER_HOME, "instances.json");
        if (!registry.exists()) {
            MinecraftInstances empty = new MinecraftInstances();
            empty.instances = new MinecraftInstances.Instance[0];
            return empty;
        }
        MinecraftInstances result = GsonUtils.jsonFileToObject(registry.getPath(), MinecraftInstances.class);
        if (result == null) throw new IOException("Saved instance registry could not be read");
        return result;
    }

    public static String directoryName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9][A-Za-z0-9 _-]{0,47}")) {
            throw new IllegalArgumentException("Use 1–48 letters, numbers, spaces, underscores or hyphens");
        }
        return name.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    public static synchronized MinecraftInstances.Instance install(Activity activity, String name,
            String version, Consumer<String> progress) throws Exception {
        PojlibRuntime.ensureInitialized(activity);
        String cleanName = name == null ? "" : name.trim();
        String directory = directoryName(cleanName);
        MinecraftInstances registry = readRegistry();

        MinecraftInstances.Instance existing = null;
        for (MinecraftInstances.Instance candidate : registry.toArray()) {
            if (candidate.instanceName == null) continue;
            String existingDirectory = candidate.instanceName.toLowerCase(Locale.ROOT).replace(' ', '_');
            if (existingDirectory.equals(directory)) {
                existing = candidate;
                break;
            }
        }

        ModsJson.Version selected = findCatalogVersion(activity, version);

        if (existing != null) {
            if (isInstalled(existing)) {
                throw new IOException("An instance with this name already exists");
            }
            if (existing.versionName != null && !existing.versionName.isEmpty()
                    && !version.equals(existing.versionName)) {
                throw new IOException("Incomplete instance uses a different Minecraft version");
            }
            progress.accept("Repairing incomplete instance…");
            prepareAndDownload(activity, existing, cleanName, version, directory, selected, progress);
            progress.accept("Saving repaired instance…");
            saveRegistry(registry);
            return existing;
        }

        MinecraftInstances.Instance instance = new MinecraftInstances.Instance();
        prepareAndDownload(activity, instance, cleanName, version, directory, selected, progress);

        progress.accept("Saving installed instance…");
        ArrayList<MinecraftInstances.Instance> all = new ArrayList<>(Arrays.asList(registry.toArray()));
        all.add(instance);
        registry.instances = all.toArray(new MinecraftInstances.Instance[0]);
        saveRegistry(registry);
        return instance;
    }

    private static ModsJson.Version findCatalogVersion(Activity activity, String version) throws IOException {
        for (ModsJson.Version candidate : catalog(activity).versions) {
            if (version.equals(candidate.name)) {
                if (candidate.coreMods == null) throw new IOException("VR runtime catalog is incomplete");
                return candidate;
            }
        }
        throw new IOException("Unsupported Minecraft VR version");
    }

    private static void prepareAndDownload(Activity activity, MinecraftInstances.Instance instance,
            String name, String version, String directory, ModsJson.Version selected,
            Consumer<String> progress) throws Exception {
        progress.accept("Fetching Minecraft and Fabric metadata…");
        VersionInfo minecraft = MinecraftMeta.getVersionInfo(version);
        FabricMeta.FabricVersion stable = null;
        FabricMeta.FabricVersion[] loaders = FabricMeta.getVersions();
        if (loaders != null) {
            for (FabricMeta.FabricVersion loader : loaders) {
                if (loader.stable) {
                    stable = loader;
                    break;
                }
            }
        }
        if (minecraft == null || stable == null) throw new IOException("Minecraft/Fabric metadata unavailable");
        FabricMeta.FabricVersion selectedLoader = stable;
        VersionInfo fabric = FabricMeta.getVersionInfo(selectedLoader, version);
        if (fabric == null || fabric.mainClass == null) throw new IOException("Fabric does not support this version");

        File instancesRoot = new File(Constants.USER_HOME, "instances").getCanonicalFile();
        Files.createDirectories(instancesRoot.toPath());
        File gameDirectory;
        if (instance.gameDir != null && !instance.gameDir.isEmpty()) {
            gameDirectory = new File(instance.gameDir).getCanonicalFile();
        } else {
            gameDirectory = new File(instancesRoot, directory).getCanonicalFile();
        }
        if (!gameDirectory.toPath().startsWith(instancesRoot.toPath())) {
            throw new IOException("Instance directory is outside VoxyQuest storage");
        }
        Files.createDirectories(gameDirectory.toPath());

        instance.instanceName = name;
        instance.versionName = version;
        instance.versionType = minecraft.type;
        instance.mainClass = fabric.mainClass;
        instance.gameDir = gameDirectory.getPath();

        progress.accept("Downloading Minecraft…");
        String client = Installer.installClient(minecraft, Constants.USER_HOME).get();
        if (client == null || !new File(client).isFile()) throw new IOException("Minecraft client download failed");

        progress.accept("Downloading game libraries…");
        String libraries = Installer.installLibraries(minecraft, Constants.USER_HOME).get();
        if (libraries == null) throw new IOException("Minecraft library download failed");

        progress.accept("Downloading Fabric…");
        String loaderLibraries = Installer.installLibraries(fabric, Constants.USER_HOME).get();
        if (loaderLibraries == null) throw new IOException("Fabric library download failed");
        String lwjgl = PojlibRuntime.installLWJGL(activity);
        instance.classpath = client + File.pathSeparator + libraries + File.pathSeparator
                + loaderLibraries + File.pathSeparator + lwjgl;

        progress.accept("Downloading Minecraft assets…");
        instance.assetsDir = Installer.installAssets(minecraft, Constants.USER_HOME).get();
        if (instance.assetsDir == null) throw new IOException("Minecraft asset download failed");
        instance.assetIndex = minecraft.assetIndex.id;
        Installer.moveLocalAssets(activity, instance);

        ArrayList<ProjectInfo> projects = new ArrayList<>();
        projects.addAll(Arrays.asList(selected.coreMods));
        if (selected.defaultMods != null) projects.addAll(Arrays.asList(selected.defaultMods));
        boolean hasVivecraft = false;
        Files.createDirectories(new File(instance.gameDir, "mods").toPath());
        for (ProjectInfo project : projects) {
            if (project.slug == null || !project.slug.matches("[A-Za-z0-9_-]+")) {
                throw new IOException("Invalid runtime mod name");
            }
            if (project.download_link == null || !project.download_link.startsWith("https://")) {
                throw new IOException("Invalid runtime mod download URL");
            }
            progress.accept("Downloading " + project.slug + "…");
            File jar = new File(instance.gameDir, "mods/" + project.slug + ".jar");
            DownloadUtils.downloadFile(project.download_link, jar);
            try (JarFile checked = new JarFile(jar)) {
                if (checked.size() == 0) throw new IOException("Empty mod archive");
            }
            project.type = "mod";
            if (project.slug.equalsIgnoreCase("Vivecraft")) hasVivecraft = true;
        }
        if (!hasVivecraft) throw new IOException("VR runtime catalog has no Vivecraft entry");
        instance.extProjects = projects.toArray(new ProjectInfo[0]);
        instance.defaultMods = true;

        progress.accept("Installing Java runtime…");
        Installer.installJVM(activity);
        File server = new File(activity.getFilesDir(), "runtimes/JRE/lib/server/libjvm.so");
        File clientJvm = new File(activity.getFilesDir(), "runtimes/JRE/lib/client/libjvm.so");
        if (!server.isFile() && !clientJvm.isFile()) throw new IOException("Java runtime installation failed");

        if (!isInstalled(instance)) throw new IOException("Instance verification failed");
    }

    private static void saveRegistry(MinecraftInstances registry) throws IOException {
        File destination = new File(Constants.USER_HOME, "instances.json");
        File parent = destination.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        File temp = File.createTempFile("instances-", ".json", parent);
        try {
            Files.write(temp.toPath(), GsonUtils.GLOBAL_GSON.toJson(registry).getBytes(StandardCharsets.UTF_8));
            MinecraftInstances verified = GsonUtils.jsonFileToObject(temp.getPath(), MinecraftInstances.class);
            if (verified == null) throw new IOException("Could not verify instance registry");
            try {
                Files.move(temp.toPath(), destination.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    public static boolean isInstalled(MinecraftInstances.Instance instance) {
        if (instance == null || instance.classpath == null || instance.mainClass == null || instance.gameDir == null) return false;
        for (String path : instance.classpath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (path.isEmpty() || !new File(path).isFile()) return false;
        }
        return new File(instance.gameDir, "mods/Vivecraft.jar").isFile();
    }
}
