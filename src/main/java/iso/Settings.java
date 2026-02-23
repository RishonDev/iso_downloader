package iso;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public class Settings {
    private static final Path CONFIG_DIR =
            Paths.get(System.getProperty("user.home"), ".iso");

    private static final Path CONFIG_FILE =
            CONFIG_DIR.resolve("settings.properties");

    private static final Properties props = new Properties();

    private static void loadSettings() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | UnsupportedLookAndFeelException | IllegalAccessException |
                 InstantiationException e) {
            throw new RuntimeException(e);
        }
        try {
            Files.createDirectories(CONFIG_DIR);

            if (Files.exists(CONFIG_FILE)) {
                try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
                    props.load(in);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveSettings() {
        try (OutputStream out = Files.newOutputStream(CONFIG_FILE)) {
            props.store(out, "ISO Settings");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean getBool(String key) {
        return Boolean.parseBoolean(props.getProperty(key, "false"));
    }

    private static void setBool(String key, boolean value) {
        props.setProperty(key, Boolean.toString(value));
    }

    private static void createUI() {

        JFrame frame = new JFrame("Settings");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(300, 220);
        frame.setLayout(new BorderLayout());

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JCheckBox enableParts = new JCheckBox("Enable parts", getBool("enableParts"));
        JCheckBox enableResume = new JCheckBox("Enable resume", getBool("enableResume"));
        JCheckBox enableAutoMerge = new JCheckBox("Enable auto merge", getBool("enableAutoMerge"));
        JCheckBox darkMode = new JCheckBox("Dark mode", getBool("darkMode"));

        enableResume.addActionListener(e -> {
            if (enableResume.isSelected()) {
                enableParts.setSelected(true);
            }
        });
        enableParts.addActionListener(e ->{
            if (enableResume.isSelected()) {
                enableParts.setSelected(true);
            }
        });

        JButton saveBtn = new JButton("Save");

        saveBtn.addActionListener(e -> {
            setBool("enableParts", enableParts.isSelected());
            setBool("enableResume", enableResume.isSelected());
            setBool("enableAutoMerge", enableAutoMerge.isSelected());
            setBool("darkMode", darkMode.isSelected());

            saveSettings();

            JOptionPane.showMessageDialog(frame, "Saved to ~/.iso/");
        });

        panel.add(enableParts);
        panel.add(enableResume);
        panel.add(enableAutoMerge);
        panel.add(darkMode);

        frame.add(panel, BorderLayout.CENTER);
        frame.add(saveBtn, BorderLayout.SOUTH);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}