package drawlio.server;

import java.net.SocketAddress;

/**
 * Represents a player in Drawlio.
 * 
 * @author Karl Koivunen
 * @since 2021-02-15
 */
public class Player {
    private static int idCounter = 1;
    private final int id;
    private final SocketAddress socketAddress;
    private boolean guessing = false;
    private boolean drawing = false;
    private int score = 0;
    public int unRespondedChecks = 0;

    /**
     * Instantiates a new player.
     *
     * @param address the address to this player
     */
    public Player(SocketAddress address) {
        id = idCounter++;
        socketAddress = address;
    }

    public SocketAddress getSocketAddress() {
        return socketAddress;
    }

    public boolean isGuessing() {
        return guessing;
    }

    public void setGuessing(boolean guessing) {
        this.guessing = guessing;
    }

    public boolean isDrawing() {
        return drawing;
    }

    public void setDrawing(boolean drawing) {
        this.drawing = drawing;
    }

    public int getId() {
        return id;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    /**
     * Compares an address with this player's address.
     *
     * @param address the address to compare with.
     * @return true, if equal.
     */
    public boolean equals(SocketAddress address) {
        return address.equals(socketAddress);
    }

    @Override
    public String toString() {
        return String.format("Player %s", id, score);
    }
}
