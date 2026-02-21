package iso;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Objects;

public class T2ISO {
    final String home = System.getProperty("user.home");

    final JFrame frame = new JFrame("T2 Linux ISO Downloader");
    final JPanel panel = new JPanel(new GridBagLayout());

    final JProgressBar progressBar = new JProgressBar();
    final JLabel statusLabel = new JLabel("Ready");
    final JLabel partLabel = new JLabel("Waiting to start...");
    final JButton downloadButton = new JButton("Download ISO");

    final JLabel flavourLabel = new JLabel("Flavour:");
    final JComboBox<String> flavourBox = new JComboBox<>();

    final JLabel versionLabel = new JLabel("Version:");
    final JComboBox<String> versionBox = new JComboBox<>();

    final GridBagConstraints gbc = new GridBagConstraints();

    final MDEngine engine = new MDEngine();

    void showError(String message) {
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(frame, message, "Error", JOptionPane.ERROR_MESSAGE));
    }

    void start() {
        try {
            engine.readMetadata();
        } catch (Exception e) {
            showError(e.getMessage());
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                 UnsupportedLookAndFeelException e) {
            throw new RuntimeException(e);
        }

        ArrayList<String> items = engine.mergeToSingleLine(engine.getMetadata("Ubuntu"));
        items.addAll(engine.mergeToSingleLine(engine.getMetadata("Mint")));

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(520, 320);
        frame.setLocationRelativeTo(null);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        var seen = new java.util.HashSet<String>();

        for (String line : items) {
            String[] parts = line.split(",");
            if (parts.length < 2) continue;

            if (seen.add(parts[0].trim())) {
                flavourBox.addItem(parts[0].trim());
            }
        }

        flavourBox.addActionListener(_ -> {
            String selected = (String) flavourBox.getSelectedItem();
            versionBox.removeAllItems();

            for (String line : items) {
                String[] parts = line.split(",");
                if (!parts[0].equals(selected)) continue;

                String version;
                if (parts.length > 2 && !parts[2].startsWith("http")) {
                    version = parts[1] + " " + parts[2];
                } else {
                    version = parts[1];
                }
                version = version.trim();
                versionBox.addItem(version);
            }
        });

        flavourBox.setSelectedIndex(0);

        downloadButton.addActionListener(_ -> new Thread(() -> {
            try {
                download(items,
                        Objects.requireNonNull(flavourBox.getSelectedItem()).toString(),
                        Objects.requireNonNull(versionBox.getSelectedItem()).toString());
            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        }).start());

        progressBar.setStringPainted(true);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

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
        panel.add(downloadButton, gbc);

        frame.add(panel);
        frame.setVisible(true);
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
            byte[] buffer = new byte[8192];
            int n;
            while ((n = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    void download(ArrayList<String> meta, String edition, String version) throws Exception {
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        String iso = (edition + "-T2-" + version).replace(" ", "").replace(".", "") + ".iso";
        String output = home + "/Downloads/" + iso;

        for (String i : meta) {
            String[] data = i.split(",");
            if (!edition.equals(data[0])) continue;

            if (!(version.contains(data[1]) || (data.length > 2 && version.contains(data[2])))) continue;

            int totalParts = data.length - 3;

            for (int p = 0; p < totalParts; p++) {
                int partNum = p + 1;
                SwingUtilities.invokeLater(() ->
                        partLabel.setText("Downloading part " + partNum + " of " + totalParts));

                Downloader.downloadFileWithProgress(progressBar, statusLabel,
                        new java.net.URI(data[3 + p]).toURL(), output);
            }
        }

        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(100);
            statusLabel.setText("Finished");
            partLabel.setText("Download complete");
        });

        revealFile(output);

        try {
            String checksum = sha256(output);
            JOptionPane.showMessageDialog(frame,
                    "SHA256:\n" + checksum,
                    "Integrity Check",
                    JOptionPane.INFORMATION_MESSAGE);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        } catch (Exception e) {
            showError("Checksum failed");
        }
    }

    static void main(String[] args) {

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new T2ISO().start();
            }
        });
    }
}