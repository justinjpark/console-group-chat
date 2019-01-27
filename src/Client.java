import java.io.*;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

// Messages from the client are sent to the server using a System.in scanner
// that reads in input from the console. If the server and client are
// disconnected accidentally or intentionally (kicked), the client is prompted
// with a "server maintenance" message. If the client tries to send messages
// while not connected, client is prompted with a "failed to send" message.
public class Client {

    private static BufferedReader input;
    private static PrintWriter output;
    private static boolean serverDown;

    public static void main(String[] args) throws IOException {
        InetAddress inetAddress = InetAddress.getLocalHost();
        String hostIP = inetAddress.getHostAddress();
        String hostName = inetAddress.getHostName();

        Socket socket;
        try {
            socket = new Socket(hostIP, 9999);

        } catch (ConnectException e) {
            System.out.println("Error: Unable to reach server.");
            return;
        }

        input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        output = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())),
                true);
        serverDown = false;

        Runnable sendingThread = () -> {
            Scanner console = new Scanner(System.in);
            String sendToServer = "";
            while (true) {
                sendToServer = console.nextLine().trim();
                if (serverDown) {
                    System.out.println("Sorry, your message failed to send.");
                } else {
                    output.println(sendToServer);
                }
            }
        };

        Runnable receivingThread = () -> {
            String receiveFromServer = "";
            while (!serverDown) {
                try {
                    receiveFromServer = input.readLine();
                    if (receiveFromServer == null) {
                        System.out.println("Sorry, our server is down for maintenance.");
                        serverDown = true;
                    } else if (receiveFromServer.equals(("close client input/output streams").hashCode() + "")) {
                        input.close();
                        output.close();
                        socket.close();
                        serverDown = true;
                    } else {
                        System.out.println(receiveFromServer);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        new Thread(sendingThread).start();
        new Thread(receivingThread).start();
    }
}
