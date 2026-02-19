import iso.MDEngine;

import javax.swing.*;
import java.awt.*;

void ui() {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
             UnsupportedLookAndFeelException e) {
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

    // ===== Flavour =====
    JLabel flavourLabel = new JLabel("Flavour:");
    JComboBox<String> flavourBox = new JComboBox<>(
            new String[]{"Ubuntu", "Kubuntu", "Ubuntu Unity"}
    );

    // ===== Version =====
    JLabel versionLabel = new JLabel("Version:");
    JComboBox<String> versionBox = new JComboBox<>(
            new String[]{"24.04 LTS", "25.10"}
    );

    // ===== Progress =====
    JProgressBar progressBar = new JProgressBar();
    progressBar.setStringPainted(true);

    // ===== Status =====
    JLabel statusLabel = new JLabel("Ready");
    statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

    // ===== Download Button =====
    JButton downloadButton = new JButton("Download ISO");

    // Layout
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

public void checkOS(){
    String os = System.getProperty("os.name");
    if(os.toLowerCase().contains("win")){
        System.err.println("Windows systems are not supported.");
        System.exit(1);
    }
    else{
        System.out.println("Detected OS: " + os);
    }
}
void main(){
    checkOS();
    SwingUtilities.invokeLater(this::ui);

}
