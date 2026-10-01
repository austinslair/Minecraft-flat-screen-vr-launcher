package pojlib.install;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.SystemClock;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import pojlib.util.Constants;
import pojlib.util.download.DownloadUtils;

/**
 * Produces a NeoForge version's processed Minecraft client on the headset by running NeoForge's
 * official installer in the launcher's setup process, as a desktop launcher would. This keeps
 * those files (about 50 MB per version) out of the APK; they are only created for versions
 * that are actually installed, and only once.
 */
final class NeoForgeSetup {
    private static final String MAVEN = "https://maven.neoforged.net/releases/net/neoforged/neoforge/";
    private static final String SERVICE = "dev.voxyquest.bridge.NeoForgeSetupService";
    private static final String PROCESS_SUFFIX = ":neoforge_setup";
    private static final long START_TIMEOUT_MS = 60_000L;
    private static final long RUN_TIMEOUT_MS = 30 * 60_000L;

    /** A file the installer writes, with an entry that only a complete copy contains. */
    static final class Output {
        final File file;
        final String marker;

        Output(File file, String marker) {
            this.file = file;
            this.marker = marker;
        }

        boolean isPresent() {
            if (!file.isFile()) return false;
            try (JarFile jar = new JarFile(file)) {
                return jar.getJarEntry(marker) != null;
            } catch (IOException e) {
                return false;
            }
        }
    }

    private NeoForgeSetup() {}

    static void run(Activity activity, String minecraft, String loader, Output[] outputs,
            Consumer<String> progress) throws IOException {
        if (allPresent(outputs)) return;
        File home = new File(Constants.USER_HOME);
        File installers = new File(home, "neoforge-installers");
        Files.createDirectories(installers.toPath());
        File installer = new File(installers, "neoforge-" + loader + "-installer.jar");
        String url = MAVEN + loader + "/neoforge-" + loader + "-installer.jar";
        progress.accept("Downloading the NeoForge " + loader + " installer…");
        String sha1 = readSha1(url + ".sha1", installers);
        if (!installer.isFile() || !DownloadUtils.compareSHA1(installer, sha1)) {
            DownloadUtils.downloadFile(url, installer);
            if (!DownloadUtils.compareSHA1(installer, sha1))
                throw new IOException("The NeoForge installer download is corrupt");
        }
        // The installer refuses to run in a folder without a launcher profile list.
        File profiles = new File(home, "launcher_profiles.json");
        if (!profiles.isFile()) Files.write(profiles.toPath(), "{\"profiles\":{}}".getBytes(StandardCharsets.UTF_8));
        File status = new File(home, "neoforge-setup-status.txt");
        Files.deleteIfExists(status.toPath());

        progress.accept("Setting up NeoForge " + loader + " for Minecraft " + minecraft +
                ". This downloads and prepares Minecraft and takes a few minutes…");
        Intent start = new Intent().setComponent(new ComponentName(activity.getPackageName(), SERVICE))
                .putExtra("installer", installer.getAbsolutePath())
                .putExtra("target", home.getAbsolutePath());
        if (activity.startService(start) == null) throw new IOException("Could not start NeoForge setup");
        File log = new File(installer.getPath() + ".log");
        waitForSetup(activity, status, log, progress);

        if (!allPresent(outputs)) {
            String result = status.isFile()
                    ? new String(Files.readAllBytes(status.toPath()), StandardCharsets.UTF_8).trim() : "no result";
            File console = new File(home, "neoforge-setup-log.txt");
            // Copy both logs' ends into latestlog.txt, which Settings can export.
            pojlib.util.Logger.getInstance().appendToLog("VoxyQuest NeoForge setup failed (" + result + ")\n"
                    + "--- " + log.getName() + " ---\n" + tail(log, 60) + "\n"
                    + "--- " + console.getName() + " ---\n" + tail(console, 60));
            String reason = lastLine(log);
            throw new IOException("NeoForge setup did not finish (" + result + ")"
                    + (reason.isEmpty() ? "" : ": " + reason) + ". Details are in the launcher log (Settings, export log).");
        }
        progress.accept("NeoForge " + loader + " is set up");
    }

    private static boolean allPresent(Output[] outputs) {
        for (Output output : outputs) if (!output.isPresent()) return false;
        return true;
    }

    /** Waits for the setup process to start and then to exit, relaying the installer's progress. */
    private static void waitForSetup(Activity activity, File status, File log, Consumer<String> progress)
            throws IOException {
        ActivityManager manager = (ActivityManager) activity.getSystemService(Activity.ACTIVITY_SERVICE);
        String process = activity.getPackageName() + PROCESS_SUFFIX;
        long started = SystemClock.elapsedRealtime();
        boolean seen = false;
        String lastLine = "";
        while (true) {
            boolean running = isRunning(manager, process);
            seen |= running;
            if (!running && (seen || status.isFile())) return;
            long elapsed = SystemClock.elapsedRealtime() - started;
            if (!seen && elapsed > START_TIMEOUT_MS) throw new IOException("NeoForge setup did not start");
            if (elapsed > RUN_TIMEOUT_MS) throw new IOException("NeoForge setup took longer than 30 minutes");
            String line = lastLine(log);
            if (!line.isEmpty() && !line.equals(lastLine)) {
                lastLine = line;
                progress.accept("Setting up NeoForge: " + (line.length() > 120 ? line.substring(0, 120) + "…" : line));
            }
            SystemClock.sleep(1000L);
        }
    }

    private static boolean isRunning(ActivityManager manager, String process) {
        List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes == null) return false;
        for (ActivityManager.RunningAppProcessInfo info : processes) {
            if (process.equals(info.processName)) return true;
        }
        return false;
    }

    private static String tail(File file, int lines) {
        if (!file.isFile()) return "(missing)";
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long start = Math.max(0L, input.length() - 16384L);
            byte[] bytes = new byte[(int) (input.length() - start)];
            input.seek(start);
            input.readFully(bytes);
            String[] all = new String(bytes, StandardCharsets.UTF_8).split("\n");
            return String.join("\n", java.util.Arrays.copyOfRange(all, Math.max(0, all.length - lines), all.length));
        } catch (IOException e) {
            return "(unreadable: " + e.getMessage() + ")";
        }
    }

    private static String lastLine(File log) {
        if (!log.isFile()) return "";
        try (RandomAccessFile file = new RandomAccessFile(log, "r")) {
            long start = Math.max(0L, file.length() - 512L);
            byte[] tail = new byte[(int) (file.length() - start)];
            file.seek(start);
            file.readFully(tail);
            String[] lines = new String(tail, StandardCharsets.UTF_8).trim().split("\n");
            return lines.length == 0 ? "" : lines[lines.length - 1].trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static String readSha1(String url, File directory) throws IOException {
        File temp = File.createTempFile("neoforge-", ".sha1", directory);
        try {
            DownloadUtils.downloadFile(url, temp);
            String sha1 = new String(Files.readAllBytes(temp.toPath()), StandardCharsets.UTF_8).trim();
            if (!sha1.matches("[0-9a-fA-F]{40}")) throw new IOException("Unexpected NeoForge checksum");
            return sha1;
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }
}
