package drawlio.server;

/**
 * The entry point to the server application.
 * 
 * @author Karl Koivunen
 * @since 2021-12-04
 */
public class ServerLauncher {
    /**
     * The main method. Handles port argument, if any. Default is 50505. Creates a server with the
     * port and a list of words to use, and starts its listening method.
     *
     * @param args the optional argument telling what port to listen on.
     */
    public static void main(String[] args) {
        int port = 50505;
        if (args.length >= 1) {
            try {
                port = Integer.parseInt(args[0]);
                if (port < 1 || port > 65535) {
                    System.err.println(args[1] + " is out of range (1-65535). Using port 50505.");
                    port = 50505;
                }
            } catch (NumberFormatException e) {
                System.err.println(args[0] + " is not a valid port. Using port 50505.");
                port = 50505;
            }
        }
        String[] words = {"apple", "sweden", "war", "cool", "tree", "rain", "angry", "slide",
                "fast", "ostrich"};
        Server server = new Server(port, words);
        server.listen();
    }

}
