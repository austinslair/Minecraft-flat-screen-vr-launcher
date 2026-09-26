package pojlib.account;

import com.microsoft.aad.msal4j.ITokenCacheAccessAspect;
import com.microsoft.aad.msal4j.ITokenCacheAccessContext;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class TokenPersistence implements ITokenCacheAccessAspect {
    private String data;
    private final File cache;

    TokenPersistence(String data, File cache) {
        this.data = data == null ? "" : data;
        this.cache = cache;
    }

    @Override
    public synchronized void beforeCacheAccess(ITokenCacheAccessContext context) {
        if (!data.isEmpty()) {
            context.tokenCache().deserialize(data);
        }
    }

    @Override
    public synchronized void afterCacheAccess(ITokenCacheAccessContext context) {
        String serialized = context.tokenCache().serialize();
        // A transient empty cache must not erase the saved refresh tokens.
        // Account removal is handled separately from this cache callback.
        if (serialized == null || serialized.isEmpty()) {
            return;
        }
        data = serialized;
        writeAtomically(data);
    }

    private void writeAtomically(String value) {
        File parent = cache.getParentFile();
        if (parent == null) {
            throw new IllegalStateException("Microsoft token cache has no parent directory");
        }

        File temporary = new File(parent, cache.getName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temporary.toPath(), StandardCharsets.UTF_8)) {
            writer.write(value);
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException("Unable to write Microsoft token cache", e);
        }

        try {
            try {
                Files.move(
                        temporary.toPath(),
                        cache.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(
                        temporary.toPath(),
                        cache.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to replace Microsoft token cache", e);
        } finally {
            if (temporary.exists() && !temporary.delete()) {
                temporary.deleteOnExit();
            }
        }
    }
}
