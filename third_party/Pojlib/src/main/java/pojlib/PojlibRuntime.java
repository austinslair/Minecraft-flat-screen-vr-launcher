package pojlib;

import static android.os.Build.VERSION.SDK_INT;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.util.DisplayMetrics;

import org.lwjgl.glfw.CallbackBridge;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import pojlib.input.AWTInputBridge;
import pojlib.util.Constants;
import pojlib.util.FileUtil;
import pojlib.util.Logger;

/**
 * Android host services used by the Minecraft runtime.
 *
 * This class deliberately has no dependency on a UI/game engine. Godot owns
 * the Android Activity through VoxyQuestBridge and initializes this runtime.
 */
public final class PojlibRuntime {
    private static volatile ClipboardManager clipboard;
    private static volatile Context applicationContext;
    private static volatile boolean initialized;
    public static volatile DisplayMetrics currentDisplayMetrics;

    private PojlibRuntime() {}

    public static synchronized void initialize(Activity activity) {
        Objects.requireNonNull(activity, "activity");
        if (!initialized) {
            Constants.initConstants(activity);
            initialized = true;
        }
        applicationContext = activity.getApplicationContext();
        clipboard = (ClipboardManager) applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
        updateWindowSize(activity);
        CallbackBridge.nativeSetUseInputStackQueue(true);
    }

    public static boolean isInitialized() {
        return initialized && applicationContext != null;
    }

    public static void ensureInitialized(Activity activity) {
        if (!isInitialized() || clipboard == null || currentDisplayMetrics == null) {
            initialize(activity);
        }
    }

    public static String installLWJGL(Activity activity) throws IOException {
        ensureInitialized(activity);
        Logger.getInstance().appendToLog("Checking LWJGL");
        File lwjgl = new File(Constants.USER_HOME + "/lwjgl3/lwjgl-glfw-classes.jar");
        byte[] lwjglAsset = FileUtil.loadFromAssetToByte(activity, "lwjgl/lwjgl-glfw-classes.jar");

        if (!lwjgl.exists() || !FileUtil.matchingAssetFile(lwjgl, lwjglAsset)) {
            if (lwjgl.exists() && !lwjgl.delete()) {
                Logger.getInstance().appendToLog("WARN! Unable to delete stale LWJGL jar");
            }
            Objects.requireNonNull(lwjgl.getParentFile()).mkdirs();
            FileUtil.write(lwjgl.getAbsolutePath(), lwjglAsset);
        }

        Logger.getInstance().appendToLog("LWJGL installed");
        return lwjgl.getAbsolutePath();
    }

    public static String installNeoForgeLWJGL(Activity activity) throws IOException {
        ensureInitialized(activity);
        File lwjgl = new File(Constants.USER_HOME + "/lwjgl3/neoforge/lwjgl-glfw-classes.jar");
        byte[] asset = FileUtil.loadFromAssetToByte(activity, "lwjgl/lwjgl-neoforge-classes.jar");
        if (!lwjgl.exists() || !FileUtil.matchingAssetFile(lwjgl, asset)) {
            Objects.requireNonNull(lwjgl.getParentFile()).mkdirs();
            FileUtil.write(lwjgl.getAbsolutePath(), asset);
        }
        return lwjgl.getAbsolutePath();
    }

    public static void restartSession(Activity activity) {
        activity.runOnUiThread(() -> {
            Intent start = activity.getPackageManager().getLaunchIntentForPackage(activity.getApplicationInfo().packageName);
            if (start == null) {
                Logger.getInstance().appendToLog("WARN! Unable to restart VoxyQuest: launch intent missing");
                return;
            }
            start.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(start);
            activity.finish();
            Process.killProcess(Process.myPid());
        });
    }

    public static DisplayMetrics getDisplayMetrics(Activity activity) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        if (activity.isInMultiWindowMode() || activity.isInPictureInPictureMode()) {
            displayMetrics = activity.getResources().getDisplayMetrics();
        } else if (SDK_INT >= Build.VERSION_CODES.R) {
            activity.getDisplay().getRealMetrics(displayMetrics);
        } else {
            activity.getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);
        }
        currentDisplayMetrics = displayMetrics;
        return displayMetrics;
    }

    public static void updateWindowSize(Activity activity) {
        currentDisplayMetrics = getDisplayMetrics(activity);
        CallbackBridge.physicalWidth = currentDisplayMetrics.widthPixels;
        CallbackBridge.physicalHeight = currentDisplayMetrics.heightPixels;
    }

    public static float dpToPx(float dp) {
        return currentDisplayMetrics == null ? dp : dp * currentDisplayMetrics.density;
    }

    public static float pxToDp(float px) {
        return currentDisplayMetrics == null ? px : px / currentDisplayMetrics.density;
    }

    public static void querySystemClipboard() {
        String text = readPlainTextClipboard();
        AWTInputBridge.nativeClipboardReceived(text, "plain");
    }

    public static void putClipboardData(String data, String mimeType) {
        ClipboardManager manager = clipboard;
        if (manager == null) return;

        ClipData clipData;
        if ("text/html".equals(mimeType)) {
            clipData = ClipData.newHtmlText("Minecraft", data, data);
        } else {
            clipData = ClipData.newPlainText("Minecraft", data);
        }
        manager.setPrimaryClip(clipData);
    }

    public static void copyToClipboard(String data) {
        putClipboardData(data == null ? "" : data, "text/plain");
    }

    public static String readPlainTextClipboard() {
        ClipboardManager manager = clipboard;
        Context context = applicationContext;
        if (manager == null || context == null || !manager.hasPrimaryClip()) return "";
        ClipData data = manager.getPrimaryClip();
        if (data == null || data.getItemCount() == 0) return "";
        CharSequence value = data.getItemAt(0).coerceToText(context);
        return value == null ? "" : value.toString();
    }
}
