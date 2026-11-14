package com.folumo.tree;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Draws a tree as glowing orbs over a starfield. Node x/y is the orb centre.
 * Drag empty space to pan, mouse wheel to zoom. Used by both the editor and the game.
 */
public class TreeCanvas extends JPanel {
    public static final int R = 32;
    private static final Color EDGE = new Color(0x27314f);

    private Tree tree;
    public Function<Node, Color> fill = n -> Theme.ACCENT;
    public Function<Node, String> subtitle = n -> Fmt.num(n.cost) + " " + tree.currency;
    public BiFunction<Node, Node, Color> edgeColor = (from, to) -> null; // null = dim link, otherwise it glows
    public BiConsumer<Node, MouseEvent> onPress = (n, e) -> {}; // n is null when empty space is pressed
    public Runnable onMoved = () -> {};
    public boolean movable;
    public Node selected;

    private double offX, offY, zoom = 1;
    private boolean centered;
    private final float[][] stars = new float[240][];
    private record Pop(String text, double x, double y, Color color, long born) {}
    private final List<Pop> pops = new ArrayList<>();
    private final Timer anim = new Timer(16, e -> {
        repaint();
        if (pops.isEmpty()) ((Timer) e.getSource()).stop();
    });

    public TreeCanvas(Tree tree) {
        this.tree = tree;
        setBackground(Theme.BG);
        setPreferredSize(new Dimension(1200, 780));
        setToolTipText("");
        Random r = new Random(7);
        for (int i = 0; i < stars.length; i++) // x, y, size, brightness, parallax
            stars[i] = new float[]{r.nextFloat(), r.nextFloat(), .6f + r.nextFloat() * 1.8f, .15f + r.nextFloat() * .6f, .03f + r.nextFloat() * .2f};

        MouseAdapter m = new MouseAdapter() {
            Point last, start;
            Node drag;
            int dragX, dragY;

            @Override
            public void mousePressed(MouseEvent e) {
                last = start = e.getPoint();
                Node n = nodeAt(e.getPoint());
                drag = movable && SwingUtilities.isLeftMouseButton(e) && !e.isShiftDown() ? n : null;
                if (drag != null) {
                    dragX = drag.x;
                    dragY = drag.y;
                }
                onPress.accept(n, e);
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (drag != null) {
                    drag.x = dragX + (int) Math.round((e.getX() - start.x) / zoom);
                    drag.y = dragY + (int) Math.round((e.getY() - start.y) / zoom);
                    onMoved.run();
                } else {
                    offX += e.getX() - last.x;
                    offY += e.getY() - last.y;
                }
                last = e.getPoint();
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                double old = zoom;
                zoom = Math.max(.3, Math.min(3, zoom * Math.pow(1.12, -e.getPreciseWheelRotation())));
                offX = e.getX() - (e.getX() - offX) * zoom / old; // keep the point under the cursor still
                offY = e.getY() - (e.getY() - offY) * zoom / old;
                repaint();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        addMouseWheelListener(m);
    }

    public void setTree(Tree tree) {
        this.tree = tree;
        selected = null;
        centered = false;
        repaint();
    }

    /** World position of the view centre, for placing new nodes. */
    public Point viewCenter() {
        return new Point((int) ((getWidth() / 2.0 - offX) / zoom), (int) ((getHeight() / 2.0 - offY) / zoom));
    }

    public Point toScreen(Node n) {
        return new Point((int) (n.x * zoom + offX), (int) (n.y * zoom + offY));
    }

    public Node nodeAt(Point p) {
        double x = (p.x - offX) / zoom, y = (p.y - offY) / zoom;
        Node hit = null; // last match wins: it is drawn on top
        for (Node n : tree.nodes.values())
            if (outline(n).contains(x, y)) hit = n;
        return hit;
    }

    /** Half-size of a node: hexagons are the biggest, squares the smallest. */
    public static int size(Node n) {
        return switch (n.shape) {
            case SQUARE -> 27;
            case CIRCLE -> R;
            case HEXAGON -> 44;
        };
    }

    private static java.awt.Shape outline(Node n) {
        double x = n.x, y = n.y, s = size(n);
        return switch (n.shape) {
            case SQUARE -> new RoundRectangle2D.Double(x - s, y - s, s * 2, s * 2, 16, 16);
            case CIRCLE -> new Ellipse2D.Double(x - s, y - s, s * 2, s * 2);
            case HEXAGON -> {
                Path2D hex = new Path2D.Double();
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI / 3 * i - Math.PI / 2; // pointy top
                    if (i == 0) hex.moveTo(x + s * Math.cos(a), y + s * Math.sin(a));
                    else hex.lineTo(x + s * Math.cos(a), y + s * Math.sin(a));
                }
                hex.closePath();
                yield hex;
            }
        };
    }

    /** Floating text that rises and fades, at a point in this component's coordinates. */
    public void pop(String text, Point at, Color color) {
        pops.add(new Pop(text, at.x, at.y, color, System.currentTimeMillis()));
        anim.start();
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        Node n = nodeAt(e.getPoint());
        if (n == null) return null;
        return "<html><b>" + esc(n.name) + "</b><br>" + esc(n.description).replace("\n", "<br>")
                + "<br><i>+" + Fmt.num(n.income) + " " + esc(tree.currency) + "/s, +"
                + Fmt.num(n.clickBonus) + " per click</i></html>";
    }

    private void centerView() {
        double minX = 0, maxX = 0, minY = 0, maxY = 0;
        boolean first = true;
        for (Node n : tree.nodes.values()) {
            minX = first ? n.x : Math.min(minX, n.x);
            maxX = first ? n.x : Math.max(maxX, n.x);
            minY = first ? n.y : Math.min(minY, n.y);
            maxY = first ? n.y : Math.max(maxY, n.y);
            first = false;
        }
        offX = getWidth() / 2.0 - (minX + maxX) / 2 * zoom;
        offY = Math.max(getHeight() / 2.0 - (minY + maxY) / 2 * zoom, 230 - minY * zoom); // tall trees open at the top, below the HUD
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (getWidth() == 0) return;
        if (!centered) {
            centerView();
            centered = true;
        }
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        paintSpace(g);

        AffineTransform screen = g.getTransform();
        g.translate(offX, offY);
        g.scale(zoom, zoom);
        for (Node n : tree.nodes.values())
            for (String r : n.requires) {
                Node p = tree.nodes.get(r);
                if (p != null) paintLink(g, p, n);
            }
        for (Node n : tree.nodes.values()) paintNode(g, n);
        g.setTransform(screen);
        paintPops(g);
        g.dispose();
    }

    private void paintSpace(Graphics2D g) {
        int w = getWidth(), h = getHeight();
        g.setPaint(new GradientPaint(0, 0, Theme.BG_TOP, 0, h, Theme.BG));
        g.fillRect(0, 0, w, h);
        nebula(g, w * .22f, h * .28f, Math.max(w, h) * .55f, new Color(0x6d28d9));
        nebula(g, w * .82f, h * .78f, Math.max(w, h) * .5f, new Color(0x0e7490));
        for (float[] s : stars) {
            float x = mod(s[0] * w + (float) offX * s[4], w), y = mod(s[1] * h + (float) offY * s[4], h);
            g.setColor(new Color(1f, 1f, 1f, s[3]));
            g.fill(new Ellipse2D.Float(x, y, s[2], s[2]));
        }
    }

    private static void nebula(Graphics2D g, float x, float y, float r, Color c) {
        g.setPaint(new RadialGradientPaint(x, y, r, new float[]{0, 1}, new Color[]{Theme.alpha(c, 45), Theme.alpha(c, 0)}));
        g.fill(new Ellipse2D.Float(x - r, y - r, r * 2, r * 2));
    }

    private void paintLink(Graphics2D g, Node from, Node to) {
        double x1 = from.x, y1 = from.y, x2 = to.x, y2 = to.y;
        CubicCurve2D.Double c;
        if (Math.abs(y2 - y1) >= Math.abs(x2 - x1)) {
            double my = (y1 + y2) / 2;
            c = new CubicCurve2D.Double(x1, y1, x1, my, x2, my, x2, y2);
        } else {
            double mx = (x1 + x2) / 2;
            c = new CubicCurve2D.Double(x1, y1, mx, y1, mx, y2, x2, y2);
        }
        Color col = edgeColor.apply(from, to);
        if (col != null) {
            g.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Theme.alpha(col, 45));
            g.draw(c);
        }
        g.setStroke(new BasicStroke(col != null ? 3.5f : 2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(col != null ? col : EDGE);
        g.draw(c);

        // arrowhead at the curve's midpoint, pointing towards the node that needs it
        double mx = (c.x1 + 3 * c.ctrlx1 + 3 * c.ctrlx2 + c.x2) / 8, my = (c.y1 + 3 * c.ctrly1 + 3 * c.ctrly2 + c.y2) / 8;
        double a = Math.atan2(c.y2 + c.ctrly2 - c.ctrly1 - c.y1, c.x2 + c.ctrlx2 - c.ctrlx1 - c.x1);
        Path2D head = new Path2D.Double();
        head.moveTo(mx + 7 * Math.cos(a), my + 7 * Math.sin(a));
        head.lineTo(mx + 9 * Math.cos(a + 2.5), my + 9 * Math.sin(a + 2.5));
        head.lineTo(mx + 9 * Math.cos(a - 2.5), my + 9 * Math.sin(a - 2.5));
        head.closePath();
        g.fill(head);
    }

    private void paintNode(Graphics2D g, Node n) {
        Color c = fill.apply(n);
        double x = n.x, y = n.y, s = size(n);
        boolean hex = n.shape == Node.Shape.HEXAGON;
        float glow = (float) s * (hex ? 2.8f : 2.2f);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), glow, new float[]{.3f, 1f},
                new Color[]{Theme.alpha(c, hex ? 160 : 120), Theme.alpha(c, 0)}));
        g.fill(new Ellipse2D.Double(x - glow, y - glow, glow * 2, glow * 2));

        java.awt.Shape body = outline(n);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x - s * .35, y - s * .4), (float) s * 1.5f, new float[]{0, .55f, 1},
                new Color[]{Theme.mix(c, Color.WHITE, .45), c.darker(), Theme.mix(c, Color.BLACK, .8)}));
        g.fill(body);
        g.setStroke(new BasicStroke(n == selected ? 3.5f : hex ? 3f : 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(n == selected ? Color.WHITE : Theme.mix(c, Color.WHITE, .35));
        g.draw(body);
        if (hex) { // inner ring marks a milestone
            AffineTransform inner = AffineTransform.getTranslateInstance(x, y);
            inner.scale(.78, .78);
            inner.translate(-x, -y);
            g.setStroke(new BasicStroke(1.5f));
            g.setColor(Theme.alpha(Theme.mix(c, Color.WHITE, .5), 170));
            g.draw(inner.createTransformedShape(body));
        }
        if (n == selected) {
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[]{4, 5}, 0));
            g.setColor(Theme.alpha(Color.WHITE, 140));
            double sel = s * (n.shape == Node.Shape.SQUARE ? 1.4 : 1) + 9;
            g.draw(new Ellipse2D.Double(x - sel, y - sel, sel * 2, sel * 2));
        }

        g.setFont(hex ? Theme.GLYPH.deriveFont(28f) : Theme.GLYPH);
        text(g, initials(n.name), x, y + g.getFontMetrics().getAscent() / 2.0 - 3, Color.WHITE);
        g.setFont(hex ? Theme.NODE_NAME.deriveFont(15f) : Theme.NODE_NAME);
        text(g, clip(g, n.name, 170), x, y + s + 22, Theme.TEXT);
        g.setFont(Theme.NODE_SUB);
        text(g, clip(g, subtitle.apply(n), 170), x, y + s + 39, Theme.GOLD);
    }

    private void paintPops(Graphics2D g) {
        long now = System.currentTimeMillis();
        pops.removeIf(p -> now - p.born > 1000);
        g.setFont(Theme.POP);
        for (Pop p : pops) {
            double age = (now - p.born) / 1000.0;
            text(g, p.text, p.x, p.y - age * 60, Theme.alpha(p.color, (int) (255 * (1 - age))));
        }
    }

    /** Centred text with a soft shadow so it reads over the glow. */
    private static void text(Graphics2D g, String s, double cx, double baseline, Color color) {
        float x = (float) (cx - g.getFontMetrics().stringWidth(s) / 2.0);
        g.setColor(new Color(0, 0, 0, Math.min(170, color.getAlpha())));
        g.drawString(s, x + 1, (float) baseline + 1);
        g.setColor(color);
        g.drawString(s, x, (float) baseline);
    }

    private static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String w : name.trim().split("\\s+"))
            if (!w.isEmpty() && sb.length() < 2) sb.appendCodePoint(Character.toUpperCase(w.codePointAt(0)));
        return sb.isEmpty() ? "?" : sb.toString();
    }

    private static String clip(Graphics2D g, String s, int max) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(s) <= max) return s;
        while (!s.isEmpty() && fm.stringWidth(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static float mod(float a, float m) {
        return ((a % m) + m) % m;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
