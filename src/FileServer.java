import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * FileServer - Sends all files from a specified directory to a connected client.
 * Repeats the transfer every 60 seconds.
 *
 * Usage: java FileServer <port> <directory>
 * Example: java FileServer 9000 /path/to/send
 */
public class FileServer {

    private static final Logger logger = Logger.getLogger(FileServer.class.getName());
    private static final int TRANSFER_INTERVAL_SECONDS = 60;

    private final int port;
    private final Path sourceDirectory;

    public FileServer(int port, String directoryPath) {
        this.port = port;
        this.sourceDirectory = Paths.get(directoryPath);
    }

    public void start() throws IOException {
        if (!Files.isDirectory(sourceDirectory)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDirectory);
        }

        logger.info("Starting FileServer on port " + port);
        logger.info("Serving files from: " + sourceDirectory.toAbsolutePath());

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            logger.info("Waiting for client connection...");

            // Accept one persistent client connection
            try (Socket clientSocket = serverSocket.accept()) {
                logger.info("Client connected: " + clientSocket.getInetAddress());

                ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

                // Schedule file transfers every TRANSFER_INTERVAL_SECONDS seconds
                scheduler.scheduleAtFixedRate(() -> {
                    try {
                        sendAllFiles(clientSocket);
                    } catch (IOException e) {
                        logger.severe("Error during file transfer: " + e.getMessage());
                        scheduler.shutdown();
                    }
                }, 0, TRANSFER_INTERVAL_SECONDS, TimeUnit.SECONDS);

                // Keep the main thread alive until the scheduler is done
                try {
                    scheduler.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warning("Server interrupted.");
                } finally {
                    scheduler.shutdown();
                }
            }
        }
    }

    private void sendAllFiles(Socket socket) throws IOException {
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

        File[] files = sourceDirectory.toFile().listFiles(File::isFile);
        if (files == null || files.length == 0) {
            logger.info("No files to send in directory: " + sourceDirectory);
            // Send file count of 0 so client knows nothing is coming
            dos.writeInt(0);
            dos.flush();
            return;
        }

        logger.info("Sending " + files.length + " file(s) to client...");

        // Write total number of files being sent
        dos.writeInt(files.length);

        for (File file : files) {
            sendFile(dos, file);
        }

        dos.flush();
        logger.info("Transfer complete.");
    }

    private void sendFile(DataOutputStream dos, File file) throws IOException {
        byte[] fileBytes = Files.readAllBytes(file.toPath());

        // Protocol: [filename length (int)] [filename (UTF)] [file size (long)] [file bytes]
        dos.writeUTF(file.getName());
        dos.writeLong(fileBytes.length);
        dos.write(fileBytes);

        logger.info("Sent: " + file.getName() + " (" + fileBytes.length + " bytes)");
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java FileServer <port> <directory>");
            System.err.println("Example: java FileServer 9000 /path/to/send");
            System.exit(1);
        }

        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number: " + args[0]);
            System.exit(1);
            return;
        }

        try {
            new FileServer(port, args[1]).start();
        } catch (IOException e) {
            logger.severe("Server error: " + e.getMessage());
            System.exit(1);
        }
    }
}
