import iso.Downloader;
import iso.MDEngine;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
//Main Window
JFrame frame = new JFrame("T2 Linux ISO Downloader");
JPanel panel = new JPanel(new GridBagLayout());

//Progress bar
JProgressBar progressBar = new JProgressBar();
JLabel statusLabel = new JLabel("Ready");
JButton downloadButton = new JButton("Download ISO");


//Flavour menu
JLabel flavourLabel = new JLabel("Flavour:");
JComboBox<String> flavourBox = new JComboBox<>();

//Version  menu
JLabel versionLabel = new JLabel("Version:");
JComboBox<String> versionBox = new JComboBox<>();

//Layout
GridBagConstraints gbc = new GridBagConstraints();

//Mardown engine to read the meta data
MDEngine engine = new MDEngine();


void start() {
    //Setting up the engine
    try {
        engine.readMetadata();
    } catch (IOException e) {
        throw new RuntimeException(e);
    }
    ArrayList<String> items = engine.mergeToSingleLine(engine.getMetadata("Ubuntu"));
    items.addAll(engine.mergeToSingleLine(engine.getMetadata("Mint")));
    //Setting the theme to the system theme
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {
        throw new RuntimeException(e);
    }

    //Setting up the Window defaults.
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setSize(520, 300);
    frame.setLocationRelativeTo(null);
    panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

    gbc.insets = new Insets(10, 10, 10, 10);
    gbc.fill = GridBagConstraints.HORIZONTAL;

    // Population of the menus
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
    // FIlters the version menu when Ubuntu is selected
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
    //Workaround to fix duplicate antries in flavour menu.
    flavourBox.setSelectedIndex(0);
    downloadButton.addActionListener(e ->{
        try {
            download(items, String.valueOf(flavourBox.getSelectedItem()),String.valueOf(versionBox.getSelectedItem()));
        } catch (URISyntaxException | MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    });

    progressBar.setStringPainted(true);
    statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

    //Sets up the position of the components
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

void download(ArrayList<String> meta, String edition, String version) throws URISyntaxException, MalformedURLException {
    String iso = edition+"-"+version;
    iso = iso.replace(" ", "").replace(".", "");
    int n = 2;
    if(edition.contains("Ubuntu") || edition.contains("ubuntu"))n+=1;

    for(String i : meta) {
        String[] data = i.split(",");
        if (i.contains(data[0])) {
            if(i.contains(data[1]) || i.contains(data[2])) {
                Downloader.downloadFileWithProgress(progressBar, statusLabel,
                        new URI(data[n]).toURL(), "~/Downloads/" + iso);
                Downloader.downloadFileWithProgress(progressBar, statusLabel,
                        new URI(data[n + 1]).toURL(), "~/Downloads/" + iso);
                if (edition.contains("Ubuntu") || edition.contains("ubuntu")) {
                    Downloader.downloadFileWithProgress(progressBar, statusLabel,
                            new URI(data[n + 5]).toURL(), "~/Downloads/" + iso);
                }
            }

        }
    }
}
void main() {
    checkOS();
    SwingUtilities.invokeLater(this::start);
}
