package pojlib.util;

import androidx.annotation.Keep;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Singleton class made to log on one file
 * The singleton part can be removed but will require more implementation from the end-dev
 */
@Keep
public class Logger {

    /* Instance variables */
    private final File mLogFile;
    private PrintStream mLogStream;
    private WeakReference<eventLogListener> mLogListenerWeakReference = null;

    /* No public construction */
    private Logger(){
        this("latestlog.txt");
    }

    private Logger(String fileName){
        mLogFile = new File(Constants.USER_HOME, fileName);
        File parent = mLogFile.getParentFile();
        if (parent != null) parent.mkdirs();
        if ("latestlog.txt".equals(fileName) && mLogFile.isFile() && mLogFile.length() > 0L) {
            File previous = new File(Constants.USER_HOME, "previouslog.txt");
            // Only replace the saved previous log when the last process actually entered
            // Minecraft. Launcher-only restarts (including an APK update after a crash)
            // must not overwrite the crash report the user still needs to copy.
            if (fileContains(mLogFile, "VoxyQuest launch: game activity created")) {
                try {
                    Files.move(
                            mLogFile.toPath(), previous.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                    );
                } catch (IOException moveFailure) {
                    try {
                        Files.copy(
                                mLogFile.toPath(), previous.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                        );
                    } catch (IOException copyFailure) {
                        copyFailure.printStackTrace();
                    }
                    mLogFile.delete();
                }
            } else {
                mLogFile.delete();
            }
        } else {
            mLogFile.delete();
        }
        try {
            mLogFile.createNewFile();
            mLogStream = new PrintStream(mLogFile.getAbsolutePath());
        }catch (IOException e){e.printStackTrace();}

    }

    private static boolean fileContains(File file, String marker) {
        try {
            return file.isFile() && new String(
                    Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8
            ).contains(marker);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static final class SLoggerSingletonHolder {
        static final Logger sLoggerSingleton = new Logger();
    }

    public static Logger getInstance(){
        return SLoggerSingletonHolder.sLoggerSingleton;
    }


    /** Print the text to the log file if not censored */
    public void appendToLog(String text){
        if(shouldCensorLog(text)) return;
        // The launcher crash dialog intentionally whitelists "VoxyQuest launch:" lines.
        // Promote the detailed JVM diagnostics into that namespace so they are included
        // in the copyable report instead of being silently filtered out.
        if (text.startsWith("VoxyQuest JVM:")) {
            text = "VoxyQuest launch: JVM:" + text.substring("VoxyQuest JVM:".length());
        }
        appendToLogUnchecked(text);
    }

    /** Print the text to the log file, no china censoring there */
    public synchronized void appendToLogUnchecked(String text){
        if (mLogStream == null) return;
        mLogStream.println(text);
        // Flush every launch breadcrumb so a native process crash does not erase the clue.
        mLogStream.flush();
        notifyLogListener(text);
    }

    /** Reset the log file, effectively erasing any previous logs */
    public synchronized void reset(){
        try{
            if (mLogStream != null) mLogStream.close();
            mLogFile.delete();
            mLogFile.createNewFile();
            mLogStream = new PrintStream(mLogFile.getAbsolutePath());
        }catch (IOException e){ e.printStackTrace();}
    }

    /** Disables the printing */
    public synchronized void shutdown(){
        if (mLogStream != null) mLogStream.close();
    }

    /**
     * Perform various checks to see if the log is safe to print
     * Subclasses may want to override this behavior
     * @param text The text to check
     * @return Whether the log should be censored
     */
    private static boolean shouldCensorLog(String text){
        return text.contains("Session ID is");
    }

    /** Small listener for anything listening to the log */
    public interface eventLogListener {
        void onEventLogged(String text);
    }

    /** Link a log listener to the logger */
    public void setLogListener(eventLogListener logListener){
        this.mLogListenerWeakReference = new WeakReference<>(logListener);
    }

    /** Notifies the event listener, if it exists */
    private void notifyLogListener(String text){
        if(mLogListenerWeakReference == null) return;
        eventLogListener logListener = mLogListenerWeakReference.get();
        if(logListener == null){
            mLogListenerWeakReference = null;
            return;
        }
        logListener.onEventLogged(text);
    }
}
