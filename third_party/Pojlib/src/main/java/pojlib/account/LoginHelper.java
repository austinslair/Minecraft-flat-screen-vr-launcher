package pojlib.account;

import android.app.Activity;
import android.content.Context;

import com.microsoft.aad.msal4j.DeviceCode;
import com.microsoft.aad.msal4j.DeviceCodeFlowParameters;
import com.microsoft.aad.msal4j.IAccount;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.PublicClientApplication;
import com.microsoft.aad.msal4j.SilentParameters;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import pojlib.API;
import pojlib.util.GsonUtils;
import pojlib.util.Logger;

/**
 * Microsoft account login for VoxyQuest.
 *
 * The Microsoft application client ID is supplied by the host application.
 * Pojlib deliberately does not carry QuestCraft's Microsoft application ID.
 */
public final class LoginHelper {
    public enum State {
        IDLE,
        STARTING,
        WAITING_FOR_USER,
        EXCHANGING,
        SIGNED_IN,
        ERROR,
        CANCELLED
    }

    public static final Set<String> SCOPES = new HashSet<>();

    private static final Object LOCK = new Object();
    private static final String AUTHORITY = "https://login.microsoftonline.com/consumers/";
    private static final String PREFS_NAME = "voxyquest_auth";
    private static final String PREF_LAST_UUID = "last_account_uuid";

    private static volatile PublicClientApplication pca;
    private static volatile String configuredClientId = "";
    private static volatile Thread loginThread;
    private static volatile CompletableFuture<IAuthenticationResult> loginFuture;
    private static volatile MinecraftAccount currentAccount;

    private static volatile State state = State.IDLE;
    private static volatile String deviceUserCode = "";
    private static volatile String verificationUri = "";
    private static volatile String message = "";
    private static volatile String error = "";
    private static volatile long deviceCodeExpiresAtMs = 0L;

    static {
        SCOPES.add("XboxLive.SignIn");
        SCOPES.add("XboxLive.offline_access");
    }

    private LoginHelper() {}

    public static boolean configure(Activity activity, String clientId) {
        String normalized = clientId == null ? "" : clientId.trim();
        if (normalized.isEmpty()) {
            fail("VoxyQuest Microsoft client ID is not configured.");
            return false;
        }

        synchronized (LOCK) {
            if (pca != null && normalized.equals(configuredClientId)) {
                return true;
            }

            try {
                File authDir = new File(activity.getFilesDir(), "auth");
                if (!authDir.exists() && !authDir.mkdirs()) {
                    throw new IOException("Unable to create private auth directory");
                }

                File cache = new File(authDir, "msal_cache.json");
                if (!cache.exists() && !cache.createNewFile()) {
                    throw new IOException("Unable to create MSAL token cache");
                }

                String cacheData = readFile(cache);
                TokenPersistence persistence = new TokenPersistence(cacheData, cache);
                pca = PublicClientApplication.builder(normalized)
                        .setTokenCacheAccessAspect(persistence)
                        .authority(AUTHORITY)
                        .build();
                configuredClientId = normalized;
                if (state == State.ERROR && error.contains("client ID")) {
                    state = State.IDLE;
                    error = "";
                    message = "";
                }
                return true;
            } catch (Exception e) {
                fail("Unable to initialize Microsoft sign-in: " + safeMessage(e));
                return false;
            }
        }
    }

    public static boolean startLogin(Activity activity, String clientId) {
        synchronized (LOCK) {
            if (state == State.STARTING || state == State.WAITING_FOR_USER || state == State.EXCHANGING) {
                return false;
            }
            clearTransientState();
            state = State.STARTING;
        }

        if (!configure(activity, clientId)) {
            return false;
        }

        loginThread = new Thread(() -> runDeviceCodeLogin(activity), "VoxyQuest-MicrosoftLogin");
        loginThread.start();
        return true;
    }

    private static void runDeviceCodeLogin(Activity activity) {
        try {
            PublicClientApplication application = pca;
            if (application == null) {
                fail("Microsoft sign-in is not initialized.");
                return;
            }

            Consumer<DeviceCode> deviceCodeConsumer = deviceCode -> {
                deviceUserCode = valueOrEmpty(deviceCode.userCode());
                verificationUri = valueOrEmpty(deviceCode.verificationUri());
                deviceCodeExpiresAtMs = System.currentTimeMillis() + (deviceCode.expiresIn() * 1000L);
                message = valueOrEmpty(deviceCode.message());
                state = State.WAITING_FOR_USER;
            };

            loginFuture = application.acquireToken(
                    DeviceCodeFlowParameters.builder(SCOPES, deviceCodeConsumer).build());

            IAuthenticationResult result = loginFuture.get();
            if (result == null || result.accessToken() == null || result.accessToken().isEmpty()) {
                fail("Microsoft sign-in did not return an access token.");
                return;
            }

            state = State.EXCHANGING;
            message = "Microsoft account verified. Signing in to Minecraft...";

            File accountsDir = new File(activity.getFilesDir(), "accounts");
            if (!accountsDir.exists() && !accountsDir.mkdirs()) {
                throw new IOException("Unable to create account directory");
            }

            MinecraftAccount account = MinecraftAccount.login(
                    activity,
                    accountsDir.getAbsolutePath(),
                    result.accessToken());
            if (account == null) {
                fail("Minecraft account login did not return an account.");
                return;
            }

            setCurrentAccount(activity, account);
            state = State.SIGNED_IN;
            error = "";
            message = account.isDemoMode
                    ? "Microsoft account signed in. Minecraft ownership was not detected; demo mode is available."
                    : "Signed in as " + account.username + ".";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (state != State.CANCELLED) {
                fail("Microsoft sign-in was interrupted.");
            }
        } catch (ExecutionException e) {
            if (state != State.CANCELLED) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                fail("Microsoft sign-in failed: " + safeMessage(cause));
            }
        } catch (Exception e) {
            if (state != State.CANCELLED) {
                fail("Minecraft sign-in failed: " + safeMessage(e));
            }
        } finally {
            loginFuture = null;
            loginThread = null;
        }
    }

    public static MinecraftAccount refreshAccount(Activity activity, String uuid) {
        PublicClientApplication application = pca;
        if (application == null || uuid == null || uuid.isEmpty()) {
            return null;
        }

        try {
            Set<IAccount> accountsInCache = application.getAccounts().join();
            for (IAccount account : accountsInCache) {
                IAuthenticationResult result = application.acquireTokenSilently(
                        SilentParameters.builder(SCOPES, account).build()).join();
                MinecraftAccount refreshed = new Msa(activity).performLogin(result.accessToken());
                if (refreshed == null) {
                    continue;
                }
                GsonUtils.objectToJsonFile(
                        new File(activity.getFilesDir(), "accounts/" + refreshed.uuid + ".json").getAbsolutePath(),
                        refreshed);
                if (uuid.equals(refreshed.uuid)) {
                    setCurrentAccount(activity, refreshed);
                    return refreshed;
                }
            }
        } catch (Exception e) {
            Logger.getInstance().appendToLog("MicrosoftLogin | Couldn't refresh token: " + safeMessage(e));
        }
        return null;
    }

    public static void cancelLogin() {
        synchronized (LOCK) {
            CompletableFuture<IAuthenticationResult> future = loginFuture;
            if (future != null) {
                future.cancel(true);
            }
            Thread thread = loginThread;
            if (thread != null) {
                thread.interrupt();
            }
            state = State.CANCELLED;
            message = "Microsoft sign-in cancelled.";
            error = "";
        }
    }

    public static State getState() {
        return state;
    }

    public static String getStateName() {
        return state.name().toLowerCase();
    }

    public static String getDeviceUserCode() {
        return deviceUserCode;
    }

    public static String getVerificationUri() {
        return verificationUri;
    }

    public static String getMessage() {
        return message;
    }

    public static String getError() {
        return error;
    }

    public static long getDeviceCodeExpiresInSeconds() {
        if (deviceCodeExpiresAtMs <= 0L) {
            return 0L;
        }
        return Math.max(0L, (deviceCodeExpiresAtMs - System.currentTimeMillis()) / 1000L);
    }

    public static boolean isSignedIn() {
        return state == State.SIGNED_IN && currentAccount != null;
    }

    public static String getProfileName() {
        MinecraftAccount account = currentAccount;
        return account == null || account.username == null ? "" : account.username;
    }

    public static String getProfileUuid() {
        MinecraftAccount account = currentAccount;
        return account == null || account.uuid == null ? "" : account.uuid;
    }

    public static boolean isDemoMode() {
        MinecraftAccount account = currentAccount;
        return account != null && account.isDemoMode;
    }

    public static String getLastAccountUuid(Activity activity) {
        return activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_LAST_UUID, "");
    }

    private static void setCurrentAccount(Activity activity, MinecraftAccount account) {
        currentAccount = account;
        API.currentAcc = account;
        API.profileName = account.username;
        API.profileUUID = account.uuid;
        API.profileImage = MinecraftAccount.getSkinFaceUrl(account);
        API.isDemoMode = account.isDemoMode;
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_LAST_UUID, account.uuid)
                .apply();
    }

    private static void clearTransientState() {
        deviceUserCode = "";
        verificationUri = "";
        message = "";
        error = "";
        deviceCodeExpiresAtMs = 0L;
    }

    private static void fail(String failure) {
        error = failure == null ? "Microsoft sign-in failed." : failure;
        message = error;
        state = State.ERROR;
        Logger.getInstance().appendToLog("MicrosoftLogin | " + error);
    }

    private static String readFile(File file) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String safeMessage(Throwable throwable) {
        String value = throwable == null ? "" : throwable.getMessage();
        if (value == null || value.trim().isEmpty()) {
            return throwable == null ? "Unknown error" : throwable.getClass().getSimpleName();
        }
        return value;
    }
}
