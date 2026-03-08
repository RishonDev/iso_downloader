package iso;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class USB {
    private static final Pattern MAC_PARTITION_PATTERN = Pattern.compile("^/dev/r?(disk\\d+)s\\d+$");
    private static final Pattern DD_BYTES_PATTERN = Pattern.compile("^(\\d+)\\s+bytes\\b.*");
    private static final String WINDOWS_DD_PRIMARY_URL = "https://github.com/RishonDev/iso_downloader/raw/refs/heads/master/dd.zip";
    private static final String WINDOWS_DD_FALLBACK_URL = "https://frippery.org/files/busybox/busybox64.exe";
    private static final int COPY_BUFFER_SIZE = 64 * 1024;
    private static final int OUTPUT_TAIL_LINES = 8;
    private static final long FLASH_UI_UPDATE_INTERVAL_MS = 120L;

    private volatile long lastFlashUiUpdateMs = 0L;
    private volatile int lastFlashPercent = -1;

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
                reader.readLine();
                String line = reader.readLine();
                process.waitFor();
                if (line == null || line.isBlank())
                    throw new RuntimeException("Could not resolve device for mount point: " + mountPoint);
                String[] parts = line.trim().split("\\s+");
                if (parts.length == 0 || parts[0].isBlank())
                    throw new RuntimeException("Could not resolve device for mount point: " + mountPoint);
                return parts[0].trim().replaceFirst("^/dev/r", "/dev/");
            }
        }

        ProcessBuilder pb = new ProcessBuilder("findmnt", "-n", "-o", "SOURCE", "--target", mountPoint);

        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String device = reader.readLine();
        process.waitFor();

        if (device == null || device.isBlank())
            throw new RuntimeException("Could not resolve device for mount point: " + mountPoint);
        return device.trim();
    }

    public String getParentDisk(String partition) throws Exception {
        if (isMac()) {
            String trimmed = partition.trim();
            Matcher m = MAC_PARTITION_PATTERN.matcher(trimmed);
            if (m.matches()) return "/dev/r" + m.group(1);
            Matcher extended = Pattern.compile("^/dev/r?(disk\\d+)(?:s\\d+.*)?$").matcher(trimmed);
            if (extended.matches()) return "/dev/r" + extended.group(1);
            if (trimmed.startsWith("/dev/disk") || trimmed.startsWith("/dev/rdisk"))
                return trimmed.startsWith("/dev/r") ? trimmed : trimmed.replace("/dev/", "/dev/r");
            throw new RuntimeException("Could not resolve parent disk");
        }

        ProcessBuilder pb = new ProcessBuilder("lsblk", "-no", "PKNAME", partition);
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String parent = reader.readLine();
        process.waitFor();
        if (parent == null || parent.isBlank()) throw new RuntimeException("Could not resolve parent disk");
        return "/dev/" + parent.trim();
    }

    public String getParentDirectory(String partition) throws Exception {
        return getParentDisk(partition);
    }

    public boolean isProtectedSystemDevice(String devicePath) {
        if (devicePath == null || devicePath.isBlank()) return false;
        String candidate = normalizeDevicePath(devicePath);
        if (!candidate.startsWith("/dev/")) return false;

        String[] protectedMounts = {"/", "/boot", "/boot/efi", "/System/Volumes/Data"};
        for (String mountPoint : protectedMounts) {
            try {
                String source = normalizeDevicePath(getDF(mountPoint));
                if (source == null || source.isBlank()) continue;
                if (sameDeviceOrDisk(candidate, source)) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private boolean sameDeviceOrDisk(String a, String b) {
        if (a.equals(b)) return true;
        String aParent = safeParent(a);
        String bParent = safeParent(b);
        if (aParent != null && aParent.equals(b)) return true;
        if (bParent != null && bParent.equals(a)) return true;
        return aParent != null && bParent != null && aParent.equals(bParent);
    }

    private String safeParent(String device) {
        try {return normalizeDevicePath(getParentDirectory(device));}
        catch (Exception ignored) {return null;}
    }

    private String normalizeDevicePath(String device) {
        String normalized = device.trim();
        if (normalized.startsWith("/dev/rdisk"))
            normalized = normalized.replace("/dev/rdisk", "/dev/disk");
        else if (normalized.startsWith("/dev/r"))
            normalized = normalized.replaceFirst("^/dev/r", "/dev/");
        return normalized;
    }

    public void flash(String isoPath, String devicePath, long totalBytes, ProgressBar progressBar, Label statusLabel, boolean verifyUsb,
            String expectedIsoSha256) {

        new Thread(() -> {
            char[] sudoPassword = null;
            try {
                if (!isWindows()) {
                    sudoPassword = promptForSudoPassword();
                    if (sudoPassword == null || sudoPassword.length == 0) {
                        Platform.runLater(() -> statusLabel.setText("Flash cancelled"));
                        return;
                    }
                }

                runOnFxAndWait(() -> {
                    lastFlashUiUpdateMs = 0L;
                    lastFlashPercent = -1;
                    progressBar.setProgress(0);
                    progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                    statusLabel.setText("Starting flash...");
                });

                List<String> cmd = new ArrayList<>();
                if (isWindows()) {
                    Platform.runLater(() ->
                            statusLabel.setText("Preparing flasher tool (may download once)..."));
                    String ddExe = resolveWindowsDdExecutable(statusLabel);
                    if (ddExe.toLowerCase(Locale.ROOT).contains("busybox")) {
                        cmd.add(ddExe);
                        cmd.add("dd");
                    } else
                        cmd.add(ddExe);

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

                FlashCommandResult result = runFlashCommand(cmd, sudoPassword, totalBytes, progressBar, statusLabel);
                if (!isWindows() && !isMac()
                        && result.exit != 0
                        && containsStatusProgressError(result.outputTail)) {
                    List<String> fallbackCmd = new ArrayList<>(cmd);
                    fallbackCmd.removeIf(arg -> "status=progress".equals(arg));
                    Platform.runLater(() -> statusLabel.setText("Retrying flash without progress flag..."));
                    result = runFlashCommand(fallbackCmd, sudoPassword, totalBytes, progressBar, statusLabel);
                }

                if (result.exit != 0) {
                    String err = result.outputTail.isBlank() ? "Flash failed (exit " + result.exit + ")"
                            : "Flash failed: " + result.outputTail;
                    Platform.runLater(() -> statusLabel.setText(err));
                    return;
                }

                if (verifyUsb) {
                    Platform.runLater(() -> statusLabel.setText("Verifying USB..."));
                    String expectedSha = expectedIsoSha256;
                    if (expectedSha == null || expectedSha.isBlank()) expectedSha = sha256File(isoPath);
                    String usbSha = computeDeviceSha256(devicePath, totalBytes, sudoPassword);
                    if (usbSha == null || usbSha.isBlank()) {
                        Platform.runLater(() -> statusLabel.setText("USB verify failed: no checksum output"));
                        return;
                    }
                    if (!usbSha.equalsIgnoreCase(expectedSha)) {
                        Platform.runLater(() -> statusLabel.setText("USB verify failed: checksum mismatch"));
                        return;
                    }
                }

                if (!result.hasByteProgress) {
                    Platform.runLater(() ->
                            statusLabel.setText("Finalizing flash..."));
                }

                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    statusLabel.setText("Flash complete");
                });

            } catch (Exception e) {
                Platform.runLater(() ->
                        {
                            progressBar.setProgress(0);
                            statusLabel.setText("Flash failed: " + e.getMessage());
                        }
                );
            } finally {
                if (sudoPassword != null) Arrays.fill(sudoPassword, '\0');
            }
        }).start();
    }

    private char[] promptForSudoPassword() {
        AtomicReference<char[]> passwordRef = new AtomicReference<>();
        runOnFxAndWait(() -> {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Sudo Authentication");
            dialog.setHeaderText("Enter sudo password to flash USB");
            PasswordField passwordField = new PasswordField();
            passwordField.setPromptText("Password");
            dialog.getDialogPane().setContent(passwordField);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            Optional<ButtonType> option = dialog.showAndWait();
            if (option.isPresent() && option.get() == ButtonType.OK)
                passwordRef.set(passwordField.getText().toCharArray());
            else
                passwordRef.set(null);
        });
        return passwordRef.get();
    }

    private synchronized boolean updateProgressFromLine(String line, long totalBytes, ProgressBar progressBar, Label statusLabel) {
        Matcher m = DD_BYTES_PATTERN.matcher(line);
        if (!m.matches() || totalBytes <= 0) return false;
        try {
            long bytes = Long.parseLong(m.group(1));
            int percent = (int) Math.max(0, Math.min(100, (bytes * 100) / totalBytes));
            long now = System.currentTimeMillis();
            boolean shouldUpdate = percent != lastFlashPercent
                    && (now - lastFlashUiUpdateMs >= FLASH_UI_UPDATE_INTERVAL_MS || percent == 100);
            if (!shouldUpdate) return true;
            lastFlashUiUpdateMs = now;
            lastFlashPercent = percent;
            Platform.runLater(() -> {
                progressBar.setProgress(percent / 100.0);
                statusLabel.setText("Flashing... " + percent + "%");
            });
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private FlashCommandResult runFlashCommand(List<String> cmd, char[] sudoPassword, long totalBytes, ProgressBar progressBar, Label statusLabel) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        if (!isWindows()) {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write(sudoPassword);
                writer.newLine();
                writer.flush();
            }
        }

        String line;
        boolean hasByteProgress = false;
        StringBuilder chunk = new StringBuilder();
        Deque<String> outputTail = new ArrayDeque<>();

        try (InputStreamReader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
            int c;
            while ((c = reader.read()) != -1) {
                if (c == '\r' || c == '\n') {
                    line = chunk.toString().trim();
                    if (!line.isEmpty()) {
                        if (updateProgressFromLine(line, totalBytes, progressBar, statusLabel))
                            hasByteProgress = true;
                        outputTail.addLast(line);
                        while (outputTail.size() > OUTPUT_TAIL_LINES) {
                            outputTail.removeFirst();
                        }
                    }
                    chunk.setLength(0);
                } else {
                    chunk.append((char) c);
                }
            }
        }

        line = chunk.toString().trim();
        if (!line.isEmpty()) {
            if (updateProgressFromLine(line, totalBytes, progressBar, statusLabel))
                hasByteProgress = true;
            outputTail.addLast(line);
            while (outputTail.size() > OUTPUT_TAIL_LINES) {
                outputTail.removeFirst();
            }
        }

        process.waitFor();
        int exit = process.exitValue();
        return new FlashCommandResult(exit, hasByteProgress, String.join(" | ", outputTail));
    }

    private boolean containsStatusProgressError(String output) {
        String lower = output.toLowerCase(Locale.ROOT);
        return lower.contains("status=progress")
                || lower.contains("invalid status flag")
                || lower.contains("unrecognized operand")
                || lower.contains("invalid argument");
    }

    private String computeDeviceSha256(String devicePath, long totalBytes, char[] sudoPassword) throws Exception {
        if (isWindows()){
            System.out.println("Verifying ISO is not supported on Windows, skipping.");
            return "";
        }
        String hashCmd;
        if (isMac()) {
            hashCmd = "dd if=" + shellQuote(devicePath) + " bs=4m 2>/dev/null | head -c " + totalBytes
                    + " | shasum -a 256";
        } else {
            hashCmd = "dd if=" + shellQuote(devicePath) + " bs=4M status=none 2>/dev/null | head -c " + totalBytes
                    + " | sha256sum";
        }
        ProcessBuilder pb = new ProcessBuilder("sudo", "-S", "-p", "", "sh", "-c", hashCmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write(sudoPassword);
            writer.newLine();
            writer.flush();
        }

        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.readLine();
        }
        process.waitFor();
        if (process.exitValue() != 0 || output == null) throw new RuntimeException("Could not verify USB contents");
        String[] parts = output.trim().split("\\s+");
        return parts.length == 0 ? null : parts[0].trim().toLowerCase(Locale.ROOT);
    }

    private String sha256File(String path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (BufferedInputStream in = new BufferedInputStream(new java.io.FileInputStream(path), COPY_BUFFER_SIZE)) {
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int n;
            while ((n = in.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static final class FlashCommandResult {
        private final int exit;
        private final boolean hasByteProgress;
        private final String outputTail;

        private FlashCommandResult(int exit, boolean hasByteProgress, String outputTail) {
            this.exit = exit;
            this.hasByteProgress = hasByteProgress;
            this.outputTail = outputTail;
        }
    }

    private String resolveWindowsDdExecutable(Label statusLabel) throws Exception {
        String env = System.getenv("ISO_DD_EXE");
        if (env != null && !env.isBlank() && new File(env).exists()) return env;

        String[] pathCandidates = {"dd.exe", "busybox.exe", "busybox64.exe"};
        for (String candidate : pathCandidates) {
            if (isCommandAvailable(candidate)) return candidate;
        }

        File isoDir = new File(System.getProperty("user.home"), ".iso");
        if (!isoDir.exists()) {
            boolean created = isoDir.mkdirs();
            if (!created && !isoDir.exists())
                throw new RuntimeException("Could not create directory: " + isoDir.getAbsolutePath());
        }

        File localDd = new File(isoDir, "dd.exe");
        if (!localDd.exists()) {
            try {
                Platform.runLater(() ->
                        statusLabel.setText("Downloading Windows flasher tool..."));
                downloadWindowsDd(localDd, WINDOWS_DD_PRIMARY_URL);
            } catch (Exception primaryError) {
                File fallbackBusybox = new File(isoDir, "busybox64.exe");
                if (!fallbackBusybox.exists()) {
                    Platform.runLater(() ->
                            statusLabel.setText("Primary tool failed, downloading fallback..."));
                    downloadWindowsDd(fallbackBusybox, WINDOWS_DD_FALLBACK_URL);
                }
                return fallbackBusybox.getAbsolutePath();
            }
        }

        if (!localDd.exists()) throw new RuntimeException("Could not find/download Windows dd tool");

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
        if (sourceUrl.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            downloadWindowsDdFromZip(destination, sourceUrl);
            return;
        }

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

    private void downloadWindowsDdFromZip(File destination, String sourceUrl) throws Exception {
        URL url = new URI(sourceUrl).toURL();
        boolean extracted = false;

        try (BufferedInputStream in = new BufferedInputStream(url.openStream(), COPY_BUFFER_SIZE);
             ZipInputStream zipIn = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String entryName = new File(entry.getName()).getName().toLowerCase(Locale.ROOT);
                if (!entryName.endsWith(".exe")) continue;

                try (FileOutputStream out = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[COPY_BUFFER_SIZE];
                    int n;
                    while ((n = zipIn.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                    }
                }
                extracted = true;
                break;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to download Windows dd tool", e);
        }
        if (!extracted) throw new RuntimeException("Windows dd zip did not contain an .exe binary");
    }

    private static void runOnFxAndWait(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {action.run();}
            finally {latch.countDown();}
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Operation interrupted", ButtonType.OK);
            Platform.runLater(alert::showAndWait);
        }
    }
}
