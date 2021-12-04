package drawlio.client;

import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import javax.swing.JTextField;

/**
 * This class handles an instance of a Drawlio client application.
 * 
 * Drawlio is a 2-11 player word-guessing game inspired by "pictionary", "Draw Something" and
 * "Scribble.io". One player is tasked to draw a word, and the other players need to guess that word
 * from the drawing. Players score more points the faster they are to guess the correct word.
 * 
 * @author Karl Koivunen
 * @since 2021-02-18
 */
public class Client {
    private ClientGUI gui;
    private Connection connection;
    private int playerId;
    private boolean guessing = false;
    private boolean drawing = false;
    private String drawingWord;

    /**
     * Instantiates a new drawlio client. Sets up a GUI and attempts to connect to the server. If
     * connection fails, the program terminates. Creates and starts a thread that listens for
     * packets from and communicates with the server.
     *
     * @param serverAddress the Drawlio server's address.
     * @param serverPort the Drawlio server's port.
     */
    public Client(String serverAddress, int serverPort) {
        gui = new ClientGUI(new PressListener(), new DragListener(), new GuessListener());

        gui.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                // Send a disconnect message to the server and allow current tasks in thread to
                // gracefully finish before terminating the program.
                connection.stop();
            }
        });

        try {
            System.out.println("Attempting connection to " + serverAddress + ":" + serverPort);
            connection = new Connection(this, serverAddress, serverPort);
            gui.setVisible(true);
            Thread thread = new Thread(connection);
            thread.start();
        } catch (IOException e) {
            System.out.println("Unable to connect");
        }
    }

    /**
     * Gets the player's id.
     *
     * @return the player id
     */
    public int getPlayerId() {
        return playerId;
    }

    /**
     * Sets the player's id. Also sets the title of the GUI window to include it.
     *
     * @param playerId the new player id.
     */
    public void setPlayerId(int playerId) {
        this.playerId = playerId;
        gui.setTitle("Drawlio: Player " + playerId);
    }

    /**
     * Checks if the player is drawing.
     *
     * @return true, if player is drawing.
     */
    public boolean isDrawing() {
        return drawing;
    }

    /**
     * Sets the game's status message in the GUI.
     *
     * @param status the new status.
     */
    public void setStatus(String status) {
        gui.setStatus(status);
    }

    /**
     * Sets the game's state.
     *
     * @param drawing is the player drawing.
     * @param guessing is the player guessing.
     * @param word the word to be drawn if player is drawing, otherwise null.
     */
    public void setGameState(boolean drawing, boolean guessing, String word) {
        this.drawing = drawing;
        this.guessing = guessing;
        drawingWord = word;
        gui.setDrawingAllowed(drawing, drawingWord);
        gui.setGuessingAllowed(guessing);
    }

    /**
     * Adds a new chat message.
     *
     * @param message the message.
     * @param colored the colored
     * @param bold the bold
     */
    public void addChatMessage(String message, boolean colored, boolean bold) {
        gui.addMessage(message, colored, bold);
    }

    /**
     * Adds a new paint point to the canvas.
     *
     * @param x the point's x-coordinate.
     * @param y the point's y-coordinate.
     */
    public void addPoint(int x, int y) {
        gui.canvas.addPoint(new Point(x, y));
    }

    /**
     * Clear the canvas.
     */
    public void clearCanvas() {
        gui.canvas.clear();
    }

    /**
     * Updates the scoreboard is the GUI. The passed string must be formatted as a comma-seperated
     * list of players, where every player is pair of its ID and their score seperated by a colon
     * (':'). Ex: "1:4,2:2,3:0".
     *
     * @param scoresString the formatted string containing the players' scores.
     */
    public void updateScores(String scoresString) {
        String[] players = scoresString.split(","); // All ID:score pairs.
        String[][] scores = new String[players.length][];
        for (int i = 0; i < players.length; i++) {
            scores[i] = players[i].split(":");
        }
        gui.updateScoreLabels(scores, playerId);
    }

    /**
     * Starts a new round by clearing the canvas and setting the gamestate as either drawing or
     * guessing.
     *
     * @param isDrawing true if the player is drawing, false if guessing.
     * @param word the word to be drawn if the player is drawing, otherwise null.
     */
    public void newRound(boolean isDrawing, String word) {
        setGameState(isDrawing, !isDrawing, word);
        clearCanvas();
    }

    /**
     * Sets the gamestate to "Waiting for players", where both drawing and guessing is disallowed.
     */
    public void waitingForPlayers() {
        setGameState(false, false, null);
        gui.setStatus("Waiting for players...");
    }

    /**
     * Terminates the program.
     *
     * @param message the message.
     */
    public void exit(String message) {
        System.out.println(message);
        System.exit(0);
    }

    /**
     * The listener for the mouse press event on the canvas. If the player is drawing, add a point
     * to the canvas and send the point to the server.
     */
    private class PressListener extends MouseAdapter {
        public void mousePressed(MouseEvent event) {
            // Ignore if player isn't currently drawing.
            if (!drawing) {
                return;
            }
            Point point = event.getPoint();
            addPoint(point.x, point.y);
            connection.sendPointData(point.x, point.y);
        }
    }

    /**
     * The listener for the mouse drag event on the canvas. If the player is drawing, add a point to
     * the canvas and send the point to the server.
     */
    private class DragListener extends MouseMotionAdapter {
        public void mouseDragged(MouseEvent event) {
            // Ignore if player isn't currently drawing.
            if (!drawing) {
                return;
            }
            Point point = event.getPoint();
            addPoint(point.x, point.y);
            connection.sendPointData(point.x, point.y);
        }
    }

    /**
     * The listener for the guess input field. Sends the guess to the Drawlio server.
     */
    private class GuessListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            // Ignore if player isn't currently guessing.
            if (!guessing) {
                return;
            }
            // If the message is not empty, send the message and empty the input field.
            if (!e.getActionCommand().isEmpty()) {
                connection.sendMessage("GUESS|" + e.getActionCommand());
                ((JTextField) e.getSource()).setText("");
            }
        }
    }
}
