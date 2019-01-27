import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Scanner;

public class TCPServer {

    private static final int PORT = 9999;
    private static ServerSocket serverSocket;
    private static HashMap<Integer, ClientThread> clientMap;
    private static HashMap<String, Integer> activeUsers;
    private static ActivityLog serverLog;
    private static ActivityLog chatLog;

    public static void main(String[] args) {

        Runnable server = () -> {
            try {
                serverSocket = new ServerSocket(PORT);
            } catch (IOException e) {
                e.printStackTrace();
            }
            serverLog.record(getTimestamp() + " Started listening to port: " + PORT);

            clientMap = new HashMap<Integer, ClientThread>();
            activeUsers = new HashMap<String, Integer>();

            Socket socket;
            int index = 0;
            try {
                while (true) {
                    socket = serverSocket.accept();
                    serverLog.record(getTimestamp() + " Client " + index + " connected!");
                    ClientThread client = new ClientThread(socket, index);
                    clientMap.put(index++, client);
                    Thread thread = new Thread(client);
                    thread.start();
                }
            } catch (SocketException e) {
                // serverSocket closed

                // e.printStackTrace();
                // System.out.println(e);
            } catch (IOException e) {
                e.printStackTrace();
            }
        };

        // "Administrator" can start and stop running the server. Administrator can also
        // print a list of all users in the chat, and is able kick users from the chat.
        Runnable admin = () -> {
            System.out.println("Welcome Administrator.");
            System.out.println();
            System.out.println("Here is a list of your commands:");
            System.out.println("1. Enter \"start\" and turn server on.");
            System.out.println("2. Enter \"stop\" to shut down the server.");
            System.out.println("3. Enter \"users\" to see a list of users in the chat.");
            System.out.println("4. Enter \"kick\" to remove someone from the server.");

            Scanner console = new Scanner(System.in);
            String command = "";
            while (true) {
                command = console.nextLine().trim().toLowerCase();
                switch (command) {
                    case "start":
                        System.out.println("Server is starting up.");
                        serverLog = new ActivityLog("Server");
                        chatLog = new ActivityLog("Chat");
                        serverLog.record(getTimestamp() + " Admin has started up the server.");
                        new Thread(server).start();
                        break;
                    case "stop":
                        System.out.println("Server is shutting down.");
                        serverLog.record(getTimestamp() + " Admin has shutdown the server.");
                        serverLog.writeToFile();
                        chatLog.writeToFile();
                        try {
                            serverSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        System.exit(0);
                        break;
                    case "users":
                        System.out.println("List of users in the chat:");
                        serverLog.record(getTimestamp() + " Admin accessed list of active users.");
                        if (activeUsers.keySet().isEmpty()) {
                            System.out.println("\t(empty)");
                        } else {
                            for (String user : activeUsers.keySet()) {
                                System.out.println("\t" + user);
                            }
                        }
                        break;
                    case "kick":
                        System.out.println("Please enter the username of the person you would " +
                                "like to remove from the server.");
                        command = console.nextLine().trim();
                        if (activeUsers.keySet().contains(command)) {
                            System.out.println(command +
                                    " has been successfully removed from server.");
                            clientMap.get(activeUsers.get(command)).disconnect(true);
                        } else {
                            System.out.println("Sorry, the username you entered doesn't exist.");
                        }
                        break;
                    default:
                        System.out.println("Error: Invalid command entered, please enter a " +
                                "valid command.");
                        System.out.println("To see a list of commands, enter \"commands\".");
                        break;
                }
            }
        };
        new Thread(admin).start();
    }

    // Returns timestamps in the format of "HH:MM", includes a leading 0 for single digit
    // hour and minute values.
    private static String getTimestamp() {
        LocalDateTime ldt = LocalDateTime.now();
        String timestamp = "";
        if (ldt.getHour() < 10) {
            timestamp += "0";
        }
        timestamp += ldt.getHour() + ":";
        if (ldt.getMinute() < 10) {
            timestamp += "0";
        }
        timestamp += ldt.getMinute();
        return timestamp;
    }

    private static class ClientThread implements Runnable {

        private Socket socket;
        private int index;
        private BufferedReader input;
        private PrintWriter output;
        private String username;
        private boolean connected;

        public ClientThread(Socket socket, int index) {
            this.socket = socket;
            this.index = index;
        }

        @Override
        public void run() {
            try {
                input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                output = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                        socket.getOutputStream())), true);
                connected = true;

                // Ensures client is entering the chat with a unique username.
                // Clients who are connected to the server, but have not yet chosen a
                // unique username will not be able to receive or send messages to the
                // chat.
                boolean uniqueClient = false;
                output.println("Please enter a unique username:");
                do {
                    if ((username = input.readLine()) == null) {
                        connected = false;
                        break;
                    } else {
                        if (username.equals("")) {
                            output.println("Error! No username detected.");
                            output.println("Please enter a valid username: ");
                        } else {
                            synchronized (activeUsers) {
                                if (activeUsers.containsKey(username)) {
                                    output.println("Sorry! That username is already taken.");
                                    output.println("Please enter a different username: ");
                                } else {
                                    uniqueClient = true;
                                    activeUsers.put(username, index);
                                }
                            }
                        }
                    }
                } while (!uniqueClient);

                if (connected) {
                    broadcast("join", "");

                    String message = "";
                    while (connected) {
                        try {
                            if ((message = input.readLine()) == null) {
                                connected = false;
                            } else {
                                broadcast("send", message);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                disconnect(false);
                try {
                    input.close();
                    output.close();
                    socket.close();
                } catch (IOException e){
                    e.printStackTrace();
                }
            }
        }

        // Used to relay a message to all the users in the chat.
        private void broadcast(String action, String message) {
            String formatMessage = "";
            switch (action) {
                case "send":
                    formatMessage = getTimestamp() + " " + username + ": " + message;
                    break;
                case "join":
                    serverLog.record(getTimestamp() + " Client " + index +
                            " has joined the chat as: " + username);
                    formatMessage = getTimestamp() + " " + username + " has joined the room.";
                    break;
                case "leave":
                    serverLog.record(getTimestamp() + " Client " + index +
                            " (" + username + ") disconnected from the server.");
                    formatMessage = getTimestamp() + " " + username + " has left the room.";
                    break;
                case "kick":
                    serverLog.record(getTimestamp() + " Client " + index +
                            " (" + username + ") was kicked from the server.");
                    formatMessage = getTimestamp() +
                            " " + username + " has been kicked from the room";
            }
            chatLog.record(formatMessage);
            for (String user : activeUsers.keySet()) {
                ClientThread recipient = clientMap.get(activeUsers.get(user));
                recipient.output.println(formatMessage);
            }
        }

        // Disconnect process for clients leaving/getting kicked from the server.
        private void disconnect(boolean kick) {
            if (username == null) {
                serverLog.record(getTimestamp() +
                        " Client " + index + " left without joining the chat.");
            } else {
                if (kick) {
                    broadcast("kick", "");
                } else {
                    broadcast("leave", "");
                }
            }
            activeUsers.remove(username);
            clientMap.remove(index);

            // closeClientStreams String lets the client know it has been disconnected from
            // the server. A trigger message is sent to the client to signal it to close out
            // input/output steams and its socket. Helps clean up errors thrown once the
            // client is removed from the server, but the server/client is still trying to
            // send/receive data.
            String closeClientStreams = ("close client input/output streams").hashCode() + "";
            output.println(closeClientStreams);
        }
    }
}
