package iso;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class Downloader {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final long UI_UPDATE_INTERVAL_MS = 150L;
    private volatile boolean isCancelled = false;

    public void setCancelled(boolean b) {
        isCancelled = b;
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public static void download(URL url, String outputFileName) throws IOException {
        try (InputStream in = url.openStream();
             FileOutputStream fos = new FileOutputStream(outputFileName)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
    }

    public void downloadFile(JProgressBar progressBar,
                             JLabel label,
                             URL url,
                             String output) {
        HttpURLConnection conn = null;

        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setUseCaches(false);
            conn.connect();

            int code = conn.getResponseCode();
            if (code >= 400) {
                throw new IOException("HTTP " + code + " while downloading");
            }

            long totalSize = conn.getContentLengthLong();

            try (InputStream in = conn.getInputStream();
                 FileOutputStream fos = new FileOutputStream(output, true)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long totalRead = 0;
                long lastUiUpdate = 0;
                int lastPercent = -1;

                while ((bytesRead = in.read(buffer)) != -1) {
                    if (isCancelled) {
                        break;
                    }

                    fos.write(buffer, 0, bytesRead);
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
                        label.setText("Downloaded "
                                + (finalTotalRead / 1024 / 1024) + "MB / "
                                + (totalSize / 1024 / 1024) + "MB");
                        progressBar.setValue(percent);
                    });
                }
            }

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
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
