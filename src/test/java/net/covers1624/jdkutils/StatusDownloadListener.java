package net.covers1624.jdkutils;

import net.covers1624.quack.net.download.DownloadListener;

/**
 * Created by covers1624 on 9/11/21.
 */
public class StatusDownloadListener implements DownloadListener {

    int lastLen = 0;
    private long expected;

    @Override
    public void connecting() {
    }

    @Override
    public void start(long expectedLen) {
        expected = expectedLen;
    }

    @Override
    public void update(long processedBytes) {
        String line = "Downloading... (" + getStatus(processedBytes, expected) + ")";
        lastLen = line.length();
        System.out.print("\r" + line);
    }

    @Override
    public void finish(long totalProcessed) {
        System.out.print("\r" + repeat(' ', lastLen) + "\r");
    }

    private String getStatus(long complete, long total) {
        if (total >= 1024) return toKB(complete) + "/" + toKB(total) + " KB";
        if (total >= 0) return complete + "/" + total + " B";
        if (complete >= 1024) return toKB(complete) + " KB";
        return complete + " B";
    }

    protected long toKB(long bytes) {
        return (bytes + 1023) / 1024;
    }

    private static String repeat(char ch, int num) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < num; i++) {
            builder.append(ch);
        }
        return builder.toString();
    }
}
