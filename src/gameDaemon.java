import java.net.*;
import java.io.*;;

public class gameDaemon {
    public static void main( String[] args) {
        ServerSocket server;
        Socket toclientsocket;

        try {
            server = new ServerSocket(33338);

            System.out.println(server.getLocalSocketAddress());
            while (true) {
                toclientsocket = server.accept();
                gameThread nextThread = new gameThread(toclientsocket);
                nextThread.start();
            }

        }
        catch (IOException e) {}
    }
}