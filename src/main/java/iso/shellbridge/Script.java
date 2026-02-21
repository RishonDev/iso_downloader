package iso.shellbridge;
/***
 * A special wrapper for the command class to run scripts on *nix based systems.
***/
@SuppressWarnings("unused")
public class Script {
    private Command command, chmod;
    @SuppressWarnings({"CanBeFinal", "FieldMayBeFinal"})
    private String script;
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
}
