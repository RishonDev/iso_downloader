package iso;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class ThemeUtil {
    private ThemeUtil() {
    }

    public static void configureLookAndFeel() {
        configureLookAndFeel(null);
    }

    public static void configureLookAndFeel(Boolean forceDarkMode) {
        try {
            boolean darkMode = forceDarkMode != null ? forceDarkMode : isSystemDarkMode();
            if (darkMode) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                     UnsupportedLookAndFeelException ignored) {
            }
        }
    }

    private static boolean isSystemDarkMode() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("mac")) {
            return isMacDarkMode();
        }
        if (osName.contains("win")) {
            return isWindowsDarkMode();
        }
        return isLinuxDarkMode();
    }

    private static boolean isMacDarkMode() {
        String output = runAndRead("defaults", "read", "-g", "AppleInterfaceStyle");
        return output != null && output.toLowerCase(Locale.ROOT).contains("dark");
    }

    private static boolean isWindowsDarkMode() {
        String output = runAndRead(
                "reg",
                "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v",
                "AppsUseLightTheme"
        );
        if (output == null) {
            return false;
        }
        String normalized = output.toLowerCase(Locale.ROOT);
        return normalized.contains("0x0") || normalized.matches("(?s).*\\b0\\b.*");
    }

    private static boolean isLinuxDarkMode() {
        String gtkTheme = System.getenv("GTK_THEME");
        if (gtkTheme != null && gtkTheme.toLowerCase(Locale.ROOT).contains("dark")) {
            return true;
        }

        String colorScheme = runAndRead("gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
        if (colorScheme != null && colorScheme.toLowerCase(Locale.ROOT).contains("prefer-dark")) {
            return true;
        }

        String theme = runAndRead("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme");
        return theme != null && theme.toLowerCase(Locale.ROOT).contains("dark");
    }

    private static String runAndRead(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean exited = process.waitFor(2, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line).append('\n');
                }
                return result.toString();
            }
        } catch (Exception ignored) {
            return null;
        }
    }
}
