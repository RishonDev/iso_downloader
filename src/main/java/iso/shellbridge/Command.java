package iso.shellbridge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
/***
 * A class that provides the ability to execute commands, something that has been complicated in java
 * for a while now. With the new Command class, you can run, execute and get the output of commands.***/
public class Command {
    private String command;
    private String output;

    public String getCommand() {
        return command;
    }
    /***
     * Allows you to change the command if necessary. It is not recommended to reuse this
     * class, as the output may change. Use the CommandList class to run commands***/
    public void setCommand(String command) {
        this.command = command;
    }
    /***
     * Default constructor
     ***/
    public Command(String command){
        setCommand(command);
    }
    /***
     * Enables you to run commands while returning an exception if an error occurs.
     * This lets you ***/
    public void exec() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command.split(" ")); // Linux/macOS
        Process process = null;
        process = pb.start();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) output += (line + "\n");
        process.waitFor();
    }
    /***
     * Enables you to run the command. You do not need to add
     * method exception signatures or wrap it in a try/catch clause unlike the exec() function
     * ***/
    public void run(){
        ProcessBuilder pb = new ProcessBuilder(command.split(" ")); // Linux/macOS
        Process process = null;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        String line;
        while (true) {
            try {
                if (!((line = reader.readLine()) != null)) break;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            output += (line + "\n");
        }
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    public String getOutput(){
        return output;
    }
    public String[] getOutputArray(){
        return output.split("\n");
    }
}
