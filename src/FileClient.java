import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.logging.*;

/**
 * FileClient - Connects to a FileServer and receives files into a specified directory.
 * Continuously listens for file batches sent by the server every 60 seconds.
 *
 * Usage: java FileClient <host> <port> <directory>
 * Example: java FileClient localhost 9000 /path/to/receive
 */
public class FileClient {

    private static final Logger logger = Logger.getLogger(FileClient.class.getName());

    private final String host;
    private final int port;
    private final Path destinationDirectory;

    public FileClient(String host, int port, String directoryPath) {
        this.host = host;
        this.port = port;
        this.destinationDirectory = Paths.get(directoryPath);
    }

    public void start() throws IOException {
        // Create destination directory if it doesn't exist
        Files.createDirectories(destinationDirectory);

        logger.info("Connecting to server " + host + ":" + port);
        logger.info("Saving received files to: " + destinationDirectory.toAbsolutePath());

        try (Socket socket = new Socket(host, port)) {
            logger.info("Connected to server.");

            DataInputStream dis = new DataInputStream(socket.getInputStream());

            // Keep receiving file batches until the connection is closed
            while (!socket.isClosed()) {
                try {
                    receiveAllFiles(dis);
                } catch (EOFException e) {
                    logger.info("Server closed the connection.");
                    break;
                }
            }
        }
    }

    private void receiveAllFiles(DataInputStream dis) throws IOException {
        // Read the number of files incoming
        int fileCount = dis.readInt();

        if (fileCount == 0) {
            logger.info("Server sent 0 files this round.");
            return;
        }

        logger.info("Receiving " + fileCount + " file(s) from server...");

        for (int i = 0; i < fileCount; i++) {
            receiveFile(dis);
        }

        logger.info("Batch receive complete.");
    }

    private void receiveFile(DataInputStream dis) throws IOException {
        // Protocol: [filename (UTF)] [file size (long)] [file bytes]
        String fileName = dis.readUTF();
        long fileSize = dis.readLong();

        Path outputPath = destinationDirectory.resolve(fileName);

        byte[] buffer = new byte[8192];
        long remaining = fileSize;

        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int bytesRead = dis.read(buffer, 0, toRead);
                if (bytesRead == -1) {
                    throw new EOFException("Unexpected end of stream while reading file: " + fileName);
                }
                fos.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
        }

        logger.info("Received: " + fileName + " (" + fileSize + " bytes) -> " + outputPath);
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java FileClient <host> <port> <directory>");
            System.err.println("Example: java FileClient localhost 9000 /path/to/receive");
            System.exit(1);
        }

        String host = args[0];

        int port;
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port number: " + args[1]);
            System.exit(1);
            return;
        }

        try {
            new FileClient(host, port, args[2]).start();
        } catch (IOException e) {
            logger.severe("Client error: " + e.getMessage());
            System.exit(1);
        }
    }
}
