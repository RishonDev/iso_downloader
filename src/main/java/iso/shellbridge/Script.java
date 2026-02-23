package iso.shellbridge;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/***
 * A special wrapper for the command class to run scripts on *nix based systems.
 * There is also an option to load the script directly into memory.
***/
@SuppressWarnings("unused")
public class Script {
    private Command chmod;
    private final CommandList commandList = new CommandList();
    private final String script;
    public Script(String path){
        this.script =path;
        setExecutable(true);
    }
    public void setExecutable(boolean exec){
        if(exec)
            chmod = new Command("chmod +x " + script);
        else
            chmod = new Command("chmod -x " + script);
        chmod.run();
    }
    public void setOwner(String user){
        chmod = new Command("chown " + user + " " + script);
        chmod.run();
    }
    public void load(String script) throws IOException {
        File file = new File(script);
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while (((line = reader.readLine()) != null)) {
                commandList.getCommands().add(new Command(line));
            }
        }
    }
}
