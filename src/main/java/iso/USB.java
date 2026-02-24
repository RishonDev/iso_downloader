package iso;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicReference;

public class USB {
    private static final Pattern MAC_PARTITION_PATTERN = Pattern.compile("^/dev/r?(disk\\d+)s\\d+$");

    private boolean isMac() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac");
    }

    public String getDF(String mountPoint) throws Exception {
        if (isMac()) {
            ProcessBuilder pb = new ProcessBuilder("df", mountPoint);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                reader.readLine(); // skip header
                String line = reader.readLine();
                process.waitFor();

                if (line == null || line.isBlank()) {
                    throw new RuntimeException("Could not resolve device for mount point: " + mountPoint);
                }

                String[] parts = line.trim().split("\\s+");
                if (parts.length == 0 || parts[0].isBlank()) {
                    throw new RuntimeException("Could not resolve device for mount point: " + mountPoint);
                }
                return parts[0].trim();
            }
        }

        ProcessBuilder pb = new ProcessBuilder(
                "findmnt",
                "-n",
                "-o",
                "SOURCE",
                "--target",
                mountPoint
        );

        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String device = reader.readLine();
        process.waitFor();

        if (device == null || device.isBlank()) {
            throw new RuntimeException("Could not resolve device for mount point: " + mountPoint);
        }
        return device.trim();
    }

    public String getParentDisk(String partition) throws Exception {
        if (isMac()) {
            Matcher m = MAC_PARTITION_PATTERN.matcher(partition.trim());
            if (m.matches()) {
                return "/dev/r" + m.group(1);
            }
            if (partition.startsWith("/dev/disk") || partition.startsWith("/dev/rdisk")) {
                return partition.startsWith("/dev/r") ? partition : partition.replace("/dev/", "/dev/r");
            }
            throw new RuntimeException("Could not resolve parent disk");
        }

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
                char[] sudoPassword = promptForSudoPassword();
                if (sudoPassword == null || sudoPassword.length == 0) {
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Flash cancelled"));
                    return;
                }

                // Enable progress display
                progressBar.setMinimum(0);
                progressBar.setMaximum(100);
                progressBar.setValue(0);

                statusLabel.setText("Starting flash...");

                List<String> cmd = new ArrayList<>();
                cmd.add("sudo");
                cmd.add("-S");
                cmd.add("-p");
                cmd.add("");
                cmd.add("dd");
                cmd.add("if=" + isoPath);
                cmd.add("of=" + devicePath);
                if (isMac()) {
                    cmd.add("bs=4m");
                    cmd.add("oflag=sync");
                } else {
                    cmd.add("bs=4M");
                    cmd.add("status=progress");
                    cmd.add("conv=fsync");
                }

                ProcessBuilder pb = new ProcessBuilder(cmd);

                pb.redirectErrorStream(true);

                Process process = pb.start();
                try (BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                    writer.write(sudoPassword);
                    writer.newLine();
                    writer.flush();
                } finally {
                    Arrays.fill(sudoPassword, '\0');
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream())
                );

                String line;
                boolean hasByteProgress = false;

                while ((line = reader.readLine()) != null) {

                    // dd prints bytes like:
                    // 12345678 bytes (12 MB, ...) copied ...

                    if (line.contains("bytes")) {
                        try {
                            String bytesStr = line.trim().split(" ")[0];
                            long bytes = Long.parseLong(bytesStr);
                            hasByteProgress = true;

                            int percent = (int)((bytes * 100) / totalBytes);

                            SwingUtilities.invokeLater(() -> {
                                progressBar.setValue(percent);
                                statusLabel.setText("Flashing... " + percent + "%");
                            });

                        } catch (Exception ignored) {}
                    }
                }

                process.waitFor();
                int exit = process.exitValue();

                if (exit != 0) {
                    SwingUtilities.invokeLater(() ->
                            statusLabel.setText("Flash failed (exit " + exit + ")"));
                    return;
                }

                if (!hasByteProgress) {
                    SwingUtilities.invokeLater(() ->
                            statusLabel.setText("Finalizing flash..."));
                }

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

    private char[] promptForSudoPassword() throws Exception {
        AtomicReference<char[]> passwordRef = new AtomicReference<>();
        Runnable showPrompt = () -> {
            JPasswordField passwordField = new JPasswordField(20);
            int option = JOptionPane.showConfirmDialog(
                    null,
                    passwordField,
                    "Enter sudo password to flash USB",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
            );
            if (option == JOptionPane.OK_OPTION) {
                passwordRef.set(passwordField.getPassword());
            } else {
                passwordRef.set(null);
            }
        };

        if (SwingUtilities.isEventDispatchThread()) {
            showPrompt.run();
        } else {
            SwingUtilities.invokeAndWait(showPrompt);
        }

        return passwordRef.get();
    }
}
