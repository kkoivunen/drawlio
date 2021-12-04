package drawlio.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * The graphical user interface for a Drawlio client. Has a score, a drawing canvas, a chat log and
 * a input box.
 * 
 * @author Karl Koivunen
 * @since 2021-02-18
 */
public class ClientGUI extends JFrame {
    private static final long serialVersionUID = 1L;
    public Canvas canvas;
    private JTextField inputField = new JTextField();
    private JTextPane chatLog = new JTextPane(); // Pane instead of area allows mixed formatting
    private JLabel gameStatus = new JLabel();
    private ArrayList<JLabel> playerLabels = new ArrayList<JLabel>();

    /**
     * Instantiates a new drawlio GUI.
     *
     * @param pressListener the listener for mouse presses on the canvas.
     * @param dragListener the listener for mouse dragging on the canvas.
     * @param guessListener the listener for making a guess.
     */
    public ClientGUI(MouseAdapter pressListener, MouseMotionAdapter dragListener,
            ActionListener guessListener) {
        super("Drawlio: Connecting...");
        canvas = new Canvas(pressListener, dragListener);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(new Dimension(900, 500));
        setResizable(false);
        setLayout(new BorderLayout());
        add(canvas, BorderLayout.CENTER);

        Font font = chatLog.getFont().deriveFont(16F);

        JPanel playersPanel = new JPanel(new GridLayout(0, 1, 0, 0));
        playersPanel.setPreferredSize(new Dimension(150, 0));

        JLabel label = new JLabel("SCORE");
        label.setFont(font.deriveFont(Font.BOLD, 22F));
        playersPanel.add(label);

        for (int i = 0; i < 11; i++) {
            label = new JLabel();
            label.setFont(font);
            label.setBorder(BorderFactory.createMatteBorder(2, 0, 0, 0, Color.GRAY));
            playerLabels.add(label);
            playersPanel.add(label);
        }
        add(playersPanel, BorderLayout.LINE_START);

        JPanel chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setPreferredSize(new Dimension(250, 0));
        chatLog.setEditable(false);
        chatLog.setMargin(new Insets(5, 5, 0, 5));
        chatLog.setFont(font);
        chatPanel.add(new JScrollPane(chatLog, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER));

        inputField.setMaximumSize(new Dimension(Short.MAX_VALUE, 50));
        inputField.setFont(font);
        inputField.addActionListener(guessListener);
        chatPanel.add(inputField);
        add(chatPanel, BorderLayout.LINE_END);

        gameStatus.setFont(font.deriveFont(Font.BOLD, 32F));
        gameStatus.setText("Connecting...");
        gameStatus.setHorizontalAlignment(SwingConstants.CENTER);
        add(gameStatus, BorderLayout.PAGE_START);
    }

    /**
     * Changes appearance of the canvas to indicate if the player can draw on it.
     *
     * @param isAllowed if drawing is allowed.
     * @param word the word that the player is drawing, if they do.
     */
    public void setDrawingAllowed(boolean isAllowed, String word) {
        if (isAllowed) {
            setStatus("You are drawing: " + word.toUpperCase());
            canvas.setBackground(Color.WHITE);
            canvas.setBorder(BorderFactory.createLineBorder(new Color(0, 200, 0), 4));
        } else {
            canvas.setBackground(new Color(220, 220, 220));
            canvas.setBorder(BorderFactory.createLineBorder(Color.RED, 4));
        }
    }

    /**
     * Changes appearance and the editable state of the input field to indicate if the player can
     * guess.
     *
     * @param isAllowed if guessing is allowed.
     */
    public void setGuessingAllowed(boolean isAllowed) {
        inputField.setEditable(isAllowed);
        if (isAllowed) {
            setStatus("You are guessing.");
            inputField.setBackground(Color.WHITE);
            inputField.setBorder(BorderFactory.createLineBorder(new Color(0, 200, 0), 2));
        } else {
            inputField.setBackground(new Color(220, 220, 220));
            inputField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
        }
    }

    /**
     * Sets the status message.
     *
     * @param status the new status message.
     */
    public void setStatus(String status) {
        gameStatus.setText(status);
    }

    /**
     * Adds a message at the bottom of the chat log.
     *
     * @param message the message.
     * @param colored if the text should be colored.
     * @param bold if the text should be bold.
     */
    public void addMessage(String message, boolean colored, boolean bold) {
        StyledDocument document = chatLog.getStyledDocument();
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, (colored ? new Color(0, 150, 0) : Color.BLACK));

        StyleConstants.setBold(attributes, bold);

        try {
            document.insertString(document.getLength(), "\n" + message, attributes);
            // This scrolls the scrollpane to the bottom.
            chatLog.setCaretPosition(document.getLength());
        } catch (BadLocationException e) {
        }
    }

    /**
     * Updates the labels in the score board. The label
     *
     * @param scores the scores.
     * @param playerId the user's player ID.
     */
    public void updateScoreLabels(String[][] scores, int playerId) {
        playerLabels.forEach(l -> l.setText(""));
        for (int i = 0; i < scores.length; i++) {
            String[] score = scores[i];
            JLabel label = playerLabels.get(i);
            label.setText("Player " + score[0] + ": " + score[1]);
            // Make text bold if it is this player's score.
            if (playerId == Integer.parseInt(score[0])) {
                label.setFont(label.getFont().deriveFont(Font.BOLD));
            } else {
                label.setFont(label.getFont().deriveFont(Font.PLAIN));
            }
        }
    }
}
