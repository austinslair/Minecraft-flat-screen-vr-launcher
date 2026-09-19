package pojlib.account;

import com.microsoft.aad.msal4j.ITokenCacheAccessAspect;
import com.microsoft.aad.msal4j.ITokenCacheAccessContext;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

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
        data = context.tokenCache().serialize();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(cache, false))) {
            writer.write(data == null ? "" : data);
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException("Unable to persist Microsoft token cache", e);
        }
    }
}
