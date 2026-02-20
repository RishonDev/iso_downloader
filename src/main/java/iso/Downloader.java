package iso;

import iso.FileIO;

import javax.swing.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class Downloader {
    public static void downloadFile(URL url, String outputFileName) throws IOException {
        try (InputStream in = url.openStream(); ReadableByteChannel rbc = Channels.newChannel(in); FileOutputStream fos = new FileOutputStream(outputFileName)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }
    }

    public static void downloadFileWithProgress(JProgressBar progressBar,
                                                JLabel label,
                                                URL url,
                                                String output) {

        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            conn.connect();

            int responseCode = conn.getResponseCode();

            // Handle redirect manually (important for GitHub)
            if (responseCode / 100 == 3) {
                String newUrl = conn.getHeaderField("Location");
                conn.disconnect();

                conn = (HttpURLConnection) new URL(newUrl).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.connect();
            }

            long totalSize = conn.getContentLengthLong();

            progressBar.setMinimum(0);
            progressBar.setMaximum(100);

            try (InputStream in = conn.getInputStream();
                 FileOutputStream fos = new FileOutputStream(output)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalRead = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    long finalTotalRead = totalRead;

                    int percent = totalSize > 0
                            ? (int) ((finalTotalRead * 100) / totalSize)
                            : 0;

                    SwingUtilities.invokeLater(() -> {
                        label.setText("Downloaded "
                                + (finalTotalRead / 1024 / 1024) + "MB / "
                                + (totalSize / 1024 / 1024) + "MB");

                        progressBar.setValue(percent);
                    });
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
