package iso;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URL;
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
    private static final String WINDOWS_DD_PRIMARY_URL = "https://www.chrysocome.net/downloads/ddrelease64.exe";
    private static final String WINDOWS_DD_FALLBACK_URL = "https://frippery.org/files/busybox/busybox64.exe";
    private static final int COPY_BUFFER_SIZE = 64 * 1024;

    private boolean isMac() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac");
    }
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    public String getDF(String mountPoint) throws Exception {
        if (isMac()) {
            ProcessBuilder pb = new ProcessBuilder("df", "-P", mountPoint);
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
                return parts[0].trim().replaceFirst("^/dev/r", "/dev/");
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

    public String getParentDirectory(String partition) throws Exception {
        return getParentDisk(partition);
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
                char[] sudoPassword = null;
                if (!isWindows()) {
                    sudoPassword = promptForSudoPassword();
                    if (sudoPassword == null || sudoPassword.length == 0) {
                        SwingUtilities.invokeLater(() -> statusLabel.setText("Flash cancelled"));
                        return;
                    }
                }

                // Enable progress display
                progressBar.setMinimum(0);
                progressBar.setMaximum(100);
                progressBar.setValue(0);

                statusLabel.setText("Starting flash...");

                List<String> cmd = new ArrayList<>();
                if (isWindows()) {
                    SwingUtilities.invokeLater(() ->
                            statusLabel.setText("Preparing flasher tool (may download once)..."));
                    String ddExe = resolveWindowsDdExecutable(statusLabel);
                    if (ddExe.toLowerCase(Locale.ROOT).contains("busybox")) {
                        cmd.add(ddExe);
                        cmd.add("dd");
                    } else {
                        cmd.add(ddExe);
                    }
                    cmd.add("if=" + isoPath);
                    cmd.add("of=" + devicePath);
                    cmd.add("bs=4M");
                    cmd.add("conv=fsync");
                } else {
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
                }

                ProcessBuilder pb = new ProcessBuilder(cmd);

                pb.redirectErrorStream(true);

                Process process = pb.start();
                if (!isWindows()) {
                    try (BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                        writer.write(sudoPassword);
                        writer.newLine();
                        writer.flush();
                    } finally {
                        Arrays.fill(sudoPassword, '\0');
                    }
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

    private String resolveWindowsDdExecutable(JLabel statusLabel) throws Exception {
        String env = System.getenv("ISO_DD_EXE");
        if (env != null && !env.isBlank() && new File(env).exists()) {
            return env;
        }

        String[] pathCandidates = {"dd.exe", "busybox.exe", "busybox64.exe"};
        for (String candidate : pathCandidates) {
            if (isCommandAvailable(candidate)) {
                return candidate;
            }
        }

        File isoDir = new File(System.getProperty("user.home"), ".iso");
        if (!isoDir.exists()) {
            isoDir.mkdirs();
        }

        File localDd = new File(isoDir, "ddrelease64.exe");
        if (!localDd.exists()) {
            try {
                SwingUtilities.invokeLater(() ->
                        statusLabel.setText("Downloading Windows flasher tool..."));
                downloadWindowsDd(localDd, WINDOWS_DD_PRIMARY_URL);
            } catch (Exception primaryError) {
                // Fallback: busybox dd-compatible binary.
                File fallbackBusybox = new File(isoDir, "busybox64.exe");
                if (!fallbackBusybox.exists()) {
                    SwingUtilities.invokeLater(() ->
                            statusLabel.setText("Primary tool failed, downloading fallback..."));
                    downloadWindowsDd(fallbackBusybox, WINDOWS_DD_FALLBACK_URL);
                }
                return fallbackBusybox.getAbsolutePath();
            }
        }

        if (!localDd.exists()) {
            throw new RuntimeException("Could not find/download Windows dd tool");
        }

        return localDd.getAbsolutePath();
    }

    private boolean isCommandAvailable(String command) {
        try {
            Process process = new ProcessBuilder(command, "--help").start();
            process.waitFor();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void downloadWindowsDd(File destination, String sourceUrl) throws Exception {
        URL url = new URI(sourceUrl).toURL();
        try (BufferedInputStream in = new BufferedInputStream(url.openStream(), COPY_BUFFER_SIZE);
             FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to download Windows dd tool", e);
        }
    }
}
