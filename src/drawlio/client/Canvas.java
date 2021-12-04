package drawlio.client;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseMotionAdapter;
import java.util.HashSet;
import java.util.Iterator;
import javax.swing.JPanel;

/**
 * A drawable canvas with a white background and black foreground.
 * 
 * @author Karl Koivunen
 * @since 2021-02-13
 */
public class Canvas extends JPanel {
    private static final long serialVersionUID = 1L;
    private HashSet<Point> points = new HashSet<Point>();

    /**
     * Instantiates a new canvas.
     *
     * @param pressListener the listener for mouse presses.
     * @param dragListener the listener for mouse dragging.
     */
    public Canvas(MouseAdapter pressListener, MouseMotionAdapter dragListener) {
        setBackground(Color.WHITE);
        addMouseListener(pressListener);
        addMouseMotionListener(dragListener);
    }

    /**
     * Paints a small black oval on every point in the canvas' hashset. Synchronized to prevent new
     * points being added while the hashset is iterated through.
     *
     * @param g the graphics object.
     */
    @Override
    synchronized public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(Color.BLACK);
        Iterator<Point> i = points.iterator();
        while (i.hasNext()) {
            Point point = i.next();
            g.fillOval(point.x, point.y, 7, 7);
        }
    }

    /**
     * Adds a new paint point and repaints the canvas.
     *
     * @param point the new point.
     */
    synchronized public void addPoint(Point point) {
        points.add(point);
        repaint();
    }

    /**
     * Clears the canvas.
     */
    public void clear() {
        points.clear();
        repaint();
    }
}
