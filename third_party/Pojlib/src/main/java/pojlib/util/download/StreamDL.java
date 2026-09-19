package pojlib.util.download;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;

public class StreamDL extends InputStream {
    private final InputStream in;
    private int count;
    private final Collection<StreamListener> listeners = new ArrayList<>();

    public StreamDL(InputStream in, long totalBytes) {
        this.in = in;
        DownloadManager.addTotalBytes(totalBytes);
    }

    @Override
    public int read() throws IOException {
        int b = in.read();
        byteReceived(b);
        return b;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int read = in.read(buffer, offset, length);
        if (read > 0) {
            if (listeners.isEmpty()) {
                count += read;
                DownloadManager.addBytes(read);
            } else {
                for (int i = 0; i < read; i++) byteReceived(buffer[offset + i] & 0xff);
            }
        }
        return read;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    public void addListener(StreamListener listener) {
        listeners.add(listener);
    }

    private void byteReceived(int b) {
        if (b != -1) {
            count++;
            DownloadManager.addBytes(1);
        }

        for (StreamListener l : listeners) {
            l.byteReceived(b, count);
        }
    }
}

