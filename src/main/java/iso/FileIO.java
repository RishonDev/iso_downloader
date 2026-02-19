package iso;

import com.sun.tools.javac.Main;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

public class FileIO {
    public static File getRunning_JAR_FilePath() throws URISyntaxException {
        return new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    public static int getFileSize(URL url) {
        URLConnection conn = null;
        try {
            conn = url.openConnection();
            if (conn instanceof HttpURLConnection) {
                ((HttpURLConnection) conn).setRequestMethod("HEAD");
            }
            conn.getInputStream();
            return conn.getContentLength();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (conn instanceof HttpURLConnection) {
                ((HttpURLConnection) conn).disconnect();
            }
        }
    }

    public static long getFileSizeInBits(String Directory) {
        return new File(Directory).length() * 8;
    }

    public static long getFileSizeInBytes(String Directory) {
        return new File(Directory).length();
    }

    public static long getFileSizeInKilobytes(String Directory) {
        return new File(Directory).length() / 1024;
    }

    public static long getFileSizeInMegabytes(String Directory) {
        return (long) (new File(Directory).length() / Math.pow(1024, 2));
    }

    public static long getFileSizeInGigabytes(String Directory) {
        return (long) (new File(Directory).length() / Math.pow(1024, 3));
    }

    public static long convertToBits(long value, String inputValueUnit) {
        return switch (inputValueUnit) {
            case "Bytes" -> value * 8;
            case "Kilobytes" -> value * 1024 * 8;
            case "Megabytes" -> value * 1024 * 1024 * 8;
            case "Gigabytes" -> value * 1024 * 1024 * 1024 * 8;
            default -> 0;
        };
    }

    public static long convertToBytes(long value, String inputValueUnit) {
        return switch (inputValueUnit) {
            case "Bits" -> value / 8;
            case "Kilobytes" -> value * 1024;
            case "Megabytes" -> value * 1024 * 1024;
            case "Gigabytes" -> value * 1024 * 1024 * 1024;
            default -> 0;
        };
    }

    public static long convertToKilobytes(long value, String inputValueUnit) {
        long size = switch (inputValueUnit) {
            case "Bits" -> value / 8 / 1024;
            case "Bytes" -> value / 1024;
            case "Megabytes" -> value * 1024;
            case "Gigabytes" -> value * 1024 * 1024;
            default -> 0;
        };
        return size;
    }


    public static long convertToMegabytes(long value, String inputValueUnit) {
        long size = switch (inputValueUnit) {
            case "Bits" -> value / 8 / 1024 / 1024;
            case "Bytes" -> value / 1024 / 1024;
            case "Kilobytes" -> value / 1024;
            case "Gigabytes" -> value * 1024;
            default -> 0;
        };
        return size;
    }

    public static long convertToGigabytes(long value, String inputValueUnit) {
        long size = switch (inputValueUnit) {
            case "Bits" -> value / 8 / 1024 / 1024 / 1024;
            case "Bytes" -> value / 1024 / 1024 / 1024;
            case "Kilobytes" -> value / 1024 / 1024;
            case "Megabytes" -> value / 1024;
            default -> 0;
        };
        return size;
    }
    //public void getFileSize()

}
