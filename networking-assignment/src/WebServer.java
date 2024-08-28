import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

// The main file you run to get the server going.
public class WebServer {
    // Filled in by readConfig.
    public static ConfigValues VALUES;
    public final ExecutorService threadPool;
    private AtomicBoolean running = new AtomicBoolean(true);

    public WebServer() {
        this.threadPool = Executors.newFixedThreadPool(VALUES.getMaxThreads());
    }

    // server socket listens to incoming requests and passes them off to
    // RequestHandler to
    // deal with - for multithreading purposes.
    public void run() {
        ServerSocket serverSocket = null; // Declare serverSocket here
        System.out.println("Server started on port " + VALUES.getPort());
        try {
            serverSocket = new ServerSocket(VALUES.getPort());
            // atomic boolean used to ensure that updates to the variable are atomic and
            // thread-safe.
            while (running.get()) {
                // Accept a connection and handle it using a thread pool
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.err.println("Received new request!");
                    RequestHandler handler = new RequestHandler(clientSocket);
                    threadPool.submit(handler); // Directly submit the Runnable task to the thread pool.
                    System.err.println("Threadpool handled request.");
                } catch (IOException e) {
                    if (running.get()) {
                        System.out.println("Error accepting connection: " + e.getMessage());
                    } else {
                        System.out.println("Server is shutting down.");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("FATAL ERROR: Server stopped unexpectedly: " + e.toString());
        } finally {
            threadPool.shutdown();
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                System.out.println("Error closing server socket: " + e.getMessage());
            }
        }
        System.out.println("Shutting down server!");
    }

    // Call this method to stop the server
    public void stop() {
        running.set(false);
    }

    // Reads our config file and fills in the values
    // of the appropriate static variables.
    public static void readConfig() {
        VALUES = new ConfigValues("config.ini");
        RequestParser.setConfigValues(VALUES);
        RequestHandler.setConfig(VALUES);
    }

    public static void main(String[] args) {
        try {
            readConfig();
            WebServer server = new WebServer();
            server.run();
        } catch (Exception e) {
            e.printStackTrace(); // Print the exception to the console.
        }
    }
}