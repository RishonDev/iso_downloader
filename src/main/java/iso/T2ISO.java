package iso;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.AWTError;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class T2ISO {
    private static final int HASH_BUFFER_SIZE = 64 * 1024;

    private final Downloader downloader = new Downloader();
    private final String home = System.getProperty("user.home");
    private final Path settingsDir = Paths.get(home, ".iso");
    private final Path settingsFile = settingsDir.resolve("settings.properties");
    private final Properties settings = new Properties();
    private String output;

    private final JFrame frame = new JFrame("T2 Linux ISO Downloader");
    private final JPanel panel = new JPanel(new GridBagLayout());
    private final GridBagConstraints gbc = new GridBagConstraints();

    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel partLabel = new JLabel("Waiting to start...");

    private final JButton downloadButton = new JButton("Download ISO");
    private final JButton cancelButton = new JButton("Cancel Download");

    private final JCheckBox flashCheck = new JCheckBox("Flash ISO?");
    private final JTextField deviceField = new JTextField(15);

    private final JLabel flavourLabel = new JLabel("Flavour:");
    private final JComboBox<String> flavourBox = new JComboBox<>();

    private final JLabel versionLabel = new JLabel("Version:");
    private final JComboBox<String> versionBox = new JComboBox<>();

    private final MDEngine engine = new MDEngine();
    private final Map<String, List<String[]>> itemsByFlavour = new LinkedHashMap<>();

    private void showError(String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(frame, message, "Error", JOptionPane.ERROR_MESSAGE));
    }

    private void showWarning(String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(frame, message, "Warning", JOptionPane.WARNING_MESSAGE));
    }

    private void configureLookAndFeel() {
        ThemeUtil.configureLookAndFeel(getBool("darkMode") ? Boolean.TRUE : null);
    }

    private void refreshLookAndFeel() {
        configureLookAndFeel();
        SwingUtilities.updateComponentTreeUI(frame);
        frame.pack();
        frame.setMinimumSize(frame.getSize());
    }

    private void loadSettings() {
        try {
            Files.createDirectories(settingsDir);
            if (Files.exists(settingsFile)) {
                try (InputStream in = Files.newInputStream(settingsFile)) {
                    settings.load(in);
                }
            }
        } catch (IOException e) {
            showWarning("Could not read settings: " + e.getMessage());
        }
    }

    private void saveSettings() {
        try {
            Files.createDirectories(settingsDir);
            try (OutputStream out = Files.newOutputStream(settingsFile)) {
                settings.store(out, "ISO Settings");
            }
        } catch (IOException e) {
            showWarning("Could not save settings: " + e.getMessage());
        }
    }

    private boolean getBool(String key) {
        String defaultValue =
                ("verifyIso".equals(key) || "enableAutoMerge".equals(key)) ? "true" : "false";
        return Boolean.parseBoolean(settings.getProperty(key, defaultValue));
    }

    private void setBool(String key, boolean value) {
        settings.setProperty(key, Boolean.toString(value));
    }

    private List<String> loadItems() {
        try {
            engine.readMetadata();
        } catch (Exception e) {
            showError(e.getMessage());
            return List.of();
        }

        return engine.mergeToSingleLine(engine.getMetadata());
    }

    private void indexItemsByFlavour(List<String> items) {
        itemsByFlavour.clear();
        for (String line : items) {
            String[] parts = line.split(",");
            if (parts.length < 2) {
                continue;
            }
            parts[0] = parts[0].trim();
            parts[1] = parts[1].trim();
            if (parts.length > 2) {
                parts[2] = parts[2].trim();
            }
            itemsByFlavour.computeIfAbsent(parts[0], key -> new ArrayList<>()).add(parts);
        }
    }

    private boolean populateFlavours() {
        flavourBox.removeAllItems();
        Set<String> seen = new HashSet<>();
        for (String flavour : itemsByFlavour.keySet()) {
            if (seen.add(flavour)) {
                flavourBox.addItem(flavour);
            }
        }
        return flavourBox.getItemCount() > 0;
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem resumeItem = new JMenuItem("Resume Download");
        resumeItem.addActionListener(e -> startDownload());

        JMenu settingsMenu = new JMenu("Settings");
        JCheckBoxMenuItem enablePartsItem = new JCheckBoxMenuItem("Enable parts", getBool("enableParts"));
        JCheckBoxMenuItem enableResumeItem = new JCheckBoxMenuItem("Enable resume", getBool("enableResume"));
        JCheckBoxMenuItem enableAutoMergeItem =
                new JCheckBoxMenuItem("Enable auto merge", getBool("enableAutoMerge"));
        JCheckBoxMenuItem darkModeItem = new JCheckBoxMenuItem("Force dark mode", getBool("darkMode"));
        JCheckBoxMenuItem verifyIsoItem = new JCheckBoxMenuItem("Verify ISO", getBool("verifyIso"));
        JCheckBoxMenuItem verifyUsbItem = new JCheckBoxMenuItem("Verify USB", getBool("verifyUsb"));

        enableResumeItem.addActionListener(e -> {
            if (enableResumeItem.isSelected()) {
                enablePartsItem.setSelected(true);
                setBool("enableParts", true);
            }
            setBool("enableResume", enableResumeItem.isSelected());
            saveSettings();
        });
        enablePartsItem.addActionListener(e -> {
            if (!enablePartsItem.isSelected() && enableResumeItem.isSelected()) {
                enablePartsItem.setSelected(true);
                return;
            }
            setBool("enableParts", enablePartsItem.isSelected());
            saveSettings();
        });
        enableAutoMergeItem.addActionListener(e -> {
            setBool("enableAutoMerge", enableAutoMergeItem.isSelected());
            saveSettings();
        });
        darkModeItem.addActionListener(e -> {
            setBool("darkMode", darkModeItem.isSelected());
            saveSettings();
            refreshLookAndFeel();
        });
        verifyIsoItem.addActionListener(e -> {
            setBool("verifyIso", verifyIsoItem.isSelected());
            saveSettings();
        });
        verifyUsbItem.addActionListener(e -> {
            setBool("verifyUsb", verifyUsbItem.isSelected());
            saveSettings();
        });

        fileMenu.add(resumeItem);
        settingsMenu.add(enablePartsItem);
        settingsMenu.add(enableResumeItem);
        settingsMenu.add(enableAutoMergeItem);
        settingsMenu.add(darkModeItem);
        settingsMenu.add(verifyIsoItem);
        settingsMenu.add(verifyUsbItem);
        menuBar.add(fileMenu);
        menuBar.add(settingsMenu);
        return menuBar;
    }

    private void configureActions() {
        flashCheck.setSelected(false);
        deviceField.setEnabled(true);
        flashCheck.addActionListener(e -> {
            if(flashCheck.isSelected())downloadButton.setText("Download ISO and Flash!");
            else downloadButton.setText("Download ISO");
        });

        cancelButton.setVisible(false);
        cancelButton.addActionListener(e -> {
            int confirmCancel = JOptionPane.showConfirmDialog(
                    frame,
                    "Cancel current download?",
                    "Cancel Download",
                    JOptionPane.YES_NO_OPTION
            );

            if (confirmCancel != JOptionPane.YES_OPTION) {
                return;
            }

            downloader.setCancelled(true);
            showWarning("Download is cancelled");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            int deleteChoice = JOptionPane.showConfirmDialog(
                    frame,
                    "Delete partial file?",
                    "Delete Partial Download",
                    JOptionPane.YES_NO_OPTION
            );

            if (deleteChoice == JOptionPane.YES_OPTION && output != null) {
                new File(output).delete();
            }

            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(0);
                statusLabel.setText("Download cancelled");
                partLabel.setText("Idle");
                downloadButton.setVisible(true);
                cancelButton.setVisible(false);
            });
        });

        flavourBox.addActionListener(e -> {
            String selected = (String) flavourBox.getSelectedItem();
            if (selected == null) {
                return;
            }
            populateVersions(selected);
        });
        String initialSelected = (String) flavourBox.getSelectedItem();
        if (initialSelected != null) {
            populateVersions(initialSelected);
        }

        deviceField.addActionListener(e -> {
            String device = deviceField.getText().trim();

            if (!flashCheck.isSelected()) {
                statusLabel.setText("Flash disabled");
                return;
            }
            if (device.isEmpty()) {
                showError("No USB selected! Please click on the provided text box to select.");
                statusLabel.setText("Invalid device");
                return;
            }
            if (!device.startsWith("/dev/")) {
                showError("Device must start with /dev/ (example: /dev/sdb)");
                statusLabel.setText("Invalid device");
                return;
            }

            statusLabel.setText("Device selected: " + device);
        });
        deviceField.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openDeviceChooser();
            }
        });
        downloadButton.addActionListener(e -> {
            if (flashCheck.isSelected()) {
                String device = deviceField.getText().trim();
                if (device.isEmpty()) {
                    showError("No USB selected! Please click on the provided text box to select.");
                    return;
                }
                if (!device.startsWith("/dev/")) {
                    showError("Device must start with /dev/ (example: /dev/sdb)");
                    return;
                }
            }
            startDownload();
        });
    }

    private void populateVersions(String selectedFlavour) {
        versionBox.removeAllItems();
        List<String[]> flavourItems = itemsByFlavour.getOrDefault(selectedFlavour, List.of());
        for (String[] parts : flavourItems) {
            String version;
            if (parts.length > 2 && !parts[2].startsWith("http")) {
                version = parts[1] + " " + parts[2];
            } else {
                version = parts[1];
            }
            versionBox.addItem(version.trim());
        }
    }

    private void openDeviceChooser() {
        JFileChooser chooser = new JFileChooser();
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        File volumesDir = new File("/Volumes");
        File mediaDir = new File("/run/media/" + System.getProperty("user.name"));
        if (osName.contains("mac") && volumesDir.isDirectory()) {
            chooser.setCurrentDirectory(volumesDir);
        } else if (mediaDir.isDirectory()) {
            chooser.setCurrentDirectory(mediaDir);
        } else {
            chooser.setCurrentDirectory(new File(home));
        }
        chooser.setDialogTitle("Select USB Device");
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setMultiSelectionEnabled(false);

        int result = chooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            String selectedPath = chooser.getSelectedFile().getAbsolutePath();
            String resolvedDevice = selectedPath;
            try {
                USB usb = new USB();
                if (selectedPath.startsWith("/dev/")) {
                    resolvedDevice = usb.getParentDirectory(selectedPath);
                } else {
                    String partition = usb.getDF(selectedPath);
                    resolvedDevice = usb.getParentDirectory(partition);
                }
                deviceField.setText(resolvedDevice);
                statusLabel.setText("Device selected: " + resolvedDevice);
            } catch (Exception ex) {
                showError("Could not resolve device from selection. Choose a mounted USB volume or /dev path.");
                statusLabel.setText("Invalid device");
            }
        }
    }

    private void startDownload() {
        String selectedFlavour = Objects.requireNonNull(flavourBox.getSelectedItem()).toString();
        String selectedVersion = Objects.requireNonNull(versionBox.getSelectedItem()).toString();
        boolean flashSelected = flashCheck.isSelected();
        String selectedDevice = deviceField.getText().trim();
        String targetOutput = buildOutputPath(selectedFlavour, selectedVersion);
        List<String[]> flavourItems = itemsByFlavour.getOrDefault(selectedFlavour, List.of());

        new Thread(() -> {
            downloader.setCancelled(false);

            try {
                if (!handleExistingDownloadFile(targetOutput, flavourItems, selectedVersion, flashSelected)) {
                    SwingUtilities.invokeLater(() -> {
                        downloadButton.setVisible(true);
                        cancelButton.setVisible(false);
                    });
                    return;
                }
            } catch (Exception ex) {
                showError(ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    downloadButton.setVisible(true);
                    cancelButton.setVisible(false);
                });
                return;
            }

            SwingUtilities.invokeLater(() -> {
                downloadButton.setVisible(false);
                cancelButton.setVisible(true);
                statusLabel.setText(new File(targetOutput).exists() ? "Resuming" : "Starting");
            });

            try {
                download(flavourItems, selectedFlavour, selectedVersion, flashSelected, selectedDevice);
            } catch (Exception ex) {
                showError(ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    downloadButton.setVisible(true);
                    cancelButton.setVisible(false);
                    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                });
            }
        }).start();
    }

    private boolean handleExistingDownloadFile(String outputPath,
                                               List<String[]> flavourItems,
                                               String selectedVersion,
                                               boolean flashSelected) throws Exception {
        File outputFile = new File(outputPath);
        if (!outputFile.exists()) {
            return true;
        }

        if (flashSelected) {
            return true;
        }

        long expectedSize = computeExpectedIsoSize(flavourItems, selectedVersion);
        long existingSize = outputFile.length();
        if (expectedSize > 0 && existingSize == expectedSize) {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(
                            frame,
                            "This ISO is already downloaded.",
                            "Already Downloaded",
                            JOptionPane.INFORMATION_MESSAGE
                    ));
            return false;
        }

        AtomicInteger choice = new AtomicInteger(JOptionPane.CLOSED_OPTION);
        Runnable showPrompt = () -> {
            Object[] options = {"Continue", "Delete"};
            int result = JOptionPane.showOptionDialog(
                    frame,
                    "A previous download file already exists.\nContinue from existing data or delete and download again?",
                    "Existing Download Found",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]
            );
            choice.set(result);
        };

        if (SwingUtilities.isEventDispatchThread()) {
            showPrompt.run();
        } else {
            SwingUtilities.invokeAndWait(showPrompt);
        }

        if (choice.get() == 1) { // Delete
            if (!outputFile.delete()) {
                throw new RuntimeException("Could not delete existing download file.");
            }
            return true;
        }

        return choice.get() == 0; // Continue only
    }

    private long computeExpectedIsoSize(List<String[]> flavourItems, String selectedVersion) {
        long total = 0L;
        for (String[] data : flavourItems) {
            if (data.length < 4 || !isVersionMatch(data, selectedVersion)) {
                continue;
            }
            for (int p = 3; p < data.length; p++) {
                try {
                    long partSize = downloader.getRemoteFileSize(new java.net.URI(data[p]).toURL());
                    if (partSize <= 0) {
                        return -1L;
                    }
                    total += partSize;
                } catch (Exception ignored) {
                    return -1L;
                }
            }
        }
        return total > 0 ? total : -1L;
    }

    private void buildLayout() {
        progressBar.setStringPainted(true);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 1;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(flavourLabel, gbc);
        gbc.gridx = 1;
        panel.add(flavourBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(versionLabel, gbc);
        gbc.gridx = 1;
        panel.add(versionBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        panel.add(partLabel, gbc);

        gbc.gridy = 3;
        panel.add(progressBar, gbc);

        gbc.gridy = 4;
        panel.add(statusLabel, gbc);

        gbc.gridy = 5;
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        panel.add(flashCheck, gbc);
        gbc.gridx = 1;
        panel.add(deviceField, gbc);

        gbc.gridy = 6;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        panel.add(downloadButton, gbc);
        panel.add(cancelButton, gbc);
    }

    private void configureFrame(JMenuBar menuBar) {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setJMenuBar(menuBar);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        frame.add(panel);
        frame.pack();
        frame.setMinimumSize(frame.getSize());
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    void start() {
        loadSettings();
        configureLookAndFeel();

        List<String> items = loadItems();
        if (items.isEmpty()) {
            return;
        }
        indexItemsByFlavour(items);
        if (!populateFlavours()) {
            showError("No valid ISO metadata was found.");
            return;
        }

        configureActions();
        buildLayout();
        configureFrame(createMenuBar());
    }

    private String buildOutputPath(String edition, String version) {
        String iso = (edition + "-T2-" + version).replace(" ", "").replace(".", "") + ".iso";
        return home + "/Downloads/" + iso;
    }

    String sha256(String file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var fis = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[HASH_BUFFER_SIZE];
            int n;
            while ((n = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    void download(List<String[]> meta,
                  String edition,
                  String version,
                  boolean flashSelected,
                  String selectedDevice) throws Exception {
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        output = buildOutputPath(edition, version);
        long downloadedBytes = new File(output).exists() ? new File(output).length() : 0L;
        boolean downloadedAtLeastOnePart = false;

        for (String[] data : meta) {
            if (data.length < 4) {
                continue;
            }
            if (!edition.equals(data[0])) {
                continue;
            }
            if (!isVersionMatch(data, version)) {
                continue;
            }

            int totalParts = data.length - 3;
            if (totalParts <= 0) {
                continue;
            }
            downloadedAtLeastOnePart = true;

            for (int p = 0; p < totalParts; p++) {
                int partNum = p + 1;
                URL partUrl = new java.net.URI(data[3 + p]).toURL();
                long partSize = downloader.getRemoteFileSize(partUrl);

                if (partSize > 0 && downloadedBytes >= partSize) {
                    downloadedBytes -= partSize;
                    int skipped = partNum;
                    SwingUtilities.invokeLater(() ->
                            partLabel.setText("Skipping downloaded part " + skipped + " of " + totalParts));
                    continue;
                }

                long partOffset = downloadedBytes;
                downloadedBytes = 0L;
                SwingUtilities.invokeLater(() ->
                        partLabel.setText("Downloading part " + partNum + " of " + totalParts));

                downloader.downloadFile(progressBar, statusLabel,
                        partUrl, output, partOffset);
            }
        }

        if (!downloadedAtLeastOnePart) {
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("No matching ISO found");
                partLabel.setText("Idle");
                downloadButton.setVisible(true);
                cancelButton.setVisible(false);
            });
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            return;
        }

        if (downloader.isCancelled()) {
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(100);
            statusLabel.setText("Finished");
            partLabel.setText("Download complete");
            downloadButton.setVisible(true);
            cancelButton.setVisible(false);
        });

        try {
            String expectedSha = findExpectedSha256(meta, edition, version);
            String actualSha = null;
            if (getBool("verifyIso")) {
                SwingUtilities.invokeLater(() -> statusLabel.setText("Verifying ISO..."));
                actualSha = sha256(output);
                if (expectedSha == null || expectedSha.isBlank()) {
                    showWarning("Verify ISO is enabled, but metadata has no SHA256 for this distro.");
                } else if (!actualSha.equalsIgnoreCase(expectedSha)) {
                    showError("ISO verification failed: checksum mismatch.");
                    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    return;
                } else {
                    SwingUtilities.invokeLater(() -> statusLabel.setText("ISO verification passed"));
                }
            }

            if (flashSelected) {
                String device = selectedDevice;
                if (device.isEmpty() || !device.startsWith("/dev/")) {
                    showError("Cannot flash: invalid USB device path");
                } else {
                    long totalBytes = new File(output).length();
                    new USB().flash(
                            output,
                            device,
                            totalBytes,
                            progressBar,
                            statusLabel,
                            getBool("verifyUsb"),
                            expectedSha != null && !expectedSha.isBlank() ? expectedSha : actualSha
                    );
                }
            }
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        } catch (Exception e) {
            showError("Checksum failed");
        }
    }

    private boolean isVersionMatch(String[] data, String version) {
        return version.contains(data[1]) || (data.length > 2 && version.contains(data[2]));
    }

    private String findExpectedSha256(List<String[]> meta, String edition, String version) {
        for (String[] data : meta) {
            if (data.length < 4 || !edition.equals(data[0]) || !isVersionMatch(data, version)) {
                continue;
            }
            for (int i = 3; i < data.length; i++) {
                String candidate = data[i].trim();
                if (!candidate.startsWith("http")) {
                    continue;
                }
                String sha = engine.getSha256ForIsoUrl(candidate);
                if (sha != null && !sha.isBlank()) {
                    return sha;
                }
            }
        }
        return null;
    }

    public static void main(String[] args) {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank()) {
            String envJavaHome = System.getenv("JAVA_HOME");
            if (envJavaHome != null && !envJavaHome.isBlank()) {
                System.setProperty("java.home", envJavaHome);
            }
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("No graphical display detected. Set DISPLAY or run in a desktop session.");
            System.exit(1);
            return;
        }
        try {
            SwingUtilities.invokeLater(() -> new T2ISO().start());
        } catch (AWTError e) {
            System.err.println("Unable to start GUI: " + e.getMessage());
            System.exit(1);
        }
    }
}
