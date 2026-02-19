import iso.MDEngine;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

void start() {
    MDEngine engine = new MDEngine();
    try {
        engine.readMetadata();
    } catch (IOException e) {
        throw new RuntimeException(e);
    }
    ArrayList<String> items = engine.mergeToSingleLine(engine.getMetadata("Ubuntu"));
    items.addAll(engine.mergeToSingleLine(engine.getMetadata("Mint")));

    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {
        throw new RuntimeException(e);
    }

    JFrame frame = new JFrame("T2 Linux ISO Downloader");
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setSize(520, 300);
    frame.setLocationRelativeTo(null);

    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(10, 10, 10, 10);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    JLabel flavourLabel = new JLabel("Flavour:");
    JComboBox<String> flavourBox = new JComboBox<>();

    JLabel versionLabel = new JLabel("Version:");
    JComboBox<String> versionBox = new JComboBox<>();

    // Safe population
    var seen = new java.util.HashSet<String>();

    for (String line : items) {
        String[] parts = line.split(",");
        if (parts.length < 2) continue;

        String flavour = parts[0].trim();
        String version = parts[1].trim();

        if (seen.add(flavour)) {
            flavourBox.addItem(flavour);
        }

        versionBox.addItem(version);
    }
    flavourBox.addActionListener(e -> {
        String selected = (String) flavourBox.getSelectedItem();

        versionBox.removeAllItems();

        for (String line : items) {
            String[] parts = line.split(",");
            if (parts.length < 2) continue;

            String flavour = parts[0].trim();
            String version = parts[1].trim();

            if (!flavour.equals(selected)) continue;

            // If Ubuntu → skip Mint desktops
            if (selected.equalsIgnoreCase("Ubuntu")) {
                if (version.equalsIgnoreCase("Cinnamon") ||
                        version.equalsIgnoreCase("MATE") ||
                        version.equalsIgnoreCase("XFCE")) {
                    continue;
                }
            }

            versionBox.addItem(version);
        }
    });
    flavourBox.setSelectedIndex(0);

    JProgressBar progressBar = new JProgressBar();
    progressBar.setStringPainted(true);

    JLabel statusLabel = new JLabel("Ready");
    statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

    JButton downloadButton = new JButton("Download ISO");

    gbc.gridx = 0; gbc.gridy = 0;
    panel.add(flavourLabel, gbc);

    gbc.gridx = 1;
    panel.add(flavourBox, gbc);

    gbc.gridx = 0; gbc.gridy = 1;
    panel.add(versionLabel, gbc);

    gbc.gridx = 1;
    panel.add(versionBox, gbc);

    gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
    panel.add(progressBar, gbc);

    gbc.gridy = 3;
    panel.add(statusLabel, gbc);

    gbc.gridy = 4;
    panel.add(downloadButton, gbc);

    frame.add(panel);
    frame.setVisible(true);
}

void checkOS() {
    String os = System.getProperty("os.name");
    if (os.toLowerCase().contains("win")) {
        System.err.println("Windows systems are not supported.");
        System.exit(1);
    } else {
        System.out.println("Detected OS: " + os);
    }
}

void main() {
    checkOS();
    SwingUtilities.invokeLater(this::start);
}
