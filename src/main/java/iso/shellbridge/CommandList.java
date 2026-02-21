package iso.shellbridge;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
/***
 * A class that allows you to run multiple Command classes.
***/
@SuppressWarnings("unused")
public class CommandList {
    ArrayList<Command> c = new ArrayList<>();
    /***
     * Adds a command ***/
    public void add(Command command){
        c.add(command);
    }
    public void add(Command[] commands){
        c.addAll(Arrays.asList(commands));
    }
    public void add(String[] commands){
        for(String i: commands)
            add(new Command(i));
    }
    public void add(String commands){
        c.add(new Command(commands));
    }
    public Command get(int i){return c.get(i);}
    public void exec() throws IOException, InterruptedException {
        for (int i = 0; i<c.toArray().length; i++){
            get(i).exec();
        }
    }
    public void run(){
        for (int i = 0; i<c.toArray().length; i++){
            get(i).run();
        }
    }
}
