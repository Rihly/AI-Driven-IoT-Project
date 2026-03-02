import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.*;
import java.util.Scanner;

public class BullsandCows {
    public static void main( String[] args) {
        Socket toserversocket;
        try {
            toserversocket = new Socket("10.238.0.94", 33338);

            BufferedReader in = new BufferedReader(new InputStreamReader(toserversocket.getInputStream()));
            PrintStream out = new PrintStream(toserversocket.getOutputStream());
            Scanner userInput = new Scanner(System.in);

            System.out.println(toserversocket.getLocalAddress());

            int counter = 0;
            String guessString = "";

            while (counter <= 20) {
                String input = in.readLine();

                if (input.equals("GO")) {
                    System.out.println("Welcome to Bulls and Cows. You will try to guess a 5 digit code using\n" +
                            "only the digits 0-9). You will lose the game if you are unable to guess\n" +
                            "the code correctly in 20 guesses. Good Luck!\n");
                } else if (input.equals("BBBBB")) {
                    System.out.println("\nCongratulations!!! You guessed the code correctly in " + counter + " guesses");
                    return;
                } else {
                    System.out.println(guessString + "  " + input);
                    if (counter == 20) {
                        System.out.println("\nSorry – the game is over. You did not guess the code correctly in 20 moves.");
                        break;
                    }
                }

                do {
                    System.out.print("Please enter your guess for the secret code or \"QUIT\" : ");
                    guessString = userInput.nextLine();

                    if (guessString.equals("QUIT")) {
                        System.out.println("\nGoodbye but please play again!");
                        out.println("QUIT");
                        return;
                    }

                } while (!verifyInput(guessString));

                out.println(guessString);
                counter++;
            }

        }
        catch (IOException e) {}
    }

    static boolean verifyInput(String GS) {
        if (GS.equals("QUIT")) {
            return true;
        }

        boolean isDigits = true;
        for (char c : GS.toCharArray()) {
            if (!Character.isDigit(c)) {
                isDigits = false;
                break;
            }
        }

        if (isDigits && GS.length() == 5) {
            return true;
        }

        System.out.println("Improperly formatted guess.");
        return false;
    }
}