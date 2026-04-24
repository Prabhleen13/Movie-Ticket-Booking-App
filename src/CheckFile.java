import java.io.File;

public class CheckFile {
    public static void main(String[] args) {
        // Change this to the absolute path you used in SQL
        String path = "C:\\Users\\DELL\\OneDrive\\Desktop\\Mannu\\posters\\dhurandhar2.jpeg";
        File f = new File(path);
        System.out.println("Looking for: " + f.getAbsolutePath());
        System.out.println("File exists: " + f.exists());
        System.out.println("Is file: " + f.isFile());
        System.out.println("Can read: " + f.canRead());
        System.out.println("Size: " + f.length());
    }
}