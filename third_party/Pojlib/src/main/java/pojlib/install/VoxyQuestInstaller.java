package pojlib.install;

import android.app.Activity;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
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

/** Synchronous worker API: only commit a registry entry after every stage succeeds. */
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
        String directory = directoryName(name);
        MinecraftInstances registry = readRegistry();
        for (MinecraftInstances.Instance existing : registry.toArray()) {
            if (existing.instanceName != null && existing.instanceName.toLowerCase(Locale.ROOT).replace(' ', '_').equals(directory)) throw new IOException("An instance with this name already exists");
        }
        ModsJson.Version selected = null;
        for (ModsJson.Version candidate : catalog(activity).versions) {
            if (version.equals(candidate.name)) selected = candidate;
        }
        if (selected == null || selected.coreMods == null) throw new IOException("Unsupported Minecraft VR version");
        progress.accept("Fetching Minecraft and Fabric metadata…");
        VersionInfo minecraft = MinecraftMeta.getVersionInfo(version);
        FabricMeta.FabricVersion stable = null;
        FabricMeta.FabricVersion[] loaders = FabricMeta.getVersions();
        if (loaders != null) {
            for (FabricMeta.FabricVersion loader : loaders) {
                if (loader.stable) { stable = loader; break; }
            }
        }
        if (minecraft == null || stable == null) throw new IOException("Minecraft/Fabric metadata unavailable");
        VersionInfo fabric = FabricMeta.getVersionInfo(stable, version);
        if (fabric == null || fabric.mainClass == null) throw new IOException("Fabric does not support this version");
        MinecraftInstances.Instance instance = new MinecraftInstances.Instance();
        instance.instanceName = name;
        instance.versionName = version;
        instance.versionType = minecraft.type;
        instance.mainClass = fabric.mainClass;
        instance.gameDir = new File(Constants.USER_HOME, "instances/" + directory).getPath();
        Files.createDirectories(new File(instance.gameDir).toPath());
        progress.accept("Downloading Minecraft…");
        String client = Installer.installClient(minecraft, Constants.USER_HOME).get();
        if (client == null) throw new IOException("Minecraft client download failed");
        progress.accept("Downloading game libraries…");
        String libraries = Installer.installLibraries(minecraft, Constants.USER_HOME).get();
        progress.accept("Downloading Fabric…");
        String loaderLibraries = Installer.installLibraries(fabric, Constants.USER_HOME).get();
        String lwjgl = PojlibRuntime.installLWJGL(activity);
        instance.classpath = client + File.pathSeparator + libraries + File.pathSeparator + loaderLibraries + File.pathSeparator + lwjgl;
        progress.accept("Downloading Minecraft assets…");
        instance.assetsDir = Installer.installAssets(minecraft, Constants.USER_HOME).get();
        instance.assetIndex = minecraft.assetIndex.id;
        Installer.moveLocalAssets(activity, instance);
        ArrayList<ProjectInfo> projects = new ArrayList<>();
        projects.addAll(Arrays.asList(selected.coreMods));
        if (selected.defaultMods != null) projects.addAll(Arrays.asList(selected.defaultMods));
        boolean hasVivecraft = false;
        for (ProjectInfo project : projects) {
            if (project.slug == null || !project.slug.matches("[A-Za-z0-9_-]+")) throw new IOException("Invalid runtime mod name");
            progress.accept("Installing " + project.slug + "…");
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
        progress.accept("Saving installed instance…");
        ArrayList<MinecraftInstances.Instance> all = new ArrayList<>(Arrays.asList(registry.toArray()));
        all.add(instance);
        registry.instances = all.toArray(new MinecraftInstances.Instance[0]);
        File destination = new File(Constants.USER_HOME, "instances.json");
        File temp = File.createTempFile("instances-", ".json", destination.getParentFile());
        try {
            Files.write(temp.toPath(), GsonUtils.GLOBAL_GSON.toJson(registry).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
        return instance;
    }

    public static boolean isInstalled(MinecraftInstances.Instance instance) {
        if (instance == null || instance.classpath == null || instance.mainClass == null || instance.gameDir == null) return false;
        for (String path : instance.classpath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (path.isEmpty() || !new File(path).isFile()) return false;
        }
        return new File(instance.gameDir, "mods/Vivecraft.jar").isFile();
    }
}
