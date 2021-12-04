package drawlio.client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;

/**
 * This class handles the connection to, and communicates to, a Drawlio server. It uses datagram as
 * its transport layer protocol.
 * 
 * @author Karl Koivunen
 * @since 2021-02-18
 */
public class Connection implements Runnable {
    private Client client;
    private DatagramSocket socket;
    protected boolean connected = false;

    /**
     * Instantiates a new drawlio connection. Opens a datagram socket and attempt to connects it to
     * the Drawlio server's address. Also sends a connect message to the server.
     *
     * @param client the client.
     * @param serverAddress the Drawlio server's address.
     * @param serverPort the Drawlio server's port.
     * @throws IOException Signals that connecting or sending a message to the server failed.
     */
    public Connection(Client client, String serverAddress, int serverPort)
            throws IOException {
        this.client = client;

        socket = new DatagramSocket();
        socket.setSoTimeout(3000); // 3 seconds timeout. The server sends checks every ~2s.
        // Connect to the address to only recieve packets from the server.
        // This also makes send and recieve calls throw PortUnreachableException if the remote
        // destination doesn't exist, or is unreachable, and an ICMP destination unreachable packet
        // has been received for that address.
        socket.connect(new InetSocketAddress(serverAddress, serverPort));
        // Ask to join the game by sending a CONNECT message.
        String message = "CONNECT";
        byte[] data = message.getBytes("UTF-8");

        DatagramPacket packet = new DatagramPacket(data, data.length);
        socket.send(packet);
    }

    /**
     * Send the coordinates for a new paint point to the server.
     *
     * @param x the x-coordinate.
     * @param y the y-coordinate.
     */
    public void sendPointData(int x, int y) {
        sendMessage("POINT|" + x + ":" + y);
    }

    /**
     * Sends a message to the server.
     *
     * @param message the message to send.
     */
    public void sendMessage(String message) {
        try {
            byte[] data = message.getBytes("UTF-8");
            DatagramPacket packet = new DatagramPacket(data, data.length);
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("Unable to message server");
        }
    }

    /**
     * Stops the connection by sending a disconnect-message and closing the socket.
     */
    public void stop() {
        // If the socket is still open and connected, tell the server that this player is
        // disconnecting.
        if (!socket.isClosed() && socket.isConnected()) {
            sendMessage("DISCONNECT");
        }
        if (!socket.isClosed()) {
            socket.close();
        }
    }

    /**
     * The meathod that the thread runs. Waits for the server to confirm that this client's
     * connection. Then listens for packets from the server and handles them until the socket
     * closes.
     */
    @Override
    public void run() {
        String exitMessage = ""; // Potential message that gets printed to the CLI at program exit.
        DatagramPacket packet = new DatagramPacket(new byte[2048], 2048);
        try {
            // Wait for a CONNECTED message from the server, confirming that the player joined.
            while (!connected) {
                socket.receive(packet);
                String data = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                        "UTF-8");
                // Tokenize the message. A CONNECTED message includes this player's ID.
                String[] values = data.split("\\|", 2);
                if (values[0].equals("CONNECTED")) {
                    client.setPlayerId(Integer.parseInt(values[1]));
                    client.addChatMessage("Connected as: Player " + values[1], true, true);
                    connected = true;
                }
            }
        } catch (IOException e) {
            exitMessage = "Unable to connect";
            stop();
        }

        // Main loop. Recieve and tokenize message -> determine message type -> handle message.
        while (socket.isConnected() && !socket.isClosed()) {
            try {
                socket.receive(packet);
                connected = true;
                String data = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                        "UTF-8");
                // Tokenize the message. All messages from the server begins with a predefined type,
                // and some of them contain one or more values following the type.
                String[] message = data.split("\\|", 2);

                // Determine the message type. Following are the known ones and their function:
                // CHAT: Adds a message in the chat, like if a player connects or makes a guess.
                // POINT: Adds one or more drawing points to the canvas.
                // SCORES: Includes all players' scores. Updates the client's scoreboard with the
                // new scores.
                // DRAWING: Announces a new round with this client being the drawer.
                // GUESSING: Announces a new round with this client being a guesser.
                // WAITING: States that the game is waiting for more players to join before starting
                // a round.
                // GAMESTATE: States the gamestate and the role of this client's player
                // (drawing/guessing etc.). This is sent when joining and if the server notices
                // that the client is in the wrong state (due to lost packets etc.).
                // CLEAR: Clears all paint points on the canvas.
                // CHECK: Verifies the connectivity of this client. Includes a randomly generated
                // string that this client sends back to confirm.
                if (message[0].equals("CHAT")) {
                    // Further tokenize the message. Format is COLOR|BOLD|<text>.
                    // COLOR and BOLD is optional and tells that the text should be displayed in
                    // color and bolded respectively.
                    String[] values = message[1].split("\\|", 3);

                    boolean colored = values[0].equals("COLOR");
                    boolean bold = values[1].equals("BOLD");
                    client.addChatMessage(values[2], colored, bold);
                } else if (message[0].equals("POINT")) {
                    // Ignore the message if this player is drawing, since this client has already
                    // adds the points itself.
                    if (!client.isDrawing()) {
                        // Split all points in the message and add each.
                        String[] points = message[1].split(",");
                        for (String point : points) {
                            // Split the x and y-coordinate and add the point to the canvas.
                            String[] xy = point.split(":");
                            try {
                                client.addPoint(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]));
                            } catch (NumberFormatException e) {
                                System.err.println("Invalid point data: " + message[1]);
                            }
                        }
                    }
                } else if (message[0].equals("SCORES")) {
                    client.updateScores(message[1]);
                } else if (message[0].equals("DRAWING")) {
                    // The player is drawing the word in message[1].
                    client.newRound(true, message[1]);
                } else if (message[0].equals("GUESSING")) {
                    client.newRound(false, null);
                } else if (message[0].equals("WAITING")) {
                    client.waitingForPlayers();
                } else if (message[0].equals("GAMESTATE")) {
                    // Indicates the role this player has in the games state.
                    // If the player is the drawer, a second value is included with the word.
                    String[] values = message[1].split("\\|", 2);

                    if (values[0].equals("GUESSING")) {
                        client.setGameState(false, true, null);
                    } else if (values[0].equals("DRAWING")) {
                        client.setGameState(true, false, values[1]);
                    } else if (values[0].equals("CORRECT")) {
                        // If the player has already guessed the word.
                        client.setGameState(false, false, null);
                        client.setStatus("You guessed correctly!");
                    } else if (values[0].equals("WAITING")) {
                        client.waitingForPlayers();
                    } else {
                        System.err.println("UNKNOWN STATE: " + values[0]);
                        client.setGameState(false, false, null);
                    }
                } else if (message[0].equals("CLEAR")) {
                    client.clearCanvas();
                } else if (message[0].equals("CHECK")) {
                    socket.send(packet); // Return the same message back.
                } else {
                    System.err.println("Unrecognized message: " + data);
                }
            } catch (SocketTimeoutException e) {
                // The recieve() call timed out (3s).
                // If the client was connected prior to the timeout, set the connected flag to false
                // and continue and wait for again. If not (two consecutive timeouts), imply
                // connection lost and stop the application.
                if (connected) {
                    connected = false;
                    client.setStatus("Connecting...");
                } else {
                    client.addChatMessage("Connection to server lost. Exiting...", false, true);

                    try {
                        Thread.sleep(4000); // Give user time to see the message in the chat.
                    } catch (InterruptedException e1) {
                    }
                    stop();
                    exitMessage = "Connection to server timed out";
                }
            } catch (PortUnreachableException e) {
                exitMessage = "Lost connection to server";
                stop();
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    exitMessage = e.getMessage();
                    stop();
                }
            }
        }
        client.exit(exitMessage);
    }
}
