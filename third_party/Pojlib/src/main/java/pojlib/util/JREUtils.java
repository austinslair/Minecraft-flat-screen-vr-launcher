package pojlib.util;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.util.ArrayMap;
import android.util.Log;

import com.oracle.dalvik.VMLauncher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import pojlib.API;

import pojlib.PojlibRuntime;
import pojlib.install.Installer;
import pojlib.install.MinecraftMeta;
import pojlib.util.json.MinecraftInstances;

public class JREUtils {
    private JREUtils() {}

    public static String LD_LIBRARY_PATH;
    public static Map<String, String> jreReleaseList;
    public static String instanceHome;
    public static String jvmLibraryPath;
    private static String sNativeLibDir;
    private static String runtimeDir;

    public static String findInLdLibPath(String libName) {
        if(Os.getenv("LD_LIBRARY_PATH")==null) {
            try {
                if (LD_LIBRARY_PATH != null) {
                    Os.setenv("LD_LIBRARY_PATH", LD_LIBRARY_PATH, true);
                }
            }catch (ErrnoException e) {
                e.printStackTrace();
            }
            return libName;
        }
        for (String libPath : Os.getenv("LD_LIBRARY_PATH").split(":")) {
            File f = new File(libPath, libName);
            if (f.exists() && f.isFile()) {
                return f.getAbsolutePath();
            }
        }
        return libName;
    }

    public static ArrayList<File> locateLibs(File path) {
        ArrayList<File> returnValue = new ArrayList<>();
        File[] list = path.listFiles();
        if(list != null) {
            for(File f : list) {
                if(f.isFile() && f.getName().endsWith(".so")) {
                    returnValue.add(f);
                }else if(f.isDirectory()) {
                    returnValue.addAll(locateLibs(f));
                }
            }
        }
        return returnValue;
    }

    private static boolean loadRuntimeLibrary(String label, String path) {
        Logger.getInstance().appendToLog("VoxyQuest JVM: loading " + label);
        boolean loaded = dlopen(path);
        Logger.getInstance().appendToLog(
                "VoxyQuest JVM: " + label + (loaded ? " loaded" : " failed")
        );
        return loaded;
    }

    public static void initJavaRuntime() {
        loadRuntimeLibrary("libjli.so", findInLdLibPath("libjli.so"));
        if(!loadRuntimeLibrary("libjvm.so", "libjvm.so")){
            loadRuntimeLibrary("libjvm.so (direct)", jvmLibraryPath+"/libjvm.so");
        }
        loadRuntimeLibrary("libverify.so", findInLdLibPath("libverify.so"));
        loadRuntimeLibrary("libjava.so", findInLdLibPath("libjava.so"));
        loadRuntimeLibrary("libnet.so", findInLdLibPath("libnet.so"));
        loadRuntimeLibrary("libnio.so", findInLdLibPath("libnio.so"));
        loadRuntimeLibrary("libawt.so", findInLdLibPath("libawt.so"));
        loadRuntimeLibrary("libawt_headless.so", findInLdLibPath("libawt_headless.so"));
        loadRuntimeLibrary("libfreetype.so", findInLdLibPath("libfreetype.so"));
        loadRuntimeLibrary("libfontmanager.so", findInLdLibPath("libfontmanager.so"));

        ArrayList<File> runtimeLibs = locateLibs(new File(runtimeDir + "/lib"));
        Logger.getInstance().appendToLog(
                "VoxyQuest JVM: preloading " + runtimeLibs.size() + " runtime libraries"
        );
        for(File f : runtimeLibs) {
            Logger.getInstance().appendToLog("VoxyQuest JVM: preloading " + f.getName());
            dlopen(f.getAbsolutePath());
        }
        Logger.getInstance().appendToLog("VoxyQuest JVM: runtime library preload complete");
    }

    public static void redirectAndPrintJRELog() {
        Log.v("jrelog","Log starts here");
        JREUtils.logToLogger(Logger.getInstance());
        new Thread(new Runnable(){
            int failTime = 0;
            ProcessBuilder logcatPb;
            @Override
            public void run() {
                try {
                    if (logcatPb == null) {
                        logcatPb = new ProcessBuilder().command("logcat", "-v", "brief", "-s", "jrelog:I", "LIBGL:I").redirectErrorStream(true);
                    }
                            Log.i("jrelog-logcat","Clearing logcat");
                    new ProcessBuilder().command("logcat", "-c").redirectErrorStream(true).start();
                    Log.i("jrelog-logcat","Starting logcat");
                    java.lang.Process p = logcatPb.start();

                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = p.getInputStream().read(buf)) != -1) {
                        String currStr = new String(buf, 0, len);
                        Logger.getInstance().appendToLog(currStr);
                    }
                            if (p.waitFor() != 0) {
                        Log.e("jrelog-logcat", "Logcat exited with code " + p.exitValue());
                        failTime++;
                        Log.i("jrelog-logcat", (failTime <= 10 ? "Restarting logcat" : "Too many restart fails") + " (attempt " + failTime + "/10");
                        if (failTime <= 10) {
                            run();
                        } else {
                            Logger.getInstance().appendToLog("ERROR: Unable to get more log.");
                        }
                            }
                } catch (Throwable e) {
                    Log.e("jrelog-logcat", "Exception on logging thread", e);
                    Logger.getInstance().appendToLog("Exception on logging thread:\n" + Log.getStackTraceString(e));
                }
            }
        }).start();
        Log.i("jrelog-logcat","Logcat thread started");
    }

    public static void relocateLibPath(final Context ctx) {
        sNativeLibDir = ctx.getApplicationInfo().nativeLibraryDir;

        LD_LIBRARY_PATH = ctx.getFilesDir() + "/runtimes/JRE/bin:" + ctx.getFilesDir() + "/runtimes/JRE/lib:" +
                "/system/lib64:/vendor/lib64:/vendor/lib64/hw:" +
                sNativeLibDir;
    }

    public static void setJavaEnvironment(Activity activity, MinecraftInstances.Instance instance) throws Throwable {
        Map<String, String> envMap = new ArrayMap<>();
        envMap.put("POJLIB_NATIVEDIR", activity.getApplicationInfo().nativeLibraryDir);
        envMap.put("JAVA_HOME", activity.getFilesDir() + "/runtimes/JRE");
        envMap.put("HOME", instance.gameDir);
        //envMap.put("APP_HOME", Constants.USER_HOME);
        envMap.put("TMPDIR", activity.getCacheDir().getAbsolutePath());
        envMap.put("VR_MODEL", API.model);
        envMap.put("POJLIB_RENDERER", "LightThinWrapper");
        envMap.put("VOXYQUEST_LAUNCH_LOG", new File(Constants.USER_HOME, "latestlog.txt").getAbsolutePath());

        envMap.put("LD_LIBRARY_PATH", LD_LIBRARY_PATH);
        envMap.put("PATH", activity.getFilesDir() + "/runtimes/JRE/bin:" + Os.getenv("PATH"));

        File customEnvFile = new File(Constants.USER_HOME, "custom_env.txt");
        if (customEnvFile.exists() && customEnvFile.isFile()) {
            BufferedReader reader = new BufferedReader(new FileReader(customEnvFile));
            String line;
            while ((line = reader.readLine()) != null) {
                // Not use split() as only split first one
                int index = line.indexOf("=");
                envMap.put(line.substring(0, index), line.substring(index + 1));
            }
            reader.close();
        }
        envMap.put("LIBGL_ES", "2");
        for (Map.Entry<String, String> env : envMap.entrySet()) {
            Logger.getInstance().appendToLog("Added custom env: " + env.getKey() + "=" + env.getValue());
            Os.setenv(env.getKey(), env.getValue(), true);
        }

        File serverFile = new File(activity.getFilesDir() + "/runtimes/JRE/lib/server/libjvm.so");
        jvmLibraryPath = activity.getFilesDir() + "/runtimes/JRE/lib/" + (serverFile.exists() ? "server" : "client");
        Log.d("DynamicLoader","Base LD_LIBRARY_PATH: "+LD_LIBRARY_PATH);
        Log.d("DynamicLoader","Internal LD_LIBRARY_PATH: "+jvmLibraryPath+":"+LD_LIBRARY_PATH);
        setLdLibraryPath(jvmLibraryPath+":"+LD_LIBRARY_PATH);
    }

    // Called before game launch to ensure all files are present and correct
    public static void prelaunchCheck(Activity activity, MinecraftInstances.Instance instance) throws IOException, ExecutionException, InterruptedException {
        PojlibRuntime.installLWJGL(activity);
        Installer.installJVM(activity);
        Installer.installClient(MinecraftMeta.getVersionInfo(instance.versionName), Constants.USER_HOME).get();
        Installer.installLibraries(MinecraftMeta.getVersionInfo(instance.versionName), Constants.USER_HOME).get();
        Installer.installAssets(MinecraftMeta.getVersionInfo(instance.versionName), Constants.USER_HOME).get();
    }

    public static int launchJavaVM(final Activity activity, final List<String> JVMArgs, MinecraftInstances.Instance instance) throws Throwable {
        // The embedded JVM loads filesystem paths, unlike ART's APK zip loader.
        // Fail before entering native code if export did not extract these libraries.
        for (String library : new String[]{"libpojavexec.so", "liblwjgl.so", "libjnidispatch.so"}) {
            File nativeFile = new File(activity.getApplicationInfo().nativeLibraryDir, library);
            if (!nativeFile.isFile() || !nativeFile.canRead()) {
                throw new IOException("Required native library is not extracted: " + library +
                        ". Install the complete APK with native library extraction enabled.");
            }
        }
        JREUtils.relocateLibPath(activity);
        setJavaEnvironment(activity, instance);

        final String graphicsLib = loadGraphicsLibrary();
        List<String> userArgs = getJavaArgs(activity, instance);

        // Add automatically generated args. Keep the initial heap much smaller than
        // the maximum heap: on Quest the OpenXR/Godot/native side remains resident,
        // and committing the full Minecraft heap during VM creation can make Android
        // kill the process before Java has a chance to print an error.
        if (API.customRAMValue) {
            long maxHeapMb;
            try {
                maxHeapMb = Long.parseLong(API.memoryValue);
            } catch (NumberFormatException invalidMemory) {
                maxHeapMb = 768L;
            }
            long initialHeapMb = Math.min(384L, Math.max(256L, maxHeapMb / 3L));
            Logger.getInstance().appendToLog(
                    "QuestCraft: Setting JVM memory to " + initialHeapMb + "MB initial / " +
                            maxHeapMb + "MB max (Custom)"
            );
            userArgs.add("-Xms" + initialHeapMb + "M");
            userArgs.add("-Xmx" + maxHeapMb + "M");
        } else {
            ActivityManager manager = (ActivityManager) activity.getSystemService(Activity.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo ami = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(ami);
            long availMem = (ami.availMem-ami.threshold)/(1024*1024);
            availMem *= 0.8; // Lossy, but should work...
            long allocatedRam = Math.max(availMem, 1536);

            Logger.getInstance().appendToLog("QuestCraft: Setting JVM memory to " + allocatedRam + "MB");


            userArgs.add("-Xms" + 1024 + "M");
            userArgs.add("-Xmx" + allocatedRam + "M");
        }


        // Garbage collection
        userArgs.add("-XX:+UseZGC");
        userArgs.add("-XX:+ZGenerational");
        userArgs.add("-XX:-ZProactive");
        userArgs.add("-XX:+UnlockDiagnosticVMOptions");
        userArgs.add("-XX:-ImplicitNullChecks");
        userArgs.add("-XX:+DisableExplicitGC");

        // Java should run at max
        userArgs.add("-XX:+UnlockExperimentalVMOptions");
        userArgs.add("-XX:+UseCriticalJavaThreadPriority");

        // Android sig fix
        userArgs.add("-XX:+UseSignalChaining");

        userArgs.add("-Dorg.lwjgl.opengl.libname=" + graphicsLib);
        userArgs.add("-Dorg.lwjgl.opengles.libname=" + "/system/lib64/libGLESv3.so");
        userArgs.add("-Dorg.lwjgl.egl.libname=" + "/system/lib64/libEGL_dri.so");

        userArgs.addAll(JVMArgs);
        // Launch arguments contain the Minecraft access token; never log them.

        runtimeDir = activity.getFilesDir() + "/runtimes/JRE";

        Logger.getInstance().appendToLog("VoxyQuest JVM: preparing runtime libraries");
        initJavaRuntime();
        Logger.getInstance().appendToLog("VoxyQuest JVM: runtime libraries ready");

        int chdirResult = chdir(instance.gameDir);
        Logger.getInstance().appendToLog("VoxyQuest JVM: chdir result " + chdirResult);
        if (chdirResult != 0) {
            throw new IOException("Could not enter Minecraft game directory");
        }
        userArgs.add(0,"java"); //argv[0] is the program name according to C standard.

        Logger.getInstance().appendToLog("VoxyQuest JVM: entering native Java launcher");
        int exitCode = VMLauncher.launchJVM(userArgs.toArray(new String[0]));
        Logger.getInstance().appendToLog("VoxyQuest JVM: native Java launcher returned " + exitCode);
        Logger.getInstance().appendToLog("Java Exit code: " + exitCode);
        return exitCode;
    }

    private static void writeDNS(Context ctx, File out) throws IOException {
        FileWriter writer = new FileWriter(out);

        if(!API.hasConnection(ctx)) {
            writer.write("nameserver 8.8.8.8\n");
            writer.write("nameserver 8.8.4.4");
            writer.flush();
            writer.close();
            return;
        }

        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network activeNetwork = cm.getActiveNetwork();
        LinkProperties lp = cm.getLinkProperties(activeNetwork);
        if(lp == null)
            return;

        List<InetAddress> dnsServers = lp.getDnsServers();
        for (InetAddress dns : dnsServers) {
            writer.write(String.format("nameserver %s\n", dns.getHostAddress()));
            writer.flush();
        }
        writer.close();
    }

    /**
     *  Gives an argument list filled with both the user args
     *  and the auto-generated ones (eg. the window resolution).
     * @param ctx The application context
     * @return A list filled with args.
     */
    public static List<String> getJavaArgs(Context ctx, MinecraftInstances.Instance instance) {
        File resConfFile = new File(Constants.USER_HOME + "/hacks/resolv.conf");
        try {
            if(!resConfFile.exists()) {
                resConfFile.createNewFile();
            }
            writeDNS(ctx, resConfFile);
        } catch (IOException e) {
            Logger.getInstance().appendToLog("Couldn't write DNS servers! " + e.getMessage());
        }
        return new ArrayList<>(Arrays.asList(
                "-Djava.home=" + new File(ctx.getFilesDir(), "runtimes/JRE"),
                "-Djava.io.tmpdir=" + ctx.getCacheDir().getAbsolutePath(),
                "-Duser.home=" + instance.gameDir,
                "-Duser.language=" + System.getProperty("user.language"),
                "-Dos.name=Linux",
                "-Dos.version=Android-" + Build.VERSION.RELEASE,
                "-Dorg.lwjgl.librarypath=" + ctx.getApplicationInfo().nativeLibraryDir,
                "-Djna.boot.library.path=" + ctx.getApplicationInfo().nativeLibraryDir,
                "-Djna.nosys=true",
                "-Djna.nounpack=true",
                "-Djna.tmpdir=" + ctx.getCacheDir().getAbsolutePath(),
                "-Djava.library.path=" + ctx.getApplicationInfo().nativeLibraryDir,
                "-Dglfwstub.windowWidth=" + FlatDisplay.width,
                "-Dglfwstub.windowHeight=" + FlatDisplay.height,
                "-Dglfwstub.gamepadStateFile=" + new File(ctx.getFilesDir(), "flat-gamepad.bin").getAbsolutePath(),
                "-Dvoxyquest.readyFile=" + new File(ctx.getFilesDir(), "minecraft-first-frame").getAbsolutePath(),
                "-Dglfwstub.initEgl=false",
                "-Dlog4j2.formatMsgNoLookups=true", //Log4j RCE mitigation
                "-Dnet.minecraft.clientmodname=" + "VoxyQuest",
                "-Dext.net.resolvPath=" + resConfFile,
                "-Dsodium.checks.issue2561=false",
                "-Dorg.sqlite.lib.path=" + ctx.getApplicationInfo().nativeLibraryDir
        ));
    }

    /**
     * Parse and separate java arguments in a user friendly fashion
     * It supports multi line and absence of spaces between arguments
     * The function also supports auto-removal of improper arguments, although it may miss some.
     *
     * @param args The un-parsed argument list.
     * @return Parsed args as an ArrayList
     */
    public static ArrayList<String> parseJavaArguments(String args){
        ArrayList<String> parsedArguments = new ArrayList<>(0);
        args = args.trim().replace(" ","");
        //For each prefixes, we separate args.
        for(String prefix : new String[]{"-XX:-","-XX:+", "-XX:","--","-"}){
            while (true){
                int start = args.indexOf(prefix);
                if(start == -1) break;
                //Get the end of the current argument
                int end = args.indexOf("-", start + prefix.length());
                if(end == -1) end = args.length();

                //Extract it
                String parsedSubString = args.substring(start, end);
                args = args.replace(parsedSubString, "");

                //Check if two args aren't bundled together by mistake
                if(parsedSubString.indexOf('=') == parsedSubString.lastIndexOf('=')) {
                    int arraySize = parsedArguments.size();
                    if(arraySize > 0){
                        String lastString = parsedArguments.get(arraySize - 1);
                        // Looking for list elements
                        if(lastString.charAt(lastString.length() - 1) == ',' ||
                                parsedSubString.contains(",")){
                            parsedArguments.set(arraySize - 1, lastString + parsedSubString);
                            continue;
                        }
                    }
                    parsedArguments.add(parsedSubString);
                }
                else Log.w("JAVA ARGS PARSER", "Removed improper arguments: " + parsedSubString);
            }
        }
        return parsedArguments;
    }

    /**
     * Open the render library in accordance to the settings.
     * It will fallback if it fails to load the library.
     * @return The name of the loaded library
     */
    public static String loadGraphicsLibrary(){
        return "libltw.so";
    }

    public static native long getEGLContextPtr();
    public static native long getEGLDisplayPtr();
    public static native long getEGLConfigPtr();
    public static native int chdir(String path);
    public static native void logToLogger(final Logger logger);
    public static native boolean dlopen(String libPath);
    public static native void setLdLibraryPath(String ldLibraryPath);

    static {
        System.loadLibrary("pojavexec");
        System.loadLibrary("istdio");
    }
}
