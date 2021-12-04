package drawlio.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * This class represents the logic and state of a Drawlio game. It handles most of the tasks
 * affecting the state, such as adding new players, starting and finishing rounds, distributing
 * scores, validating guesses, etc. GameLogic is not responsible for running the game loop or
 * communicating with the client applications.
 * 
 * @author Karl Koivunen
 * @since 2021-02-18
 */
public class GameLogic {
    // The minimum amount of players required to start a new round.
    protected static final int MINIMUM_PLAYERS = 2;

    private boolean waitingForPlayers = true;
    // All connected participating players.
    private ArrayList<Player> players;
    // Players who have guessed correctly in the current round.
    private ArrayList<Player> guessedCorrect = new ArrayList<Player>();
    // All potential words that drawers must draw.
    private ArrayList<String> words;
    // The current pool of words that will be picked from at the start of a new round. It's filled
    // and shuffled with the words from the words list above, and refilled when all words have been
    // used.
    private ArrayList<String> wordPool;
    private String currentWord;

    /**
     * Instantiates a new Drawlio logic instance.
     *
     * @param players the list containing participating players.
     * @param words the words that the drawers can be tasked to draw.
     */
    public GameLogic(ArrayList<Player> players, String[] words) {
        this.players = players;
        this.words = new ArrayList<String>(Arrays.asList(words));
        this.wordPool = new ArrayList<String>(this.words);
        Collections.shuffle(this.wordPool);
    }

    /**
     * Adds a player to the game as a participant. Also checks if the game is waiting for more
     * players, and adding this player results in there being enough. In which case this method
     * returns true, indicating a new round can be started. If a round is in progress or theres
     * still not enough players, returns false.
     *
     * @param newPlayer the new player.
     * @return true, if a new round is ready to be started as a result of this new player.
     */
    public boolean addPlayer(Player newPlayer) {
        players.add(newPlayer);
        if (waitingForPlayers && players.size() >= MINIMUM_PLAYERS) {
            waitingForPlayers = false;
            return true;
        } else if (!waitingForPlayers) {
            // Sets the player as a guesser if a round is in progress.
            newPlayer.setGuessing(true);
        }
        return false;
    }

    /**
     * Checks if a client's action is legal in it's current state legal.
     *
     * @param player the player doing the action.
     * @param action the action.
     * @return true, if the action is legal.
     */
    public boolean isActionLegal(Player player, String action) {
        boolean isLegal = false;

        if (action.equals("CONNECT") && player == null) {
            isLegal = true;
        } else if (player == null || waitingForPlayers) {
            // If the player isn't connected or a round is not in progress.
            isLegal = false;
        } else if (action.equals("GUESS") && player.isGuessing()) {
            isLegal = true;
        } else if (action.equals("POINT") && player.isDrawing()) {
            isLegal = true;
        }

        return isLegal;
    }

    /**
     * Checks if a guess by a player is correct, and if so, disable further guessing and save the
     * player as a correct guesser.
     *
     * @param player the player making the guess.
     * @param guess the guess.
     * @return true, if the guess was the word being drawn.
     */
    public boolean guessWord(Player player, String guess) {
        if (currentWord.toLowerCase().equals(guess.toLowerCase())) {
            player.setGuessing(false);
            // Shifting guessers that guessed faster makes determining the scores easier by using
            // their indices. I.e faster guess = heigher index = more score.
            guessedCorrect.add(0, player);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks if the player has guessed correctly this round.
     *
     * @param player the player.
     * @return true, if they have.
     */
    public boolean hasGuessedCorrect(Player player) {
        return guessedCorrect.contains(player);
    }

    /**
     * Returns whether the game is waiting for more players or not.
     *
     * @return true, if it is waiting for more players.
     */
    public boolean isWaitingForPlayers() {
        return waitingForPlayers;
    }

    /**
     * Sets whether the game is waiting for more players or not.
     *
     * @param waitingForPlayers if it's waiting for players.
     */
    public void setWaitingForPlayers(boolean waitingForPlayers) {
        this.waitingForPlayers = waitingForPlayers;
    }

    /**
     * Determines if the round is finished. The round is finished if there is no connected drawer or
     * too few guessers (that haven't guessed correctly). The number of guessers depends on the
     * number of players total. With two or fewer players, no remaining guessers is required. With
     * more than two players, one or fewer guessers is required.
     *
     * @return true, if the round is determined to be finished.
     */
    public boolean isRoundFinished() {
        long numGuessing = players.stream().filter(p -> p.isGuessing()).count();
        boolean drawerExists = players.stream().anyMatch(p -> p.isDrawing());

        if (!drawerExists || (players.size() <= 2 && numGuessing == 0)
                || (players.size() > 2 && numGuessing < 2)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Distributes score to correct guessers.
     */
    private void distributeScore() {
        // Since the correct guesses list is ordered by the time they guessed correctly, the index
        // can be used to determine each player's score.
        for (Player player : guessedCorrect) {
            player.setScore(player.getScore() + guessedCorrect.indexOf(player) + 1);
        }
    }

    /**
     * Finishes the current round. Distributes score to relevant guessers and disables guessing and
     * drawing from all players.
     */
    public void finishRound() {
        distributeScore();
        players.forEach(p -> {
            p.setGuessing(false);
            p.setDrawing(false);
        });
        guessedCorrect.clear();
    }

    /**
     * Starts a new round. A new drawer and a word is picked, with remaining being set as guessers.
     *
     * @param isFirst if it's the first round, in which case the turn order isn't rotated.
     * @return the drawing player for this round.
     */
    public Player newRound(boolean isFirst) {
        players.forEach(p -> p.setGuessing(true));
        if (!isFirst) {
            Collections.rotate(players, -1);
        }
        players.get(0).setDrawing(true);
        players.get(0).setGuessing(false);
        newWord();

        return players.get(0);
    }

    /**
     * Picks a new word as the current round's word and removes it from the pool. If the pool is
     * empty, then refill the pool with all words and shuffle the order.
     */
    private void newWord() {
        if (wordPool.isEmpty()) {
            wordPool = new ArrayList<String>(words);
            Collections.shuffle(wordPool);
        }

        currentWord = wordPool.remove(0);
    }

    /**
     * Gets the current round's word.
     *
     * @return the current word.
     */
    public String getCurrentWord() {
        return currentWord;
    }
}
