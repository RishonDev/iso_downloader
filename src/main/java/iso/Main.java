import javax.swing.*;

void main(){
    checkOS();
    JFrame frame = new JFrame();
    try {
        UIManager.setLookAndFeel(UIManager.getLookAndFeel());
    } catch (UnsupportedLookAndFeelException e) {
        throw new RuntimeException(e);
    }
}
public void checkOS(){
    String os = System.getProperty("os.name");
    if(os.toLowerCase().contains("win")){
        System.err.println("Windows systems are not supported.");
    }
    else{
        System.out.println("Detected OS: " + os);
    }
}