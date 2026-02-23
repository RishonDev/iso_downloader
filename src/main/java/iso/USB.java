package iso;

import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class USB {

    public String getDF(String mountPoint) throws Exception {

        ProcessBuilder pb = new ProcessBuilder(
                "findmnt",
                "-n",
                "-o",
                "SOURCE",
                "--target",
                mountPoint
        );

        Process process = pb.start();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
        );

        String device = reader.readLine();

        process.waitFor();

        if (device == null || device.isBlank()) {
            throw new RuntimeException("Could not resolve device for mount point: " + mountPoint);
        }

        return device.trim();
    }
    public String getParentDisk(String partition) throws Exception {

        ProcessBuilder pb = new ProcessBuilder(
                "lsblk",
                "-no",
                "PKNAME",
                partition
        );

        Process process = pb.start();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
        );

        String parent = reader.readLine();

        process.waitFor();

        if (parent == null || parent.isBlank()) {
            throw new RuntimeException("Could not resolve parent disk");
        }

        return "/dev/" + parent.trim();
    }
    public  void flash(
            String isoPath,
            String devicePath,
            long totalBytes,
            JProgressBar progressBar,
            JLabel statusLabel
    ) {

        new Thread(() -> {
            try {

                // Enable progress display
                progressBar.setMinimum(0);
                progressBar.setMaximum(100);
                progressBar.setValue(0);

                statusLabel.setText("Starting flash...");

                ProcessBuilder pb = new ProcessBuilder(
                        "sudo",
                        "dd",
                        "if=" + isoPath,
                        "of=" + devicePath,
                        "bs=4M",
                        "status=progress",
                        "conv=fsync"
                );

                pb.redirectErrorStream(true);

                Process process = pb.start();

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                );

                String line;
                long lastBytes = 0;

                while ((line = reader.readLine()) != null) {

                    // dd prints bytes like:
                    // 12345678 bytes (12 MB, ...) copied ...

                    if (line.contains("bytes")) {
                        try {
                            String bytesStr = line.trim().split(" ")[0];
                            long bytes = Long.parseLong(bytesStr);

                            int percent = (int)((bytes * 100) / totalBytes);

                            SwingUtilities.invokeLater(() -> {
                                progressBar.setValue(percent);
                                statusLabel.setText("Flashing... " + percent + "%");
                            });

                            lastBytes = bytes;

                        } catch (Exception ignored) {}
                    }
                }

                process.waitFor();

                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(100);
                    statusLabel.setText("Flash complete");
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() ->
                        statusLabel.setText("Flash failed: " + e.getMessage())
                );
            }
        }).start();
    }
}
