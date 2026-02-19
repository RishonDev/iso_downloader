package iso;

import iso.FileIO;

import javax.swing.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class Downloader {
    public static void downloadFile(URL url, String outputFileName) throws IOException {
        try (InputStream in = url.openStream(); ReadableByteChannel rbc = Channels.newChannel(in); FileOutputStream fos = new FileOutputStream(outputFileName)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }
    }

    public static void downloadFileWithProgress(JProgressBar progressBar, JLabel label, URL url, String output) {
        try {
            URLConnection conn = url.openConnection();
            long totalSize = conn.getContentLengthLong();

            // Configure progress bar
            progressBar.setMinimum(0);
            progressBar.setMaximum(100);

            try (InputStream in = conn.getInputStream();
                 FileOutputStream fos = new FileOutputStream(output, true)) {

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
