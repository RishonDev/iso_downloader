package iso;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.Desktop;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class T2ISO {
    private static final int HASH_BUFFER_SIZE = 64 * 1024;

    private final Downloader downloader = new Downloader();
    private final String home = System.getProperty("user.home");
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
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                 UnsupportedLookAndFeelException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> loadItems() {
        try {
            engine.readMetadata();
        } catch (Exception e) {
            showError(e.getMessage());
            return List.of();
        }

        ArrayList<String> items = engine.mergeToSingleLine(engine.getMetadata("Ubuntu"));
        items.addAll(engine.mergeToSingleLine(engine.getMetadata("Mint")));
        return items;
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

        fileMenu.add(resumeItem);
        menuBar.add(fileMenu);
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
        File mediaDir = new File("/run/media/" + System.getProperty("user.name"));
        if (mediaDir.isDirectory()) {
            chooser.setCurrentDirectory(mediaDir);
        } else {
            chooser.setCurrentDirectory(new File(home));
        }
        chooser.setDialogTitle("Select USB Device");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(false);

        int result = chooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            String selectedPath = chooser.getSelectedFile().getAbsolutePath();
            deviceField.setText(selectedPath);
            statusLabel.setText("Device selected: " + selectedPath);
        }
    }

    private void startDownload() {
        new Thread(() -> {
            downloader.setCancelled(false);

            String selectedFlavour = Objects.requireNonNull(flavourBox.getSelectedItem()).toString();
            String selectedVersion = Objects.requireNonNull(versionBox.getSelectedItem()).toString();
            String targetOutput = buildOutputPath(selectedFlavour, selectedVersion);

            SwingUtilities.invokeLater(() -> {
                downloadButton.setVisible(false);
                cancelButton.setVisible(true);
                statusLabel.setText(new File(targetOutput).exists() ? "Resuming" : "Starting");
            });

            try {
                download(itemsByFlavour.getOrDefault(selectedFlavour, List.of()), selectedFlavour, selectedVersion);
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

    void revealFile(String path) {
        try {
            Desktop.getDesktop().open(new File(path).getParentFile());
        } catch (Exception e) {
            showError("Could not open file location");
        }
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

    void download(List<String[]> meta, String edition, String version) throws Exception {
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        output = buildOutputPath(edition, version);
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
                SwingUtilities.invokeLater(() ->
                        partLabel.setText("Downloading part " + partNum + " of " + totalParts));

                downloader.downloadFile(progressBar, statusLabel,
                        new java.net.URI(data[3 + p]).toURL(), output);
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

        revealFile(output);
        try {
            String checksum = sha256(output);
            JOptionPane.showMessageDialog(frame,
                    "SHA256:\n" + checksum,
                    "Integrity Check",
                    JOptionPane.INFORMATION_MESSAGE);
            if (flashCheck.isSelected()) {
                String device = deviceField.getText().trim();
                if (device.isEmpty() || !device.startsWith("/dev/")) {
                    showError("Cannot flash: invalid USB device path");
                } else {
                    long totalBytes = new File(output).length();
                    new USB().flash(output, device, totalBytes, progressBar, statusLabel);
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new T2ISO().start());
    }
}
