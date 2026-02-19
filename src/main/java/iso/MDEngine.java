package iso;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

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
    public void readMeta() throws IOException {
        File dir = new File(home + "/Downloads/");
        dir.mkdirs();

        File jsonFile = new File(dir, "distro-metadata.json");

        Downloader.downloadFile(metadata, jsonFile.getAbsolutePath());

        try (BufferedReader reader = new BufferedReader(new FileReader(jsonFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                contents.add(line);
            }
        }
    }

    public ArrayList getMetadata(){
        String[] strings = Arrays.stream(contents.toArray())
                .map(String::valueOf)
                .toArray(String[]::new);

        for(String i: strings){
            String s = "";
            if(i.contains("name"))
                s+= i.replace("\"name\":", "").replace("\"", "");
            if (i.contains("https://"))
                s+= i.replace(" ", "").replace("\"", "");
            contents.add(s);
        }
        return contents;
    }
    public String getMeta() {
        return metadata.toExternalForm();
    }

    public void setMeta(URL metadata) {
        this.metadata = metadata;
    }

//    static void main() throws IOException {
//        MDEngine engine = new MDEngine();
//        engine.readMeta();
//
//    }
}
