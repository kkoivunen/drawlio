package drawlio.client;

/**
 * The entry point to the client application.
 * 
 * This application creates a client with a GUI and a connection to a drawlio server. Up to two
 * command-line arguments can be supplied that tells the server's address and port that this client
 * wants to connect to, defaults to loopback address and port 50505.
 * 
 * @author Karl Koivunen
 * @since 2021-12-04
 */
public class ClientLauncher {
    /**
     * The main method. Handles address/port arguments and starts the client.
     *
     * @param args optional arguments for the Drawlio server's address and port.
     */
    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                int port = 50505;
                String address = "127.0.0.1";
                // If address argument is supplied, use that instead.
                if (args.length >= 1) {
                    address = args[0];
                }

                // If port argument is supplied, use that instead.
                if (args.length >= 2) {
                    try {
                        port = Integer.parseInt(args[1]);
                        if (port < 1 || port > 65535) {
                            System.err.println(args[1] + " is out of range (1-65535). Using port 50505.");
                            port = 50505;
                        }
                    } catch (NumberFormatException e) {
                        System.err.println(args[1] + " is not a valid port. Using port 50505.");
                        port = 50505;
                    }
                }

                // Create and start the client.
                Client main = new Client(address, port);
            }
        });
    }
}
