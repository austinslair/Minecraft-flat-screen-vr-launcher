package com.oracle.dalvik;

import pojlib.util.Logger;

public final class VMLauncher {
	private VMLauncher() {
	}
	public static native int launchJVM(String[] args);

	static {
		// JREUtils already loads pojavexec. The native JVM launcher is built into that
		// same library so Quest does not need to load a second JNI library at the exact
		// point where the previous builds terminated.
		Logger.getInstance().appendToLog("VoxyQuest launch: JVM launcher: ensuring integrated native library");
		System.loadLibrary("pojavexec");
		Logger.getInstance().appendToLog("VoxyQuest launch: JVM launcher: integrated native library ready");
	}
}
