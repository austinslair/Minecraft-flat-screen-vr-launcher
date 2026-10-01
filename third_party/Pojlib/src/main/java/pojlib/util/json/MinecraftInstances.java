package pojlib.util.json;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pojlib.account.MinecraftAccount;
import pojlib.API;
import pojlib.InstanceHandler;
import pojlib.util.Constants;
import pojlib.util.ClasspathUtils;
import pojlib.util.download.DownloadManager;
import pojlib.util.download.DownloadUtils;
import pojlib.util.GsonUtils;
import pojlib.util.Logger;

public class MinecraftInstances {
    public Instance[] instances;

    public Instance load(String name) {
        for(Instance instance : instances) {
            if(instance.instanceName.equals(name)) {
                return instance;
            }
        }

        return null;
    }

    public Instance[] toArray() {
        if(instances == null) {
            return new Instance[0];
        }
        return instances;
    }

    public static void CheckVivecraftConfig(MinecraftInstances.Instance instance) {
        try { configurePlayMode(instance, true); }
        catch (IOException e) { throw new IllegalStateException("Could not configure VR", e); }
    }

    public static void configurePlayMode(Instance instance, boolean vr) throws IOException {
        File config = new File(instance.gameDir, "config/vivecraft-client-config.json");
        Files.createDirectories(config.getParentFile().toPath());
        JsonObject obj = config.isFile()
                ? GsonUtils.jsonFileToObject(config.getPath(), JsonObject.class) : new JsonObject();
        if (obj == null) throw new IOException("Invalid Vivecraft config");
        obj.addProperty("stereoProviderPluginID", "OPENXR");
        obj.addProperty("vrEnabled", Boolean.toString(vr));
        obj.addProperty("vrToggleButtonEnabled", "false");
        obj.addProperty("vrHotswitchingEnabled", "false");
        obj.addProperty("seated", "false");
        File temp = File.createTempFile("vivecraft-", ".json", config.getParentFile());
        try {
            Files.write(temp.toPath(), GsonUtils.GLOBAL_GSON.toJson(obj)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Files.move(temp.toPath(), config.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp.toPath()); }
    }

    public static class Instance {
        public String instanceName;
        public String instanceImageURL;
        public String versionName;
        // Missing in existing saved profiles; those profiles were all installed with Fabric.
        public String modLoader;
        public String versionType;
        public String classpath;
        public String gameDir;
        public String assetIndex;
        public String assetsDir;
        public String mainClass;
        public String[] jvmLaunchArgs;
        public String[] gameLaunchArgs;
        public ProjectInfo[] extProjects;
        public boolean defaultMods;

        public String loaderId() {
            return "neoforge".equalsIgnoreCase(modLoader) ? "neoforge" : "fabric";
        }

        public List<String> generateLaunchArgs(MinecraftAccount account) {
            String[] mcArgs = {"--username", account.username, "--version", versionName, "--gameDir", gameDir,
                    "--assetsDir", assetsDir, "--assetIndex", assetIndex, "--uuid", account.uuid.replace("-", ""),
                    "--accessToken", account.accessToken, "--userType", account.userType, "--versionType", "release"};

            List<String> allArgs = new ArrayList<>();
            if (jvmLaunchArgs != null) allArgs.addAll(Arrays.asList(jvmLaunchArgs));
            allArgs.add("-cp");
            // Saved NeoForge instances may contain duplicate vanilla/loader JARs.
            allArgs.add("neoforge".equals(loaderId()) ? ClasspathUtils.unique(classpath) : classpath);
            allArgs.add(mainClass);
            if (gameLaunchArgs != null) allArgs.addAll(Arrays.asList(gameLaunchArgs));
            allArgs.addAll(Arrays.asList(mcArgs));
            if (account.isDemoMode) {
                allArgs.add("--demo");
            }

            return allArgs;
        }

        public ProjectInfo[] toArray() {
            if(extProjects == null) {
                return new ProjectInfo[0];
            }
            return extProjects;
        }

        public ModsJson parseModsJson(String jsonPath) {
            return GsonUtils.jsonFileToObject(jsonPath, ModsJson.class);
        }

        private ModsJson downloadCurrentModsJson(String userHome) throws Exception {
            File mods = new File(userHome + "/new_mods.json");
            if(API.developerMods) {
                DownloadUtils.downloadFile(InstanceHandler.DEV_MODS, mods);
            } else {
                DownloadUtils.downloadFile(InstanceHandler.MODS, mods);
            }

            return parseModsJson(mods.getAbsolutePath());
        }

        private void removeModByType(List<ProjectInfo> oldMods, List<ProjectInfo> newMods) {
            ArrayList<ProjectInfo> removedMods = new ArrayList<>();

            for(ProjectInfo oldMod : oldMods) {
                boolean exists = false;
                for(ProjectInfo newMod : newMods) {
                    if(!oldMod.slug.equals(newMod.slug)) {
                        continue;
                    }
                    exists = true;
                    break;
                }

                if(!exists) {
                    removedMods.add(oldMod);
                }
            }

            ArrayList<ProjectInfo> newExtProjects = new ArrayList<>();
            for(ProjectInfo extProject : extProjects) {
                boolean remove = false;
                for(ProjectInfo removedMod : removedMods) {
                    if (extProject.slug.equals(removedMod.slug)) {
                        remove = true;
                        break;
                    }
                }
                if(!remove) {
                    newExtProjects.add(extProject);
                } else {
                    boolean legacyMod = extProject.fileName == null;
                    File mod = new File(
                            gameDir + (extProject.type.equals("mod") ? "/mods" : "/resourcepacks"),
                            (legacyMod ? extProject.slug : extProject.fileName) + (extProject.type.equals("resourcepack") ? ".zip" : ".jar")
                    );
                    if(mod.exists()) {
                        mod.delete();
                    }
                }
            }
            extProjects = newExtProjects.toArray(new ProjectInfo[0]);
        }

        private void removeOldMods(ModsJson oldMods, ModsJson newMods) {
            for(ModsJson.Version oldVersion : oldMods.versions) {
                if(!versionName.equals(oldVersion.name)) {
                    continue;
                }

                for(ModsJson.Version newVersion : newMods.versions) {
                    if(!versionName.equals(newVersion.name)) {
                        continue;
                    }

                    ArrayList<ProjectInfo> mergedOldMods = new ArrayList<>();
                    mergedOldMods.addAll(Arrays.asList(oldVersion.coreMods));
                    mergedOldMods.addAll(Arrays.asList(oldVersion.defaultMods));

                    ArrayList<ProjectInfo> mergedNewMods = new ArrayList<>();
                    mergedNewMods.addAll(Arrays.asList(newVersion.coreMods));
                    mergedNewMods.addAll(Arrays.asList(newVersion.defaultMods));

                    if(defaultMods) {
                        removeModByType(mergedOldMods, mergedNewMods);
                    } else {
                        removeModByType(Arrays.asList(oldVersion.coreMods), Arrays.asList(newVersion.coreMods));
                    }
                    break;
                }
            }
        }

        private void updateModByType(List<ProjectInfo> newMods) throws IOException {
            ArrayList<ProjectInfo> newExtMods = new ArrayList<>();
            for(ProjectInfo extMod : extProjects) {
                boolean manual = true;
                for(ProjectInfo newMod : newMods) {
                    if(!extMod.slug.equals(newMod.slug)) {
                        continue;
                    }
                    manual = false;
                    boolean legacyMod = newMod.fileName == null;
                    File mod = new File(
                            gameDir + (newMod.type.equals("mod") ? "/mods" : "/resourcepacks"),
                            (legacyMod ? newMod.slug : newMod.fileName) + (newMod.type.equals("resourcepack") ? ".zip" : ".jar")
                    );
                    if(!mod.exists() || !extMod.version.equals(newMod.version)) {
                        DownloadUtils.downloadFile(newMod.download_link, mod);
                        extMod = newMod;
                        break;
                    }
                }
                if(manual) {
                    boolean legacyMod = extMod.fileName == null;
                    File mod = new File(
                            gameDir + (extMod.type.equals("mod") ? "/mods" : "/resourcepacks"),
                            (legacyMod ? extMod.slug : extMod.fileName) + (extMod.type.equals("resourcepack") ? ".zip" : ".jar")
                    );
                    if(!mod.exists()) {
                        DownloadUtils.downloadFile(extMod.download_link, mod, 0);
                    }
                }
                newExtMods.add(extMod);
            }

            extProjects = newExtMods.toArray(new ProjectInfo[0]);
        }

        private void downloadAllMods(List<ProjectInfo> newMods) throws IOException {
            for(ProjectInfo newMod : newMods) {
                boolean legacyMod = newMod.fileName == null;
                File mod = new File(
                        gameDir + (newMod.type.equals("mod") ? "/mods" : "/resourcepacks"),
                        (legacyMod ? newMod.slug : newMod.fileName) + (newMod.type.equals("resourcepack") ? ".zip" : ".jar")
                );
                DownloadUtils.downloadFile(newMod.download_link, mod, 0);
            }

            extProjects = newMods.toArray(new ProjectInfo[0]);
        }

        public void updateMods(MinecraftInstances instances) {
            if(extProjects == null) {
                extProjects = new ProjectInfo[0];
            }
            try {
                ModsJson newMods = downloadCurrentModsJson(Constants.USER_HOME);
                ModsJson oldMods = parseModsJson(Constants.USER_HOME + "/mods.json");

                if(oldMods != null) {
                    removeOldMods(oldMods, newMods);
                }

                File modsFolder = new File(gameDir + "/mods");
                for (ModsJson.Version newVersion : newMods.versions) {
                    if (!versionName.equals(newVersion.name)) {
                        continue;
                    }

                    ArrayList<ProjectInfo> mergedNewMods = new ArrayList<>(Arrays.asList(newVersion.coreMods));
                    if(defaultMods)
                        mergedNewMods.addAll(Arrays.asList(newVersion.defaultMods));

                    if(extProjects.length == 0 || !modsFolder.exists())
                        downloadAllMods(mergedNewMods);
                    else
                        updateModByType(mergedNewMods);
                    break;
                }

                GsonUtils.objectToJsonFile(Constants.USER_HOME + "/instances.json", instances);
                File newModsFile = new File(Constants.USER_HOME + "/new_mods.json");
                File modsFile = new File(Constants.USER_HOME + "/mods.json");

                modsFile.delete();
                Files.copy(newModsFile.toPath(), modsFile.toPath());
                newModsFile.delete();
            } catch (Exception e) {
                Logger.getInstance().appendToLog("Mods failed to download! Are you offline?\n" + e);
            }
        }
    }
}
