package iso;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
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
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class T2ISO extends Application {
    private static final int HASH_BUFFER_SIZE = 64 * 1024;

    private final Downloader downloader = new Downloader();
    private final String home = System.getProperty("user.home");
    private final Path settingsDir = Paths.get(home, ".iso");
    private final Path settingsFile = settingsDir.resolve("settings.properties");
    private final Properties settings = new Properties();
    private String output;

    private final ProgressBar progressBar = new ProgressBar(0);
    private final Label statusLabel = new Label("Ready");
    private final Label partLabel = new Label("Waiting to start...");

    private final Button downloadButton = new Button("Download ISO");
    private final Button cancelButton = new Button("Cancel Download");

    private final CheckBox flashCheck = new CheckBox("Flash ISO?");
    private final TextField deviceField = new TextField();

    private final Label flavourLabel = new Label("Flavour:");
    private final ComboBox<String> flavourBox = new ComboBox<>();

    private final Label versionLabel = new Label("Version:");
    private final ComboBox<String> versionBox = new ComboBox<>();

    private final MDEngine engine = new MDEngine();
    private final USB usb = new USB();
    private final Map<String, List<String[]>> itemsByFlavour = new LinkedHashMap<>();

    private Stage stage;
    private Scene scene;
    private volatile boolean preventClose = false;

    private void showError(String message) {
        Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", message));
    }

    private void showWarning(String message) {
        Platform.runLater(() -> showAlert(Alert.AlertType.WARNING, "Warning", message));
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (stage != null) alert.initOwner(stage);
        alert.showAndWait();
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

    private MenuBar createMenuBar() {
        Menu fileMenu = new Menu("File");
        MenuItem resumeItem = new MenuItem("Resume Download");
        resumeItem.setOnAction(e -> startDownload());

        Menu settingsMenu = new Menu("Settings");
        CheckMenuItem enablePartsItem = new CheckMenuItem("Enable parts");
        enablePartsItem.setSelected(Boolean.parseBoolean(settings.getProperty("enableParts", "false")));

        CheckMenuItem enableResumeItem = new CheckMenuItem("Enable resume");
        enableResumeItem.setSelected(Boolean.parseBoolean(settings.getProperty("enableResume", "false")));

        CheckMenuItem enableAutoMergeItem = new CheckMenuItem("Enable auto merge");
        enableAutoMergeItem.setSelected(Boolean.parseBoolean(settings.getProperty("enableAutoMerge", "true")));

        CheckMenuItem darkModeItem = new CheckMenuItem("Force dark mode");
        darkModeItem.setSelected(Boolean.parseBoolean(settings.getProperty("darkMode", "false")));

        CheckMenuItem verifyIsoItem = new CheckMenuItem("Verify ISO");
        verifyIsoItem.setSelected(Boolean.parseBoolean(settings.getProperty("verifyIso", "true")));

        CheckMenuItem verifyUsbItem = new CheckMenuItem("Verify USB");
        verifyUsbItem.setSelected(Boolean.parseBoolean(settings.getProperty("verifyUsb", "false")));

        enableResumeItem.setOnAction(e -> {
            if (enableResumeItem.isSelected()) {
                enablePartsItem.setSelected(true);
                settings.setProperty("enableParts", Boolean.toString(true));
            }
            settings.setProperty("enableResume", Boolean.toString(enableResumeItem.isSelected()));
            saveSettings();
        });

        enablePartsItem.setOnAction(e -> {
            if (!enablePartsItem.isSelected() && enableResumeItem.isSelected()) {
                enablePartsItem.setSelected(true);
                return;
            }
            settings.setProperty("enableParts", Boolean.toString(enablePartsItem.isSelected()));
            saveSettings();
        });

        enableAutoMergeItem.setOnAction(e -> {
            settings.setProperty("enableAutoMerge", Boolean.toString(enableAutoMergeItem.isSelected()));
            saveSettings();
        });

        darkModeItem.setOnAction(e -> {
            settings.setProperty("darkMode", Boolean.toString(darkModeItem.isSelected()));
            saveSettings();
            ThemeUtil.applyTheme(scene, Boolean.parseBoolean(settings.getProperty("darkMode", "false")) ? Boolean.TRUE : null);
        });

        verifyIsoItem.setOnAction(e -> {
            settings.setProperty("verifyIso", Boolean.toString(verifyIsoItem.isSelected()));
            saveSettings();
        });

        verifyUsbItem.setOnAction(e -> {
            settings.setProperty("verifyUsb", Boolean.toString(verifyUsbItem.isSelected()));
            saveSettings();
        });

        fileMenu.getItems().add(resumeItem);
        settingsMenu.getItems().addAll(
                enablePartsItem,
                enableResumeItem,
                enableAutoMergeItem,
                darkModeItem,
                verifyIsoItem,
                verifyUsbItem
        );

        return new MenuBar(fileMenu, settingsMenu);
    }

    private void configureActions() {
        flashCheck.setSelected(false);
        deviceField.setPrefColumnCount(18);
        downloadButton.setMaxWidth(Double.MAX_VALUE);
        cancelButton.setMaxWidth(Double.MAX_VALUE);
        downloadButton.managedProperty().bind(downloadButton.visibleProperty());
        cancelButton.managedProperty().bind(cancelButton.visibleProperty());

        flashCheck.setOnAction(e ->
                downloadButton.setText(flashCheck.isSelected() ? "Download ISO and Flash!" : "Download ISO"));

        downloadButton.setVisible(true);
        cancelButton.setVisible(false);
        setControlsBusy(false);
        cancelButton.setOnAction(e -> {
            ButtonType yes = new ButtonType("Yes", ButtonBar.ButtonData.YES);
            ButtonType no = new ButtonType("No", ButtonBar.ButtonData.NO);
            Alert cancelConfirm = new Alert(Alert.AlertType.CONFIRMATION, "Cancel current download?", yes, no);
            cancelConfirm.setTitle("Cancel Download");
            cancelConfirm.setHeaderText(null);
            cancelConfirm.initOwner(stage);

            Optional<ButtonType> confirm = cancelConfirm.showAndWait();
            if (confirm.isEmpty() || confirm.get() != yes) return;

            downloader.setCancelled(true);
            preventClose = false;
            showWarning("Download is cancelled");

            Alert deleteConfirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete partial file?", yes, no);
            deleteConfirm.setTitle("Delete Partial Download");
            deleteConfirm.setHeaderText(null);
            deleteConfirm.initOwner(stage);
            Optional<ButtonType> deleteChoice = deleteConfirm.showAndWait();

            if (deleteChoice.isPresent() && deleteChoice.get() == yes && output != null) {
                File partialFile = new File(output);
                boolean deleted = partialFile.delete();
                if (!deleted && partialFile.exists())
                    showWarning("Could not delete partial file: " + partialFile.getName());
            }

            progressBar.setProgress(0);
            statusLabel.setText("Download cancelled");
            partLabel.setText("Idle");
            setControlsBusy(false);
        });

        flavourBox.setOnAction(e -> {
            String selected = flavourBox.getSelectionModel().getSelectedItem();
            if (selected != null) populateVersions(selected);
        });

        String initialSelected = flavourBox.getSelectionModel().getSelectedItem();
        if (initialSelected != null) populateVersions(initialSelected);

        deviceField.setOnAction(e -> validateDeviceField());
        deviceField.setOnMouseClicked(e -> {
            if (e.getClickCount() >= 1) openDeviceChooser();
        });

        downloadButton.setOnAction(e -> {
            if (flashCheck.isSelected()) {
                String device = deviceField.getText().trim();
                if (!validateDevice(device, false)) return;
            }
            startDownload();
        });
    }

    private void validateDeviceField() {
        String device = deviceField.getText().trim();

        if (!flashCheck.isSelected()) {
            statusLabel.setText("Flash disabled");
            return;
        }
        validateDevice(device, true);
    }

    private void openDeviceChooser() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        File volumesDir = new File("/Volumes");
        File mediaDir = new File("/run/media/" + System.getProperty("user.name"));

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select mounted USB volume");

        if (osName.contains("mac") && volumesDir.isDirectory())
            chooser.setInitialDirectory(volumesDir);
        else if (mediaDir.isDirectory())
            chooser.setInitialDirectory(mediaDir);
        else
            chooser.setInitialDirectory(new File(home));

        File selected = chooser.showDialog(stage);
        if (selected != null) {
            String selectedPath = selected.getAbsolutePath();
            try {
                String partition = usb.getDF(selectedPath);
                String resolvedDevice = usb.getParentDirectory(partition);
                if (validateDevice(resolvedDevice, false)) {
                    deviceField.setText(resolvedDevice);
                    statusLabel.setText("Device selected: " + resolvedDevice);
                    return;
                }
            } catch (Exception ignored) {
                showError("Could not resolve device from selection.");
            }
        }

        statusLabel.setText("No device selected");
    }

    private void startDownload() {
        String selectedFlavour = flavourBox.getSelectionModel().getSelectedItem();
        if (selectedFlavour == null) {
            showError("Please select flavour first.");
            return;
        }
        String selectedVersion = versionBox.isVisible()
                ? versionBox.getSelectionModel().getSelectedItem()
                : "";
        if (versionBox.isVisible() && (selectedVersion == null || selectedVersion.isBlank())) {
            showError("Please select a version.");
            return;
        }

        boolean flashSelected = flashCheck.isSelected();
        String selectedDevice = deviceField.getText().trim();
        String targetOutput = buildOutputPath(selectedFlavour, selectedVersion);
        List<String[]> flavourItems = itemsByFlavour.getOrDefault(selectedFlavour, List.of());

        new Thread(() -> {
            downloader.setCancelled(false);

            try {
                if (!handleExistingDownloadFile(targetOutput, flavourItems, selectedVersion, flashSelected)) {
                    Platform.runLater(() -> setControlsBusy(false));
                    return;
                }
            } catch (Exception ex) {
                showError(ex.getMessage());
                Platform.runLater(() -> {
                    setControlsBusy(false);
                });
                return;
            }

            Platform.runLater(() -> {
                preventClose = true;
                setControlsBusy(true);
                statusLabel.setText(new File(targetOutput).exists() ? "Resuming" : "Starting");
            });

            try {
                download(flavourItems, selectedFlavour, selectedVersion, flashSelected, selectedDevice);
            } catch (Exception ex) {
                showError(ex.getMessage());
                Platform.runLater(() -> {
                    preventClose = false;
                    setControlsBusy(false);
                });
            }
        }, "iso-download-thread").start();
    }

    private boolean handleExistingDownloadFile(String outputPath,
                                               List<String[]> flavourItems,
                                               String selectedVersion,
                                               boolean flashSelected) throws Exception {
        File outputFile = new File(outputPath);
        if (!outputFile.exists()) return true;

        if (flashSelected) return true;

        long expectedSize = getExpectedSize(flavourItems, selectedVersion);
        long existingSize = outputFile.length();
        if (expectedSize > 0 && existingSize == expectedSize) {
            runOnFxThread(() -> {
                showAlert(Alert.AlertType.INFORMATION, "Already Downloaded", "This ISO is already downloaded.");
                return null;
            });
            return false;
        }

        return runOnFxThread(() -> {
            ButtonType continueBtn = new ButtonType("Continue", ButtonBar.ButtonData.YES);
            ButtonType deleteBtn = new ButtonType("Delete", ButtonBar.ButtonData.NO);
            ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert alert = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "A previous download file already exists. Continue from existing data or delete and download again?",
                    continueBtn,
                    deleteBtn,
                    cancelBtn
            );
            alert.setTitle("Existing Download Found");
            alert.setHeaderText(null);
            alert.initOwner(stage);
            Optional<ButtonType> choice = alert.showAndWait();

            if (choice.isPresent() && choice.get() == deleteBtn) {
                boolean deleted = outputFile.delete();
                if (!deleted && outputFile.exists()) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Could not delete existing download file.");
                    return false;
                }
                return true;
            }

            return choice.isPresent() && choice.get() == continueBtn;
        });
    }

    private BorderPane buildLayout() {
        progressBar.setPrefWidth(520);
        statusLabel.setId("statusLabel");
        partLabel.setId("partLabel");
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setAlignment(Pos.CENTER);
        partLabel.setMaxWidth(Double.MAX_VALUE);
        partLabel.setAlignment(Pos.CENTER);

        flavourBox.setItems(FXCollections.observableArrayList(flavourBox.getItems()));

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setHgap(10);
        grid.setVgap(12);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(120);
        ColumnConstraints inputColumn = new ColumnConstraints();
        inputColumn.setHgrow(Priority.ALWAYS);
        inputColumn.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelColumn, inputColumn);

        int row = 0;
        grid.add(flavourLabel, 0, row);
        grid.add(flavourBox, 1, row++);

        grid.add(versionLabel, 0, row);
        grid.add(versionBox, 1, row++);

        grid.add(partLabel, 0, row, 2, 1);
        row++;

        grid.add(progressBar, 0, row, 2, 1);
        row++;

        grid.add(statusLabel, 0, row, 2, 1);
        row++;

        grid.add(flashCheck, 0, row);
        grid.add(deviceField, 1, row);
        row++;

        grid.add(downloadButton, 0, row, 2, 1);
        grid.add(cancelButton, 0, row, 2, 1);
        GridPane.setHgrow(downloadButton, Priority.ALWAYS);
        GridPane.setHgrow(cancelButton, Priority.ALWAYS);
        GridPane.setFillWidth(downloadButton, true);
        GridPane.setFillWidth(cancelButton, true);

        BorderPane root = new BorderPane();
        root.setTop(createMenuBar());
        root.setCenter(grid);
        return root;
    }

    String sha256(String file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(file)) {
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
        output = buildOutputPath(edition, version);
        File outputFile = new File(output);
        long downloadedBytes = outputFile.exists() ? outputFile.length() : 0L;
        boolean downloadedAtLeastOnePart = false;

        for (String[] data : meta) {
            if (data.length < 4) continue;
            if (!edition.equals(data[0])) continue;
            if (!matchesVersion(data, version)) continue;

            int totalParts = data.length - 3;
            if (totalParts <= 0) continue;
            downloadedAtLeastOnePart = true;

            for (int p = 0; p < totalParts; p++) {
                int partNum = p + 1;
                URL partUrl = new java.net.URI(data[3 + p]).toURL();
                long partSize = downloader.getRemoteFileSize(partUrl);

                if (partSize > 0 && downloadedBytes >= partSize) {
                    downloadedBytes -= partSize;
                    int skipped = partNum;
                    Platform.runLater(() ->
                            partLabel.setText("Skipping downloaded part " + skipped + " of " + totalParts));
                    continue;
                }

                long partOffset = downloadedBytes;
                downloadedBytes = 0L;
                Platform.runLater(() ->
                        partLabel.setText("Downloading part " + partNum + " of " + totalParts));

                downloader.downloadFile(progressBar, statusLabel,
                        partUrl, output, partOffset);
            }
        }

        if (!downloadedAtLeastOnePart) {
            Platform.runLater(() -> {
                statusLabel.setText("No matching ISO found");
                partLabel.setText("Idle");
                setControlsBusy(false);
                preventClose = false;
            });
            return;
        }

        if (downloader.isCancelled()) {
            Platform.runLater(() -> preventClose = false);
            return;
        }

        Platform.runLater(() -> {
            progressBar.setProgress(1.0);
            statusLabel.setText("Finished");
            partLabel.setText("Download complete");
            setControlsBusy(false);
        });

        try {
            String expectedSha = getExpectedSha(meta, edition, version);
            String actualSha = null;
            if (Boolean.parseBoolean(settings.getProperty("verifyIso", "true"))) {
                Platform.runLater(() -> statusLabel.setText("Verifying ISO..."));
                actualSha = sha256(output);
                if (expectedSha == null || expectedSha.isBlank())
                    showWarning("Verify ISO is enabled, but metadata has no SHA256 for this distro.");
                else if (!actualSha.equalsIgnoreCase(expectedSha)) {
                    showError("ISO verification failed: checksum mismatch.");
                    Platform.runLater(() -> preventClose = false);
                    return;
                } else {
                    Platform.runLater(() -> statusLabel.setText("ISO verification passed"));
                }
            }

            if (flashSelected) {
                String device = selectedDevice;
                if (device.isEmpty() || !device.startsWith("/dev/"))
                    showError("Cannot flash: invalid USB device path");
                else {
                    usb.flash(
                            output,
                            device,
                            outputFile.length(),
                            progressBar,
                            statusLabel,
                            Boolean.parseBoolean(settings.getProperty("verifyUsb", "false")),
                            expectedSha != null && !expectedSha.isBlank() ? expectedSha : actualSha
                    );
                }
            }
            Platform.runLater(() -> preventClose = false);
        } catch (Exception e) {
            showError("Checksum failed");
            Platform.runLater(() -> preventClose = false);
        }
    }

    private String buildOutputPath(String edition, String version) {
        String name = (version == null || version.isBlank())
                ? edition + "-T2"
                : edition + "-T2-" + version;
        return Path.of(home, "Downloads", name.replaceAll("[^A-Za-z0-9._-]", "") + ".iso").toString();
    }

    private void setControlsBusy(boolean busy) {
        downloadButton.setVisible(!busy);
        cancelButton.setVisible(busy);
        flavourBox.setDisable(busy);
        versionBox.setDisable(busy);
        flashCheck.setDisable(busy);
        deviceField.setDisable(busy);
    }

    private void populateVersions(String flavour) {
        versionBox.getItems().clear();
        for (String[] parts : itemsByFlavour.getOrDefault(flavour, List.of())) {
            if (parts.length <= 1 || parts[1].startsWith("http")) continue;
            String label = (parts.length > 2 && !parts[2].startsWith("http"))
                    ? (parts[1] + " " + parts[2]).trim()
                    : parts[1].trim();
            versionBox.getItems().add(label);
        }
        if (!versionBox.getItems().isEmpty()) versionBox.getSelectionModel().selectFirst();
        setVersionControlsVisible(versionBox.getItems().size() > 1);
    }

    private void setVersionControlsVisible(boolean visible) {
        versionLabel.setManaged(visible);
        versionLabel.setVisible(visible);
        versionBox.setManaged(visible);
        versionBox.setVisible(visible);
    }

    private boolean validateDevice(String device, boolean updateStatusOnSuccess) {
        if (device.isEmpty()) {
            showError("No USB selected! Click the device field to choose or enter /dev path.");
            statusLabel.setText("Invalid device");
            return false;
        }
        if (!device.startsWith("/dev/")) {
            showError("Device must start with /dev/ (example: /dev/sdb)");
            statusLabel.setText("Invalid device");
            return false;
        }
        try {
            if (usb.isProtectedSystemDevice(device)) {
                showError("Refusing to flash the current system boot/root device.");
                statusLabel.setText("Protected device");
                return false;
            }
        } catch (Exception ex) {
            showWarning("Could not fully validate device safety: " + ex.getMessage());
            return false;
        }

        if (updateStatusOnSuccess) statusLabel.setText("Device selected: " + device);
        return true;
    }

    private boolean matchesVersion(String[] data, String selectedVersion) {
        return data.length < 2
                || data[1].startsWith("http")
                || selectedVersion == null
                || selectedVersion.isBlank()
                || selectedVersion.contains(data[1])
                || (data.length > 2 && !data[2].startsWith("http") && selectedVersion.contains(data[2]));
    }

    private long getExpectedSize(List<String[]> flavourItems, String selectedVersion) {
        long expectedSize = 0L;
        for (String[] data : flavourItems) {
            if (data.length < 4 || !matchesVersion(data, selectedVersion)) continue;
            for (int p = 3; p < data.length; p++) {
                try {
                    long partSize = downloader.getRemoteFileSize(new java.net.URI(data[p]).toURL());
                    if (partSize <= 0) return -1L;
                    expectedSize += partSize;
                } catch (Exception ignored) {
                    return -1L;
                }
            }
        }
        return expectedSize == 0 ? -1L : expectedSize;
    }

    private String getExpectedSha(List<String[]> meta, String edition, String version) {
        for (String[] data : meta) {
            if (data.length < 4 || !edition.equals(data[0]) || !matchesVersion(data, version)) continue;
            for (int i = 3; i < data.length; i++) {
                String candidate = data[i].trim();
                if (!candidate.startsWith("http")) continue;
                String sha = engine.getSha256ForIsoUrl(candidate);
                if (sha != null && !sha.isBlank()) return sha;
            }
        }
        return null;
    }

    private <T> T runOnFxThread(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) return action.call();
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get();
    }

    private static void configureNativeLibraryPath() {
        try {
            String command = ProcessHandle.current().info().command().orElse(null);
            if (command == null || command.isBlank()) return;
            Path dir = Paths.get(command).toAbsolutePath().getParent();
            if (dir == null) return;

            String dirPath = dir.toString();
            String current = System.getProperty("java.library.path", "");
            if (current == null || current.isBlank() || ".".equals(current)) {
                System.setProperty("java.library.path", dirPath);
                return;
            }
            if (!current.contains(dirPath))
                System.setProperty("java.library.path", dirPath + File.pathSeparator + current);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            String message = throwable == null ? "Unknown error" : throwable.getMessage();
            if (message == null || message.isBlank())
                message = throwable == null ? "Unknown error" : throwable.getClass().getSimpleName();
            String finalMessage = message;
            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Unexpected Error", finalMessage));
        });
        loadSettings();
        try {
            engine.readMetadata();
        } catch (Exception e) {
            showError(e.getMessage());
            return;
        }
        List<String> items = engine.mergeToSingleLine(engine.getMetadata());
        if (items.isEmpty()) return;
        itemsByFlavour.clear();
        for (String line : items) {
            String[] parts = line.split(",");
            if (parts.length < 2) continue;
            parts[0] = parts[0].trim();
            parts[1] = parts[1].trim();
            if (parts.length > 2) parts[2] = parts[2].trim();
            itemsByFlavour.computeIfAbsent(parts[0], key -> new ArrayList<>()).add(parts);
        }

        flavourBox.getItems().clear();
        Set<String> seen = new HashSet<>();
        for (String flavour : itemsByFlavour.keySet()) {
            if (seen.add(flavour)) flavourBox.getItems().add(flavour);
        }
        if (!flavourBox.getItems().isEmpty()) flavourBox.getSelectionModel().selectFirst();
        if (flavourBox.getItems().isEmpty()) {
            showError("No valid ISO metadata was found.");
            return;
        }

        configureActions();
        BorderPane root = buildLayout();
        scene = new Scene(root, 520, 380);
        ThemeUtil.applyTheme(scene, Boolean.parseBoolean(settings.getProperty("darkMode", "false")) ? Boolean.TRUE : null);
        stage.setTitle("T2 Linux ISO Downloader");
        stage.setScene(scene);
        stage.setMinWidth(500);
        stage.setMinHeight(380);
        stage.setOnCloseRequest(event -> {
            if (preventClose) {
                event.consume();
                showWarning("Please cancel the current operation before closing.");
            }
        });
        stage.show();
    }

    public static void main(String[] args) {
        configureNativeLibraryPath();
        launch(args);
    }
}
