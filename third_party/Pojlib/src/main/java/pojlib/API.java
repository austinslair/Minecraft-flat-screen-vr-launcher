package pojlib;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

import org.lwjgl.glfw.CallbackBridge;

import pojlib.account.MinecraftAccount;
import pojlib.util.JREUtils;
import pojlib.util.Logger;
import pojlib.util.download.DownloadManager;
import pojlib.util.json.MinecraftInstances;
import pojlib.util.Constants;
import pojlib.account.LoginHelper;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * This class is the only class used by the launcher to communicate and talk to pojlib. This keeps pojlib and launcher separate.
 * If we ever make breaking change to either project, we can make a new api class to accommodate for those changes without
 * having to make changes to either project deeply.
 */
public class API {

    public static String msaMessage = "";
    public static String model = "Quest";
    private static boolean hasQueried = false;
    private static JsonObject initialResponse;
    public static boolean ignoreInstanceName;
    public static boolean customRAMValue = false;
    public static String profileImage;
    public static String profileName;
    public static String profileUUID;
    public static String memoryValue = "1800";
    public static boolean developerMods;
    public static MinecraftAccount currentAcc;
    public static boolean isDemoMode;
    public static MinecraftInstances.Instance currentInstance;
    private static boolean hasWifi;
    public static boolean advancedDebugger;
    public static boolean gameReady = false;


    /**
     * Add a mod to an instance
     *
     * @param instances Acquired from {@link API#loadAll()}
     * @param instance Acquired from {@link API#(Activity, MinecraftInstances, String, String, boolean, String, String, String, String)}
     *                 or {@link API#load(MinecraftInstances, String)}
     * @param name Project name
     * @param version Project version
     * @param url Mod download URL
     */
    public static void addExtraProject(MinecraftInstances instances, MinecraftInstances.Instance instance, String name, String fileName, String version, String url, String type) {
        InstanceHandler.addExtraProject(instances, instance, name, fileName, version, url, type);
    }

    public static boolean isDownloadsCompleted() {
        return DownloadManager.downloadsCompleted();
    }

    public static float getDownloadPercentage() {
        return DownloadManager.getPercentComplete();
    }

    /**
     * Check if an instance has a mod
     *
     * @param instance Acquired from {@link API#createNewInstance(Activity, MinecraftInstances, String, boolean, String, String, String)}
     *                 or {@link API#load(MinecraftInstances, String)}
     * @param name Project name
     * @return True if the project is already in the instance
     */
    public static boolean hasExtraProject(MinecraftInstances.Instance instance, String name) {
        return InstanceHandler.hasExtraProject(instance, name);
    }

    /**
     * Remove a mod from an instance
     *
     * @param instances Acquired from {@link API#loadAll()}
     * @param instance Acquired from {@link API#createNewInstance(Activity, MinecraftInstances, String, boolean, String, String, String)}
     *                 or {@link API#load(MinecraftInstances, String)}
     * @param name project name
     * @return True if the project was deleted
     */
    public static boolean removeExtraProject(MinecraftInstances instances, MinecraftInstances.Instance instance, String name) {
        return InstanceHandler.removeExtraProject(instances, instance, name);
    }

    public static String[] getQCSupportedVersions(Activity ctx) {
        return APIHandler.getQCSupportedVersions(ctx);
    }

    /**
     * Loads all instances from the filesystem.
     *
     * @return                  A minecraft instance object
     */
    public static MinecraftInstances loadAll() throws IOException {
        return InstanceHandler.load(Constants.USER_HOME);
    }

    /**
     * Load a specific instance by name
     *
     * @param instances Acquired from {@link API#loadAll()}
     * @param name Name of the instance
     * @return The instance, or null if an instance with name does not exist
     */
    public static MinecraftInstances.Instance load(MinecraftInstances instances, String name) {
        return instances.load(name);
    }

    /**
     * Delete an instance
     * NOTE: Only deletes the instance, not the correlated mods for said instance
     *
     * @param instances Acquired from {@link API#loadAll()}
     * @param instance Instance object
     * @return True if it deletes successfully, false otherwise.
     */
    public static boolean deleteInstance(MinecraftInstances instances, MinecraftInstances.Instance instance) throws IOException {
        return InstanceHandler.delete(instances, instance);
    }

    /**
     * Creates a new game instance with a selected mod loader. The latest version of the mod loader will be installed
     *
     * @param activity          The active android activity
     * @param instanceName      The name of the instance being created - can be anything, used for identification
     * @param useDefaultMods    Use QC's default mods for the version (Core mods are automatically included)
     * @param minecraftVersion  The version of minecraft to install
     * @param imageURL          Modpack image url, nullable
     * @return                  A minecraft instance object
     * @throws                  IOException Throws if download of library or asset fails
     */
    public static MinecraftInstances.Instance createNewInstance(Activity activity, MinecraftInstances instances, String instanceName, boolean useDefaultMods, String minecraftVersion, String modLoader, String imageURL) throws IOException {
        return InstanceHandler.create(activity, instances, instanceName, Constants.USER_HOME, useDefaultMods, minecraftVersion, modLoader, imageURL, null);
    }

    /**
     * Creates a new game instance from a mrpack file.
     *
     * @param activity          The active android activity
     * @param instanceName      The name of the instance being created - can be anything, used for identification
     * @param imageURL          Modpack image url, nullable
     * @return                  A minecraft instance object
     * @throws                  IOException Throws if download of library or asset fails
     */
    public static MinecraftInstances.Instance createNewInstance(Activity activity, MinecraftInstances instances, String instanceName, String imageURL, String modLoader, String mrpackFile) throws IOException {
        if(ignoreInstanceName) {
            return InstanceHandler.create(activity, instances, instanceName, Constants.USER_HOME, modLoader, mrpackFile, imageURL);
        } else if (instanceName.contains("/") || instanceName.contains("!")) {
            throw new IOException("You cannot use special characters (!, /, ., etc) when creating instances.");
        } else {
            return InstanceHandler.create(activity, instances, instanceName, Constants.USER_HOME, modLoader, mrpackFile, imageURL);
        }
    }

    /**
     * Update the mods for the selected instance
     *
     * @param instance The instance to update
     */
    public static void prelaunch(Activity activity, MinecraftInstances instances, MinecraftInstances.Instance instance) {
        gameReady = false;
        instance.updateMods(instances);
        if (hasConnection(activity)) {
            try {
                JREUtils.prelaunchCheck(activity, instance);
            } catch (IOException | ExecutionException | InterruptedException e) {
                Logger.getInstance().appendToLog("WARN! Instance launch failed!" + e);
            }
        } else {
            Logger.getInstance().appendToLog("Skipping prelaunch check due to no network connection!");
        }
        DownloadManager.reset();

        MinecraftInstances.CheckVivecraftConfig(instance);
    }

    /**
     * Launch an instance
     *
     * @param activity Android activity object
     * @param account Account object
     * @param instance Instance object from {@link API#createNewInstance(Activity, MinecraftInstances, String, boolean, String, String, String)}
     *                 or {@link API#load(MinecraftInstances, String)}
     */
    public static void launchInstance(Activity activity, MinecraftAccount account, MinecraftInstances.Instance instance) {
        InstanceHandler.launchInstance(activity, account, instance);
    }

    /**
     * Kill the current instance
     *
     * @param activity Android activity object
     */
    public static void restartLauncher(Activity activity) {
        Logger.getInstance().appendToLog("VoxyQuest: Restarting launcher...");
        CallbackBridge.restartRuntimeSession(activity);
    }

    /**
     * Removes the user account
     *
     * @param activity The base directory where minecraft should be setup
     * @param uuid The uuid of the profile to remove
     * @return True if removal was successful
     */
    public static boolean removeAccount(Activity activity, String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return false;
        }

        if (currentAcc != null && uuid.equals(currentAcc.uuid)) {
            currentAcc = null;
            profileImage = null;
            profileName = null;
            profileUUID = null;
            isDemoMode = false;
        }
        return MinecraftAccount.removeAccount(activity, uuid);
    }

    /**
     * Start the login process
     *
     * @param activity Android activity object
     */
    public static void login(Activity activity, @Nullable String accountUUID)
    {
        if(accountUUID == null) {
            currentAcc = null;
            LoginHelper.login(activity);
            return;
        }

        MinecraftAccount acc = MinecraftAccount.load(activity.getFilesDir() + "/accounts", accountUUID);
        if(acc != null && (acc.expiresOn >= System.currentTimeMillis() || !hasConnection(activity) || acc.isDemoMode)) {
            API.profileImage = MinecraftAccount.getSkinFaceUrl(acc);
            API.profileName = acc.username;
            API.profileUUID = acc.uuid;
            API.isDemoMode = acc.isDemoMode;
            currentAcc = acc;
            return;
        } else if(acc != null && acc.expiresOn < System.currentTimeMillis()) {
            MinecraftAccount refreshed = LoginHelper.refreshAccount(activity, accountUUID);
            if(refreshed != null) {
                API.profileImage = MinecraftAccount.getSkinFaceUrl(refreshed);
                API.profileName = refreshed.username;
                API.profileUUID = refreshed.uuid;
                API.isDemoMode = refreshed.isDemoMode;
                currentAcc = refreshed;
                return;
            }
        }

        LoginHelper.login(activity);
    }

    /**
     * Check whether Android reports a validated internet connection.
     *
     * This avoids a synchronous HTTP probe on the launcher thread while still
     * rejecting networks that Android has not validated for internet access.
     *
     * @param activity activity object
     * @return true if the active network has validated internet access
     */
    public static boolean hasConnection(Context activity) {
        ConnectivityManager connManager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connManager == null) {
            hasWifi = false;
            return false;
        }

        NetworkCapabilities capabilities = connManager.getNetworkCapabilities(connManager.getActiveNetwork());
        boolean hasNetwork = capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

        hasWifi = hasNetwork;
        if (!hasNetwork) {
            Logger.getInstance().appendToLog("WARN! Device has no validated internet connection!");
        }
        return hasNetwork;
    }
}
