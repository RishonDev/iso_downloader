package iso;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MDEngine {
    private static final Pattern ENTRY_PATTERN = Pattern.compile(
            "\\{\\s*\"name\"\\s*:\\s*\"[^\"]+\"\\s*,\\s*\"iso\"\\s*:\\s*\\[(.*?)\\](.*?)\\}",
            Pattern.DOTALL
    );
    private static final Pattern URL_PATTERN = Pattern.compile("\"(https?://[^\"]+)\"");
    private static final Pattern SHA_PATTERN = Pattern.compile("\"sha256\"\\s*:\\s*\"([0-9a-fA-F]{64})\"");

    private URL metadata;
    {
        try {
            metadata = new URI("https://raw.githubusercontent.com/t2linux/wiki/refs/heads/master/docs/tools/distro-metadata.json").toURL();
        } catch (MalformedURLException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
    @SuppressWarnings("CanBeFinal")
    ArrayList<String> contents = new ArrayList<>();
    private final Map<String, String> shaByIsoUrl = new HashMap<>();
    private final String home = System.getProperty("user.home");
    public void readMetadata() throws IOException {
        File dir = new File(home + "/.iso/");
        dir.mkdirs();
        File jsonFile = new File(dir, "distro-metadata.json");
        contents.clear();

        AtomicReference<IOException> downloadError = new AtomicReference<>();
        Thread downloadThread = new Thread(() -> {
            try {
                Downloader.download(metadata, jsonFile.getAbsolutePath());
            } catch (IOException e) {
                downloadError.set(e);
            }
        }, "metadata-download-thread");
        downloadThread.start();

        try {
            downloadThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Metadata download was interrupted", e);
        }

        IOException error = downloadError.get();
        if (error != null && !jsonFile.exists()) {
            throw error;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(jsonFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                contents.add(line);
            }
        }
        indexShaByIsoUrl();
    }
    public ArrayList<String> getMetadata() {
        ArrayList<String> lines = new ArrayList<>();

        String currentName = null;

        for (String line : contents) {
            if (line.contains("\"name\"")) {
                currentName = line.replace("\"name\":", "")
                        .replace("\"", "")
                        .trim();
            }

            if (line.contains("https://") && currentName != null) {
                String url = line.replace(" ", "")
                        .replace("\"", "")
                        .trim();

                lines.add(currentName + " " + url);
                currentName = null; // reset
            }
        }

        return lines;
    }

    public ArrayList<String> getMetadata(String filter) {
        ArrayList<String> lines = new ArrayList<>();

        String currentName = null;
        String f = filter.toLowerCase();

        for (String line : contents) {
            String lower = line.toLowerCase();

            // detect new name
            if (lower.contains("\"name\"")) {
                currentName = line.replace("\"name\":", "")
                        .replace("\"", "")
                        .replace(",", "")
                        .trim();
            }

            // detect iso url
            if (lower.contains("http")) {
                String url = line.replace("\"", "")
                        .replace(",", "")
                        .trim();
                if (currentName == null || currentName.isBlank()) {
                    continue;
                }

                // filter applied to entry
                if ((currentName != null && currentName.toLowerCase().contains(f))
                        || url.toLowerCase().contains(f)) {

                    lines.add(currentName + " " + url);
                }
            }
        }

        return lines;
    }

    public ArrayList<String> mergeToSingleLine(ArrayList<String> input) {
        Map<String, ArrayList<String>> map = new LinkedHashMap<>();

        // Group URLs by name
        for (String line : input) {
            int idx = line.indexOf(" https://");
            if (idx == -1) continue;

            String name = line.substring(0, idx);
            String url = line.substring(idx + 1);

            map.computeIfAbsent(name, k -> new ArrayList<>()).add(url);
        }

        ArrayList<String> result = new ArrayList<>();

        for (Map.Entry<String, ArrayList<String>> entry : map.entrySet()) {
            String name = entry.getKey();
            ArrayList<String> urls = entry.getValue();

            String csvHeader;

            // Case 1 — contains version + codename (Ubuntu style)
            if (name.matches(".*\\d+\\.\\d+.*-.*")) {
                String[] parts = name.split(" - ", 2);

                String main = parts[0];
                String codename = parts.length > 1 ? parts[1] : "";

                int lastSpace = main.lastIndexOf(' ');
                String distro = main.substring(0, lastSpace);
                String version = main.substring(lastSpace + 1);

                csvHeader = distro + "," + version + "," + codename;
            }
            // Case 2 — edition only (Mint / NixOS)
            else if (name.contains(" - ")) {
                String[] parts = name.split(" - ", 2);
                csvHeader = parts[0] + "," + parts[1];
            }
            // Case 3 — single name
            else {
                csvHeader = name;
            }

            StringBuilder sb = new StringBuilder(csvHeader);

            for (String url : urls) {
                sb.append(",").append(url);
            }

            result.add(sb.toString());
        }

        return result;
    }

    public String getSha256ForIsoUrl(String isoUrl) {
        if (isoUrl == null || isoUrl.isBlank()) {
            return null;
        }
        return shaByIsoUrl.get(isoUrl.trim());
    }

    private void indexShaByIsoUrl() {
        shaByIsoUrl.clear();
        String json = String.join("\n", contents);
        Matcher entryMatcher = ENTRY_PATTERN.matcher(json);
        while (entryMatcher.find()) {
            String isoBlock = entryMatcher.group(1);
            String suffixBlock = entryMatcher.group(2);
            Matcher shaMatcher = SHA_PATTERN.matcher(suffixBlock);
            if (!shaMatcher.find()) {
                continue;
            }
            String sha = shaMatcher.group(1).toLowerCase();
            Matcher urlMatcher = URL_PATTERN.matcher(isoBlock);
            while (urlMatcher.find()) {
                shaByIsoUrl.put(urlMatcher.group(1), sha);
            }
        }
    }
}
