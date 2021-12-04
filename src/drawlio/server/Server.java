package drawlio.server;

import java.awt.Point;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

/**
 * This class is the server-side Drawlio application. It handles a GameLogic instance and
 * communicates with Drawlio client applications. This server runs in an indefinite loop where it
 * listens for packets from client and handles them accoringly.
 * 
 * The server communicates uses datagram for communication with the clients. As datagram is
 * connectionless, this server periodically sends a randomly generated check-string to all connected
 * clients that they have to reply back with to confirm that they are still connected.
 * 
 * Since the server's only function during runtime is listening for packets and handling them,
 * creating a seperate thread for that purpose isn't necessary. And since the server communicates
 * using UDP, listening for packets from all clients can be done in a single loop.
 * If the server were to use TCP, seperate threads for every connection would be more approapriate.
 * 
 * The server and the clients communicates using a set of predefined message types. The type is
 * always place first in the packet's message. Some messages are only the type, like "CONNECT", but
 * others have additional values following the type, where the type and each value is seperated by a
 * '|' character. E.g. "POINT|44:187".
 * 
 * Following are the known types of messages recieved from clients:
 * CONNECT: A new player wants to connect and join the game.
 * GUESS: A player makes a guess.
 * POINT: Coordinates of a new drawing point on the canvas by the drawing player.
 * CHECK: A returned check message, verifying the client is still running.
 * DISCONNECT: A player want to disconnect from the game.
 * 
 * @author Karl Koivunen
 * @since 2021-02-21
 */
public class Server {
    private DatagramSocket serverSocket;
    private int serverPort;
    // The last string sent out to the clients. Returned check strings are compared to this.
    private String lastCheckString;
    // A flag telling if a new connection check should be sent.
    private boolean checkPlayers = false;

    private GameLogic logic;
    private ArrayList<Player> players = new ArrayList<Player>();
    // All points on the canvas.
    private HashSet<Point> drawingPoints = new HashSet<Point>();

    /**
     * Instantiates a new drawlio server.
     *
     * @param serverPort the port the server will listen on.
     * @param words the words that will be drawn.
     */
    public Server(int serverPort, String[] words) {
        this.serverPort = serverPort;
        logic = new GameLogic(players, words);
    }

    /**
     * Send message to a player's client application. The values in passed String array will be
     * joined and seperated by a '|' character in the final message.
     *
     * @param messageTokens the text values that will be joined in the message.
     * @param recipient the player whose client to send to.
     */
    private void sendMessage(String[] messageTokens, Player recipient) {
        String message = String.join("|", messageTokens);
        byte[] data;
        try {
            SocketAddress address = recipient.getSocketAddress();
            data = message.getBytes("UTF-8");
            DatagramPacket packet = new DatagramPacket(data, data.length, address);
            serverSocket.send(packet);
        } catch (UnsupportedEncodingException | UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Broadcast a message to all connected players. The values in passed String array will be
     * joined and seperated by a '|' character in the final message.
     *
     * @param messageTokens the text values that will be joined in the message.
     */
    private void broadcastMessage(String[] messageTokens) {
        SocketAddress address;
        String message = String.join("|", messageTokens);
        byte[] data = null;
        try {
            data = message.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        DatagramPacket packet = new DatagramPacket(data, data.length);
        for (Player player : players) {
            address = player.getSocketAddress();
            packet.setSocketAddress(address);
            try {
                serverSocket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Broadcast a message to appear in all players' chat. Supplies "COLOR" or "BOLD" to the message
     * if the text should be colored or bolded, otherwise lefts respective value blank.
     *
     * @param text the text message to add to the chat.
     * @param colored if the text should be colored.
     * @param bold if the text should be bold.
     */
    private void broadcastChatMessage(String text, boolean colored, boolean bold) {
        broadcastMessage(new String[] {"CHAT", colored ? "COLOR" : "", bold ? "BOLD" : "", text});
    }

    /**
     * Opens and binds a datagram socket to the local host adress. Listens for packets in a loop,
     * until an IOException is thrown.
     * 
     * A repeating 2-second timer which sets the checkPlayers flag is created. Once the flag is set,
     * all clients will be checked and potentially removed from the game after multiple missing
     * responses.
     */
    protected void listen() {
        // Create a timer and a task that sets the checkPlayers flag.
        Timer timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                checkPlayers = true;
            }
        };
        // Run the task every 2 seconds after the first 4 seconds.
        timer.schedule(task, 4000, 2000);

        try (DatagramSocket socket = new DatagramSocket(serverPort)) {
            serverSocket = socket;
            System.out.println("SERVER RUNNING ON: " + InetAddress.getLocalHost().getHostAddress());
            System.out.println("LISTENING ON PORT: " + serverPort);
            // Set a small timeout to allow check to be done while listening.
            serverSocket.setSoTimeout(100);

            boolean running = true;
            while (running) {
                // If the check flag is true, check the connectivity of all players.
                if (checkPlayers) {
                    checkPlayerConnections();
                    // Check if a round is in progress but it's in a state where it can be finished.
                    // This can happen if many players or the drawer were removed.
                    if (!logic.isWaitingForPlayers() && logic.isRoundFinished()) {
                        broadcastChatMessage("Ending round.", false, true);
                        if (finishRound()) {
                            newRound(false);
                        }
                    }

                    checkPlayers = false;
                }

                DatagramPacket packet = new DatagramPacket(new byte[512], 512);
                try {
                    serverSocket.receive(packet);
                    processMessage(packet);
                } catch (SocketTimeoutException e) {
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Processes a message from a client application. Determines the message type and handles the
     * message accordingly if the type is known.
     *
     * @param packet the packet containing the message.
     * @throws UnsupportedEncodingException if UTF-8 is not a supported encoding.
     */
    private void processMessage(DatagramPacket packet) throws UnsupportedEncodingException {
        String data = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
        // Split the message type from its value (if any).
        String[] values = data.split("\\|", 2);
        values[0] = values[0].toUpperCase();

        // Informative text that gets printed to the standard output stream about incoming packets.
        // Is not printed for "POINT" or "CHECK" due to their high frequency.
        String logText = "\n" + values[0] + ": From " + packet.getAddress().getHostAddress() + ":"
                + packet.getPort();
        Player sender = determinePlayer(packet.getSocketAddress());

        // Determine the type of the recieved message and make sure it meets the requirement.
        // CONNECT require that the sender is not already connected and the game is not full.
        // GUESS, POINT and CHECK messages must contain an additional value along with the type.
        if (values[0].equals("CONNECT") && sender == null && players.size() < 11) {
            System.out.println(logText);
            Player newPlayer = new Player(packet.getSocketAddress());
            // Broadcast to the other clients that a new player connected.
            broadcastChatMessage(newPlayer + " joined", false, true);
            // Confirm the connection and inform the client of it's player ID.
            sendMessage(new String[] {"CONNECTED", String.valueOf(newPlayer.getId())}, newPlayer);

            // Add the player. Start a new round if adding them results in a round being able to
            // start. Otherwise send the current gamestate.
            // Due to the nature of UDP, the gamestate can arrive to the client before the CONNECTED
            // message, which causes problems for the client.
            if (logic.addPlayer(newPlayer)) {
                newRound(true);
            } else {
                sendGameState(newPlayer, true);
            }
            broadcastScores();
        } else if (sender == null) {
            // Messages other than CONNECT requires that the sender is already connected.
            System.out.println(logText);
            System.out.println("ILLEGAL ACTION -> NOT A PLAYER: " + data);
        } else if (values[0].equals("GUESS") && values.length == 2) {
            System.out.println(logText);
            if (!logic.isActionLegal(sender, values[0])) {
                System.out.println("ILLEGAL ACTION -> Player is not guessing");
                // Send the client the correct gamestate.
                sendGameState(sender, true);
                return;
            }

            System.out.println("-> " + values[1]);
            // Check if the guess was correct and update the player's state accordingly.
            if (logic.guessWord(sender, values[1])) {
                broadcastChatMessage(sender + " guessed the word!", true, false);
                // Check if the round should be finished after this guess.
                if (logic.isRoundFinished()) {
                    if (finishRound()) {
                        newRound(false);
                    }
                } else {
                    sendGameState(sender, false);
                }
            } else {
                broadcastChatMessage(sender + ": " + values[1], false, false);
            }
        } else if (values[0].equals("POINT") && values.length == 2) {
            if (!logic.isActionLegal(sender, values[0])) {
                sendGameState(sender, true);
                return;
            }

            String[] xy = values[1].split(":");
            drawingPoints.add(new Point(Integer.parseInt(xy[0]), Integer.parseInt(xy[1])));
            // Send the new point to all other players.
            broadcastMessage(new String[] {"POINT", values[1]});
        } else if (values[0].equals("CHECK") && values.length == 2) {
            // Reset the count for this player if they returned the most recent check string.
            if (values[1].equals(lastCheckString)) {
                sender.unRespondedChecks = 0;
            }
        } else if (values[0].equals("DISCONNECT")) {
            System.out.println(logText);
            players.remove(sender);
            broadcastChatMessage(sender + " disconnected.", false, true);
            if (sender.isDrawing()) {
                System.out.println("-> Drawer disconnected");
                broadcastChatMessage("Drawer disconnected.", false, true);
            }
            System.out.println("-> " + sender + " disconnected.");

            broadcastScores();
            // Finish the round if this disconnect causes the rount to not being able to continue.
            if (!logic.isWaitingForPlayers() && logic.isRoundFinished()) {
                broadcastChatMessage("Ending round.", false, true);
                if (finishRound()) {
                    newRound(false);
                }
            }
        } else {
            System.out.println(logText);
            System.out.println("INVALID MESSAGE -> " + data);
        }
    }

    /**
     * Checks all player connections and remove any player who is assumed to not be "connected".
     * 
     * This function generates a new check string, sends it to all players and adds +1 unresponed
     * check to all players. The unresponded checks is reset to 0 by this server if a player's
     * client responds the the most recent check string. A player is assumed to be disconnected if
     * they have at least 4 unresponded check, i.e. they haven't responded to the last 4 checks.
     *
     * @throws UnsupportedEncodingException if UTF-8 is not a supported encoding.
     */
    private void checkPlayerConnections() throws UnsupportedEncodingException {
        Iterator<Player> i = players.iterator();
        while (i.hasNext()) {
            Player player = i.next();
            // Remove players with >=4 unresponded checks
            if (player.unRespondedChecks >= 4) {
                i.remove();
                System.out.println("\n[CHECK]: " + player + " failed too many checks, removing");
                broadcastChatMessage(player + " disconnected.", false, true);
                if (player.isDrawing()) {
                    System.out.println("[CHECK]: Drawer disconnected");
                    broadcastChatMessage("Drawer disconnected.", false, true);
                }
            } else {
                player.unRespondedChecks++;
            }
        }

        byte[] randomBytes = new byte[12];
        new Random().nextBytes(randomBytes);
        lastCheckString = new String(randomBytes, "UTF-8");
        broadcastMessage(new String[] {"CHECK", lastCheckString});
    }

    /**
     * Determines the player with the given address.
     *
     * @param address the player's address.
     * @return the player with this address, or null if no player was found.
     */
    private Player determinePlayer(SocketAddress address) {
        return players.stream().filter(player -> player.equals(address)).findFirst().orElse(null);
    }

    /**
     * Finishes the current round. Set the game state to "waiting for player" is there's not enough
     * players to start a new round.
     *
     * @return true, if a new round can be started.
     */
    private boolean finishRound() {
        logic.finishRound();
        String correctWord = logic.getCurrentWord().toUpperCase();
        System.out.println("\n[GAME]: Round finished. Word: " + correctWord);

        broadcastChatMessage("Round finished. Word was " + correctWord, true, true);
        broadcastScores();

        if (players.size() < GameLogic.MINIMUM_PLAYERS) {
            logic.setWaitingForPlayers(true);
            broadcastMessage(new String[] {"WAITING"});
            return false;
        } else {
            return true;
        }
    }

    /**
     * Starts a new round, sends each player their role for this round and the word to the drawer
     * that they are tasked to draw.
     *
     * @param isFirst if this is the first round.
     */
    private void newRound(boolean isFirst) {
        Player newDrawer = logic.newRound(isFirst);
        sendMessage(new String[] {"DRAWING", logic.getCurrentWord()}, newDrawer);
        players.stream().filter(p -> p.isGuessing())
                .forEach(p -> sendMessage(new String[] {"GUESSING"}, p));
        broadcastChatMessage(newDrawer + " is drawing", false, true);
        drawingPoints.clear();
        System.out.println("[GAME]: New round ");
        System.out.println("-> " + newDrawer + " is drawing " + logic.getCurrentWord());
    }

    /**
     * Broadcasts the current score board to all players. The sent scoreboard is ordered by the
     * players' scores.
     */
    private void broadcastScores() {
        @SuppressWarnings("unchecked")
        ArrayList<Player> sortedPlayers = (ArrayList<Player>) players.clone();
        sortedPlayers.sort((p1, p2) -> p2.getScore() - p1.getScore());
        String scores = sortedPlayers.stream().map(p -> p.getId() + ":" + p.getScore())
                .collect(Collectors.joining(","));
        broadcastMessage(new String[] {"SCORES", scores});
    }

    /**
     * Sends the game's state and the player's role in the current round. The state can be GUESSING
     * (if player is a guesser), DRAWING (if player the drawer, the word is also sent), CORRECT (if
     * player is a guesser but has guessed correct), or WAITING in other cases.
     * 
     * If sendCanvas is true, then all points on the current round's canvas is also sent. To lessen
     * network overhead, the points are joined as a comma-seperated list and sent together. However,
     * list is split into mulitple packets if the message text exceeds 512 bytes.
     *
     * @param player the player to send the gamestate to.
     * @param sendCanvas if the points in canvas should be sent as well.
     */
    private void sendGameState(Player player, boolean sendCanvas) {
        String[] message = new String[3];
        message[0] = "GAMESTATE";
        if (player.isGuessing()) {
            message[1] = "GUESSING";
        } else if (player.isDrawing()) {
            message[1] = "DRAWING";
            message[2] = logic.getCurrentWord();
        } else if (logic.hasGuessedCorrect(player)) {
            message[1] = "CORRECT";
        } else {
            message[1] = "WAITING";
        }
        sendMessage(message, player);

        if (sendCanvas && !drawingPoints.isEmpty()) {
            String pointMessage = "";
            try {
                int maxSize = 512 - "POINT|".getBytes("UTF-8").length;
                for (Point point : drawingPoints) {
                    // Send the current list if the new point won't fit within size limit.
                    if ((pointMessage + point.x + ":" + point.y + ",")
                            .getBytes("UTF-8").length > maxSize) {
                        sendMessage(new String[] {"POINT", pointMessage}, player);
                        pointMessage = "";
                    }
                    pointMessage += point.x + ":" + point.y + ",";
                }
                sendMessage(new String[] {"POINT", pointMessage}, player);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }
}
