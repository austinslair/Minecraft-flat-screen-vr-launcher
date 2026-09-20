package pojlib.util;

import android.view.Surface;

/** Attach before starting the JVM; the window remains owned until process restart. */
public final class FlatDisplay {
    static { System.loadLibrary("pojavexec"); }
    private FlatDisplay() {}
    public static native void attach(Surface surface, int width, int height);
}
