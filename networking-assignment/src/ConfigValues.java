import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigValues {
    private int SERVER_PORT;
    private String ROOT_PATH_STRING;
    private Path ROOT_PATH;
    private String DEFAULT_PAGE;
    private int MAX_THREADS;

    public ConfigValues(String configPath) throws IllegalArgumentException {
        if (!readConfig(configPath)) {
            System.err.println("Failed to read configuration file.");
            throw new IllegalArgumentException("Failed to read configuration file.");
        }

        // Handle tilde ("~") at the start of the path
        if (ROOT_PATH_STRING.startsWith("~")) {

            // Replace "~" with the user's home directory on Unix-like systems
            // You might log a warning or handle it differently on Windows or other systems
            Path homeDir = Paths.get(System.getProperty("user.home"));
            ROOT_PATH_STRING = ROOT_PATH_STRING.substring(2);
            ROOT_PATH_STRING = homeDir.resolve(ROOT_PATH_STRING).toString();
        }

        if (!convertPath()) {
            System.err.println("Failed to convert root path string to Path.");
            throw new IllegalArgumentException("Failed to convert root path string to Path.");
        }
        System.out.println("Configuration read successfully.");
    }

    // Manually parse the config.ini file
    private boolean readConfig(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("port=")) {
                    SERVER_PORT = Integer.parseInt(line.trim().substring(5));
                } else if (line.trim().startsWith("root=")) {
                    ROOT_PATH_STRING = line.trim().substring(5);
                } else if (line.trim().startsWith("defaultPage=")) {
                    DEFAULT_PAGE = line.trim().substring(12);
                } else if (line.trim().startsWith("maxThreads=")) {
                    MAX_THREADS = Integer.parseInt(line.trim().substring(11));
                }
            }
            return true;
        } catch (IOException e) {
            System.err.println("Error reading configuration file: " + e.getMessage());
            return false;
        }
    }

    public int getPort() {
        return SERVER_PORT;
    }

    public int getMaxThreads() {
        return MAX_THREADS;
    }

    public String getRootPathString() {
        // No idea why you'd use this, but putting it here.
        return ROOT_PATH_STRING;
    }

    public Path getRootPath() {
        return ROOT_PATH;
    }

    public String getDefaultPage() {
        return DEFAULT_PAGE;
    }

    // Converts ROOT_PATH_STRING into a path, puts it in ROOT_PATH.
    private boolean convertPath() {
        try {
            // Convert the ROOT_PATH_STRING to an absolute, normalized path
            ROOT_PATH = Paths.get(ROOT_PATH_STRING).toAbsolutePath().normalize();
            return true;
        } catch (Exception e) {
            System.err.println("Failed to convert path: " + e.getMessage());
            return false;
        }
    }

    public static void main(String[] args) {
        // For testing!

        // Testing ConfigValues works on its own.
        ConfigValues values = new ConfigValues("config.ini");
        System.out.println("Recieved configValues are: \n" +
                "Default page:" + values.getDefaultPage() +
                "\nMax Threads:" + values.getMaxThreads() +
                "\nRoot Path String:" + values.getRootPathString());
    }
}