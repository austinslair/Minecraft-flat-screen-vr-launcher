package pojlib.install;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import pojlib.util.FileUtil;
import pojlib.util.download.DownloadUtils;

/** Installs the Android/arm64 Java runtime used by VoxyQuest. */
final class VoxyQuestJavaRuntime {
    // Pin the tested QuestCraft Java 22 runtime instead of following a mutable "latest" release.
    private static final String JRE_URL =
            "https://github.com/QuestCraftPlusPlus/android-openjdk-build-multiarch/"
                    + "releases/download/jre22-6.0.0/JRE.zip";
    private static final long MAX_EXTRACTED_BYTES = 512L * 1024L * 1024L;
    private static final String AWT_LIBRARY = "libawt_xawt.so";

    private VoxyQuestJavaRuntime() {}

    static void install(Activity activity, Consumer<String> progress) throws IOException {
        File runtimes = new File(activity.getFilesDir(), "runtimes");
        File jre = new File(runtimes, "JRE");
        if (isReady(jre)) return;

        Files.createDirectories(runtimes.toPath());
        File archive = new File(runtimes, "JRE.zip");
        Path staging = null;
        Path backup = null;
        boolean installed = false;

        try {
            progress.accept("Downloading Java runtime…");
            DownloadUtils.downloadFile(JRE_URL, archive);
            if (!archive.isFile() || archive.length() < 1024L * 1024L) {
                throw new IOException("Java runtime download is empty or incomplete");
            }

            progress.accept("Extracting Java runtime…");
            staging = Files.createTempDirectory(runtimes.toPath(), "JRE-install-");
            extractRuntime(archive, staging);

            Path serverJvm = staging.resolve("lib/server/libjvm.so");
            Path clientJvm = staging.resolve("lib/client/libjvm.so");
            if (!Files.isRegularFile(serverJvm) && !Files.isRegularFile(clientJvm)) {
                throw new IOException("Downloaded Java runtime does not contain libjvm.so");
            }

            progress.accept("Finalizing Java runtime…");
            Path awtTarget = staging.resolve("lib").resolve(AWT_LIBRARY);
            Files.createDirectories(awtTarget.getParent());
            copyPackagedNativeLibrary(activity, AWT_LIBRARY, awtTarget);
            if (!Files.isRegularFile(awtTarget) || Files.size(awtTarget) == 0L) {
                throw new IOException("Java graphics bridge was not installed");
            }

            if (jre.exists()) {
                backup = runtimes.toPath().resolve("JRE-backup-" + System.nanoTime());
                moveDirectory(jre.toPath(), backup);
            }
            try {
                moveDirectory(staging, jre.toPath());
                staging = null;
            } catch (IOException failure) {
                if (backup != null && Files.exists(backup) && !jre.exists()) {
                    moveDirectory(backup, jre.toPath());
                    backup = null;
                }
                throw failure;
            }

            if (!isReady(jre)) throw new IOException("Java runtime verification failed");
            installed = true;
        } finally {
            Files.deleteIfExists(archive.toPath());
            if (staging != null && Files.exists(staging)) deleteTree(staging);
            if (installed && backup != null && Files.exists(backup)) deleteTree(backup);
        }
    }

    private static boolean isReady(File jre) {
        return (new File(jre, "lib/server/libjvm.so").isFile()
                || new File(jre, "lib/client/libjvm.so").isFile())
                && new File(jre, "lib/" + AWT_LIBRARY).isFile();
    }

    private static void extractRuntime(File archive, Path staging) throws IOException {
        long extracted = 0L;
        byte[] buffer = new byte[64 * 1024];
        try (ZipFile zip = new ZipFile(archive)) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File target = FileUtil.newFile(staging.toFile(), entry);
                if (entry.isDirectory()) {
                    Files.createDirectories(target.toPath());
                    continue;
                }
                Path parent = target.toPath().getParent();
                if (parent != null) Files.createDirectories(parent);
                try (InputStream input = zip.getInputStream(entry);
                     java.io.OutputStream output = Files.newOutputStream(target.toPath())) {
                    int count;
                    while ((count = input.read(buffer)) >= 0) {
                        if (count == 0) continue;
                        extracted += count;
                        if (extracted > MAX_EXTRACTED_BYTES) {
                            throw new IOException("Java runtime archive is unexpectedly large");
                        }
                        output.write(buffer, 0, count);
                    }
                }
            }
        }
    }

    /**
     * Android can load uncompressed native libraries directly from the APK instead of extracting
     * them to nativeLibraryDir. Try the filesystem first, then copy the library from base/split APKs.
     */
    private static void copyPackagedNativeLibrary(Activity activity, String libraryName, Path target)
            throws IOException {
        ApplicationInfo app = activity.getApplicationInfo();
        if (app.nativeLibraryDir != null) {
            Path extracted = new File(app.nativeLibraryDir, libraryName).toPath();
            if (Files.isRegularFile(extracted)) {
                Files.copy(extracted, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
        }

        List<String> apkPaths = new ArrayList<>();
        if (app.sourceDir != null) apkPaths.add(app.sourceDir);
        if (app.splitSourceDirs != null) {
            for (String split : app.splitSourceDirs) if (split != null) apkPaths.add(split);
        }

        for (String apkPath : apkPaths) {
            try (ZipFile apk = new ZipFile(apkPath)) {
                for (String abi : Build.SUPPORTED_ABIS) {
                    ZipEntry entry = apk.getEntry("lib/" + abi + "/" + libraryName);
                    if (entry == null) continue;
                    try (InputStream input = apk.getInputStream(entry)) {
                        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return;
                }
            }
        }
        throw new IOException(libraryName + " is missing from the installed APK");
    }

    private static void moveDirectory(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Path path : (Iterable<Path>) paths.sorted(java.util.Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(path);
            }
        }
    }
}
