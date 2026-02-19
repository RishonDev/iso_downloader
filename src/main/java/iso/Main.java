import iso.MDEngine;

import javax.swing.*;
import java.awt.*;

void ui(){
    MDEngine engine = new MDEngine();
    JFrame frame = new JFrame();
    CardLayout layout = new CardLayout();
    JPanel mainPane = new JPanel();
    JPanel downloadPane = new JPanel();
    JMenu versions = new JMenu();
    JMenu edition = new JMenu();

    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (UnsupportedLookAndFeelException | ClassNotFoundException | InstantiationException |
             IllegalAccessException e) {
        throw new RuntimeException(e);
    }
    //Adding panes
    frame.add(mainPane, "1");
    frame.add(downloadPane, "2");
    layout.show(frame, "1");
    //Setting up positions
    versions.setBounds(150,200,100,30);
    edition.setBounds(150,300,100,30);
    //Adding components
    mainPane.add(versions);
    mainPane.add(edition);
    //Setup
    //frame.setContentPane(tabbedPane);
    frame.setPreferredSize(new Dimension(600,400));
    frame.setLocationRelativeTo(null);
    frame.setLayout(layout);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.pack();
    frame.setVisible(true);
}

public void checkOS(){
    String os = System.getProperty("os.name");
    if(os.toLowerCase().contains("win")){
        System.err.println("Windows systems are not supported.");
        System.exit(1);
    }
    else{
        System.out.println("Detected OS: " + os);
    }
}
void main(){
    checkOS();
    SwingUtilities.invokeLater(this::ui);

}
