package iso;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class MDEngine {
    private URL metadata;
    {
        try {
            metadata = new URL("https://raw.githubusercontent.com/t2linux/wiki/refs/heads/master/docs/tools/distro-metadata.json");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
    ArrayList<String> contents = new ArrayList<>();
    private final String home = System.getProperty("user.home");
    public void readMetadata() throws IOException {
        File dir = new File(home + "/.iso/");
        boolean b = dir.mkdirs();
        File jsonFile = new File(dir, "distro-metadata.json");

        Downloader.downloadFile(metadata, jsonFile.getAbsolutePath());

        try (BufferedReader reader = new BufferedReader(new FileReader(jsonFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                contents.add(line);
            }
        }
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

                // filter applied to entry
                if ((currentName != null && currentName.toLowerCase().contains(f))
                        || url.toLowerCase().contains(f)) {

                    lines.add(currentName + " " + url);
                }
            }
        }

        return lines;
    }


    public String getMeta() {
        return metadata.toExternalForm();
    }

    public void setMeta(URL metadata) {
        this.metadata = metadata;
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

    //FOR TESTING PURPOSES ONLY
//    static void main() throws IOException {
//        MDEngine engine = new MDEngine();
//        engine.readMetadata();
//        for(String i : engine.mergeToSingleLine(engine.getMetadata("Mint"))){
//            String[] data = i.split(",");
//            System.out.println(data[2]);
//            System.out.println(data[3]);
//        }
//        for(String i : engine.mergeToSingleLine(engine.getMetadata("Ubuntu"))){
////            System.out.println(i);
//            String[] data = i.split(",");
//            if(i.contains(data[0])){
//                System.out.println(data[3]);
//                System.out.println(data[4]);
//                System.out.println(data[5]);
//            }
//        }
//
//    }
}
