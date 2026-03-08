package iso;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.Theme;
import javafx.application.Application;
import javafx.scene.Scene;
import jfxtras.styles.jmetro.JMetro;
import jfxtras.styles.jmetro.Style;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class ThemeUtil {
    private static final String BASE_SHEET = "/themes/native.css";
    private static final String LIGHT_SHEET = "/themes/native-light.css";
    private static final String DARK_SHEET = "/themes/native-dark.css";

    private ThemeUtil() {
    }

    public static void applyTheme(Scene scene, Boolean forceDarkMode) {
        if (scene == null || scene.getRoot() == null) return;

        boolean darkMode = forceDarkMode != null ? forceDarkMode : isSystemDarkMode();
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        scene.getStylesheets().clear();

        if (osName.contains("mac") || osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            applyAtlantaFx(darkMode);
            return;
        }

        if (osName.contains("win")) {
            applyJMetro(scene, darkMode);
            return;
        }

        applyFallbackCss(scene, darkMode);
    }

    private static void applyAtlantaFx(boolean darkMode) {
        try {
            Theme theme = darkMode ? new CupertinoDark() : new CupertinoLight();
            Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
        } catch (Exception ignored) {
        }
    }

    private static void applyJMetro(Scene scene, boolean darkMode) {
        try {
            Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
            JMetro metro = new JMetro(darkMode ? Style.DARK : Style.LIGHT);
            metro.setScene(scene);
        } catch (Exception ignored) {
            applyFallbackCss(scene, darkMode);
        }
    }

    private static void applyFallbackCss(Scene scene, boolean darkMode) {
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
        addStylesheet(scene, BASE_SHEET);
        addStylesheet(scene, darkMode ? DARK_SHEET : LIGHT_SHEET);
    }

    private static void addStylesheet(Scene scene, String resourcePath) {
        var url = ThemeUtil.class.getResource(resourcePath);
        if (url != null) scene.getStylesheets().add(url.toExternalForm());
    }

    private static boolean isSystemDarkMode() {
        if (isMacOs()) return isMacDarkMode();
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) return isWindowsDarkMode();
        return isLinuxDarkMode();
    }

    private static boolean isMacOs() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return osName.contains("mac");
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
        if (output == null) return false;
        String normalized = output.toLowerCase(Locale.ROOT);
        return normalized.contains("0x0") || normalized.matches("(?s).*\\b0\\b.*");
    }

    private static boolean isLinuxDarkMode() {
        String gtkTheme = System.getenv("GTK_THEME");
        if (gtkTheme != null && gtkTheme.toLowerCase(Locale.ROOT).contains("dark")) return true;

        String colorScheme = runAndRead("gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
        if (colorScheme != null && colorScheme.toLowerCase(Locale.ROOT).contains("prefer-dark")) return true;

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
            if (process.exitValue() != 0) return null;
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
