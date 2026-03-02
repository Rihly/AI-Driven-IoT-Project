import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Random;

public class gameThread extends Thread{
    private Socket toClient;
    private String code = ""; // The code the client is trying to guess

    public gameThread(Socket S) {
        toClient = S;
    }

    public void run() {

        Random rng = new Random();

        try {
            // Get input from the client
            BufferedReader in = new BufferedReader(new InputStreamReader(toClient.getInputStream()));
            PrintStream out = new PrintStream(toClient.getOutputStream());

            for (int i = 0; i < 5; i++) {
                int digit = rng.nextInt(10);
                code = code + digit;
            }

            // Print out info related to a particular client's game
            System.out.println( "CODE : " + code + ", IP : " + toClient.getInetAddress());

            String guess = "";
            String result = "     ";
            int counter = 0;

            while (!result.equals("BBBBB") && counter <= 20) {
                if (counter == 0) {
                    out.println("GO");
                    counter++;
                } else {
                    guess = in.readLine();

                    if (guess.equals("QUIT")) break; // Exit the code
                    else {
                        // Print out the guess made by the client
                        System.out.println("Client " + toClient.getInetAddress() + " made the guess \"" + guess
                                + "\" on turn " + counter);
                        result = processGuess(guess);
                    }

                    counter++;
                    out.println(result);
                }
            }

            // Print out the result of the game
            if (result.equals("BBBBB")) System.out.println("Client " + toClient.getInetAddress() + " WON the game");
            else if (guess.equals("QUIT")) System.out.println("Client " + toClient.getInetAddress() + " QUIT the game");
            else System.out.println("Client " + toClient.getInetAddress() + " LOST the game");

            toClient.close(); // if done close socket

        } catch (IOException ioe) {
            System.out.println("IOException on socket listen: " + ioe);
            ioe.printStackTrace();
        }
    }

    String processGuess(String guess) {
        StringBuilder codeTemp = new StringBuilder(code);
        // a stringbuilder that can be manipulated to ensure a digit isn't read more than once
        String result = "";
        int cCount = 0;
        int bCount = 0;

        ArrayList<Integer> unhandledIndices = new ArrayList<>();
        //more insurance to protect against digits being read more than once

        for (int i = 0; i < 5; i++) {
            char current = guess.charAt(i);
            if (current == codeTemp.charAt(i)) {
                bCount++;
                codeTemp.setCharAt(i, ' ');
            } else { // if this is not a bull it could still be a cow, so we need to check it again
                unhandledIndices.add(i);
            }
        }

        for (int i : unhandledIndices) { // check the remaining characters
            String current = Character.toString(guess.charAt(i));
            int index = codeTemp.indexOf(current);
            if (index != -1) {
                cCount++;
                codeTemp.setCharAt(index, ' ');
            }
        }

        for (int i = 0; i < 5; i++) {
            if (cCount > 0) {
                result += "C";
                cCount--;
            } else if (bCount > 0) {
                result += "B";
                bCount--;
            } else {
                result += " ";
            }
        }

        return result;
    }
}
