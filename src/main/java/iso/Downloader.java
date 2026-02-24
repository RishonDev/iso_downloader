package iso;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Downloader {
    private static final int HTTP_RANGE_NOT_SATISFIABLE = 416;
    private static final int BUFFER_SIZE = 256 * 1024;
    private static final long UI_UPDATE_INTERVAL_MS = 150L;
    private static final Pattern CONTENT_RANGE_TOTAL = Pattern.compile(".*/(\\d+)$");
    private volatile boolean isCancelled = false;

    public void setCancelled(boolean b) {
        isCancelled = b;
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public static void download(URL url, String outputFileName) throws IOException {
        try (InputStream in = new BufferedInputStream(url.openStream(), BUFFER_SIZE);
             FileOutputStream fos = new FileOutputStream(outputFileName);
             BufferedOutputStream out = new BufferedOutputStream(fos, BUFFER_SIZE)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }

    public void downloadFile(JProgressBar progressBar,
                             JLabel label,
                             URL url,
                             String output) {
        downloadFile(progressBar, label, url, output, 0L);
    }

    public void downloadFile(JProgressBar progressBar,
                             JLabel label,
                             URL url,
                             String output,
                             long partOffset) {
        long safeOffset = Math.max(0L, partOffset);

        try {
            HttpURLConnection conn = openConnection(url, safeOffset, safeOffset > 0);
            int code = conn.getResponseCode();
            if (code >= 400 && code != HTTP_RANGE_NOT_SATISFIABLE) {
                throw new IOException("HTTP " + code + " while downloading");
            }

            long totalSize;
            if (safeOffset > 0) {
                if (code != HttpURLConnection.HTTP_PARTIAL) {
                    conn.disconnect();
                    throw new IOException("Server does not support resume for this part");
                }
                long remainingSize = conn.getContentLengthLong();
                totalSize = remainingSize > 0 ? safeOffset + remainingSize : -1L;
            } else {
                if (code == HttpURLConnection.HTTP_PARTIAL) {
                    long remainingSize = conn.getContentLengthLong();
                    totalSize = remainingSize > 0 ? remainingSize : -1L;
                } else {
                    long fullSize = conn.getContentLengthLong();
                    totalSize = fullSize > 0 ? fullSize : -1L;
                }
            }

            try (InputStream in = new BufferedInputStream(conn.getInputStream(), BUFFER_SIZE);
                 FileOutputStream fos = new FileOutputStream(output, true);
                 BufferedOutputStream out = new BufferedOutputStream(fos, BUFFER_SIZE)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long totalRead = safeOffset;
                long lastUiUpdate = 0;
                int lastPercent = -1;

                while ((bytesRead = in.read(buffer)) != -1) {
                    if (isCancelled) {
                        break;
                    }

                    out.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    int percent = totalSize > 0 ? (int) ((totalRead * 100) / totalSize) : 0;
                    long now = System.currentTimeMillis();
                    boolean shouldUpdate = percent != lastPercent
                            && (now - lastUiUpdate >= UI_UPDATE_INTERVAL_MS || percent == 100);
                    if (!shouldUpdate) {
                        continue;
                    }

                    lastUiUpdate = now;
                    lastPercent = percent;
                    long finalTotalRead = totalRead;

                    SwingUtilities.invokeLater(() -> {
                        if (totalSize > 0) {
                            label.setText("Downloaded "
                                    + (finalTotalRead / 1024 / 1024) + "MB / "
                                    + (totalSize / 1024 / 1024) + "MB");
                        } else {
                            label.setText("Downloaded " + (finalTotalRead / 1024 / 1024) + "MB");
                        }
                        progressBar.setValue(percent);
                    });
                }
            }
            conn.disconnect();

        } catch (Exception e) {
            if (isCancelled) {
                return;
            }

            int choice = JOptionPane.showConfirmDialog(null,
                    "Download failed or timed out.\nKeep partial file?",
                    "Download Error",
                    JOptionPane.YES_NO_OPTION);

            if (choice == JOptionPane.NO_OPTION) {
                var f = new java.io.File(output);
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    private HttpURLConnection openConnection(URL url, long existingSize, boolean useRange) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Accept-Encoding", "identity");
        conn.setRequestProperty("Connection", "keep-alive");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setUseCaches(false);
        conn.setInstanceFollowRedirects(true);
        if (useRange && existingSize > 0) {
            conn.setRequestProperty("Range", "bytes=" + existingSize + "-");
        }
        conn.connect();
        return conn;
    }

    public long getRemoteFileSize(URL url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setUseCaches(false);
            conn.connect();
            long size = conn.getContentLengthLong();
            if (size > 0) {
                return size;
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Range", "bytes=0-0");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setUseCaches(false);
            conn.connect();
            String contentRange = conn.getHeaderField("Content-Range");
            if (contentRange != null) {
                Matcher matcher = CONTENT_RANGE_TOTAL.matcher(contentRange.trim());
                if (matcher.find()) {
                    return Long.parseLong(matcher.group(1));
                }
            }
            return conn.getContentLengthLong();
        } catch (Exception ignored) {
            return -1L;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
