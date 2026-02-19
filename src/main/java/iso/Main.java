import javax.swing.*;

void main(){
    JFrame frame = new JFrame();
    try {
        UIManager.setLookAndFeel(UIManager.getLookAndFeel());
    } catch (UnsupportedLookAndFeelException e) {
        throw new RuntimeException(e);
    }

}
public void checkOS(){
    //if()
}

//public static void downloadFileWithProgress(JLabel label, URL url, String DirectoryOutputFileName) throws MalformedURLException {
//    try (InputStream in = url.openStream(); ReadableByteChannel rbc = Channels.newChannel(in); FileOutputStream fos = new FileOutputStream(DirectoryOutputFileName)) {
//        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
//        float size = iso.FileIO.convertToMegabytes(iso.FileIO.  getFileSize(url), "Bytes"), downloadedData = 0.0F;
//        while (downloadedData <= size) {
//            label.setText("Downloaded " + iso.FileIO.getFileSizeInMegabytes(DirectoryOutputFileName) + "MB out of " + size + "MB");
//            downloadedData = iso.FileIO.getFileSizeInMegabytes(Definitions.image.getAbsolutePath());
//        }
//    } catch (IOException e) {
//        throw new RuntimeException(e);
//    }
//}
