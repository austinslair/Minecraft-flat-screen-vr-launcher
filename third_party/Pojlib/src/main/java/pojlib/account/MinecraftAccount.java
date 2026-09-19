package pojlib.account;

import android.app.Activity;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;

import pojlib.util.Constants;
import pojlib.util.GsonUtils;
import pojlib.util.Logger;
import pojlib.util.MSAException;

public class MinecraftAccount {
    public String accessToken;
    public String uuid;
    public String username;
    public boolean isDemoMode = false;
    public long expiresOn;
    public final String userType = "msa";

    public static MinecraftAccount login(Activity activity, String gameDir, String msToken) throws MSAException, IOException, JSONException {
        Msa instance = new Msa(activity);
        MinecraftAccount account = instance.performLogin(msToken);

        GsonUtils.objectToJsonFile(gameDir + "/" + account.uuid + ".json", account);
        return account;
    }

    public static boolean removeAccount(Activity activity, String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return false;
        }

        File accountFile = new File(activity.getFilesDir(), "accounts/" + uuid + ".json");
        boolean accountRemoved = !accountFile.exists() || accountFile.delete();

        File accountCache = new File(Constants.USER_HOME + "/cache_data");
        if (accountCache.isFile() && !accountCache.delete()) {
            Logger.getInstance().appendToLog("WARN! Unable to remove stale account cache");
        }

        return accountRemoved;
    }

    // Try this before using login - the account will have been saved to disk if previously logged in.
    public static MinecraftAccount load(String path, String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        return GsonUtils.jsonFileToObject(path + "/" + uuid + ".json", MinecraftAccount.class);
    }

    public static String getSkinFaceUrl(MinecraftAccount account) {
        if (account == null) {
            return null;
        }
        if (account.isDemoMode) {
            return Constants.MINOTAR_URL + "/helm/MHF_Steve";
        }
        return account.uuid == null || account.uuid.isEmpty()
                ? null
                : Constants.MINOTAR_URL + "/helm/" + account.uuid;
    }
}
