import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.Queue;

// Activity logger, stores any data received into a Queue.
// When used in the TCPServer, data is stored in real time, but is only printed
// out to a file once the administrator shuts down the server properly.
public class ActivityLog {

    private String type;
    private Queue<String> log;

    public ActivityLog(String type) {
        this.type = type;
        log = new LinkedList<String>();
        log.add((type + " Log").toUpperCase());
    }

    public void record(String activity) {
        log.add(activity);
    }

    public void writeToFile() {
        String fileName = type + "Log.txt";
        try {
            File file = new File("./Logs/" + fileName);
            if (!file.exists()) {
                file.createNewFile();
            }
            PrintWriter writer = new PrintWriter(file);
            writer.println(log.remove());
            writer.println();
            while (!log.isEmpty()) {
                writer.println(log.remove());
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
