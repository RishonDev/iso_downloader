package iso;

import javax.swing.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class Downloader {
    public static void downloadFile(URL url, String outputFileName) throws IOException {
        try (InputStream in = url.openStream();
             FileOutputStream fos = new FileOutputStream(outputFileName)) {

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
    }
    public static void downloadFileWithProgress(JProgressBar progressBar,
                                                JLabel label,
                                                URL url,
                                                String output) {

        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent","Mozilla/5.0");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.connect();

            long totalSize = conn.getContentLengthLong();

            try (InputStream in = conn.getInputStream();
                 FileOutputStream fos = new FileOutputStream(output, true)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalRead = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    fos.write(buffer,0,bytesRead);
                    totalRead += bytesRead;

                    int percent = totalSize>0 ? (int)((totalRead*100)/totalSize) : 0;

                    long finalTotalRead = totalRead;
                    SwingUtilities.invokeLater(() -> {
                        label.setText("Downloaded "
                                + (finalTotalRead/1024/1024)+"MB / "
                                + (totalSize/1024/1024)+"MB");
                        progressBar.setValue(percent);
                    });
                }
            }

        } catch (Exception e) {

            int choice = JOptionPane.showConfirmDialog(null,
                    "Download failed or timed out.\nKeep partial file?",
                    "Download Error",
                    JOptionPane.YES_NO_OPTION);

            if (choice == JOptionPane.NO_OPTION) {
                new java.io.File(output).delete();
            }
        }
    }
}