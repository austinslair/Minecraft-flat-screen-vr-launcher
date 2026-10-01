package pojlib.util;

import android.view.Surface;

/** Attach before starting the JVM; the window remains owned until process restart. */
public final class FlatDisplay {
    static { System.loadLibrary("pojavexec"); }
    private FlatDisplay() {}
    public static int width = 1280;
    public static int height = 720;
    public static void attach(Surface surface, int surfaceWidth, int surfaceHeight) {
        width = surfaceWidth;
        height = surfaceHeight;
        attachNative(surface, surfaceWidth, surfaceHeight);
    }
    public static native void detachNative();
    private static native void attachNative(Surface surface, int width, int height);
}
