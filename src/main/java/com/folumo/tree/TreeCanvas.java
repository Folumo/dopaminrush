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
import java.util.function.Predicate;

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
    public Predicate<Node> movable = n -> false;
    public Node selected;
    /** Highlighted with a pulsing ring, or an arrow at the edge of the view when it's off screen. */
    public Node goal;
    public boolean minimap = true;
    /** Nodes that aren't shown are left out completely, with their links (fog of war). */
    public Predicate<Node> shown = n -> true;
    /** Shown as "???": the player can see something is there, but not what. */
    public Predicate<Node> mystery = n -> false;
    private Rectangle mini = new Rectangle(); // where the minimap was last drawn, for clicks

    private double offX, offY, zoom = 1;
    private boolean centered;
    private final float[][] stars = new float[240][];
    private record Pop(String text, double x, double y, Color color, long born) {}
    private final List<Pop> pops = new ArrayList<>();
    private record Ring(double x, double y, long born) {}
    private final List<Ring> rings = new ArrayList<>();
    /** Called with the click point when the player catches the golden molecule. */
    public java.util.function.Consumer<Point> onBonus = p -> {};
    private long bonusBorn = -1; // -1 = no golden molecule on screen
    private double bonusY;
    private static final double BONUS_MS = 14_000;
    private final Timer anim = new Timer(16, e -> {
        repaint();
        if (pops.isEmpty() && rings.isEmpty() && bonusAt() == null) ((Timer) e.getSource()).stop();
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
            boolean onMini;

            @Override
            public void mousePressed(MouseEvent e) {
                last = start = e.getPoint();
                Point2D bonus = bonusAt();
                if (bonus != null && bonus.distance(e.getPoint()) < 40) {
                    bonusBorn = -1;
                    onBonus.accept(e.getPoint());
                    return;
                }
                onMini = minimap && mini.contains(e.getPoint());
                if (onMini) {
                    lookAtMini(e.getPoint());
                    return;
                }
                Node n = nodeAt(e.getPoint());
                drag = n != null && movable.test(n) && SwingUtilities.isLeftMouseButton(e) && !e.isShiftDown() ? n : null;
                if (drag != null) {
                    dragX = drag.x;
                    dragY = drag.y;
                }
                onPress.accept(n, e);
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (onMini) lookAtMini(e.getPoint());
                else if (drag != null) {
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
        zoom = 1;
        centered = false;
        repaint();
    }

    /** Centres the view on the world point under {@code p} in the minimap. */
    private void lookAtMini(Point p) {
        double[] b = miniWorld();
        double k = mini.width / (b[1] - b[0]);
        offX = getWidth() / 2.0 - (b[0] + (p.x - mini.x) / k) * zoom;
        offY = getHeight() / 2.0 - (b[2] + (p.y - mini.y) / k) * zoom;
        repaint();
    }

    /** Tree bounds with a margin, as shown by the minimap: {minX, maxX, minY, maxY}. */
    private double[] miniWorld() {
        double[] b = treeBounds();
        return new double[]{b[0] - 120, b[1] + 120, b[2] - 120, b[3] + 120};
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
            if (shown.test(n) && outline(n).contains(x, y)) hit = n;
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

    /** Sends a golden molecule drifting across the view; catching it calls {@link #onBonus}. */
    public void spawnBonus() {
        bonusBorn = System.currentTimeMillis();
        bonusY = getHeight() * (.3 + Math.random() * .4);
        anim.start();
    }

    /** Where the golden molecule is now, in this component's coordinates; null when there is none. */
    private Point2D bonusAt() {
        if (bonusBorn < 0) return null;
        double p = (System.currentTimeMillis() - bonusBorn) / BONUS_MS;
        if (p >= 1) {
            bonusBorn = -1;
            return null;
        }
        return new Point2D.Double(-40 + p * (getWidth() + 80), bonusY + 40 * Math.sin(p * 14));
    }

    /** A ring of light that spreads out from a node across the tree. */
    public void ring(Node n) {
        rings.add(new Ring(n.x, n.y, System.currentTimeMillis()));
        anim.start();
    }

    /** Floating text that rises and fades, at a point in this component's coordinates. */
    public void pop(String text, Point at, Color color) {
        pops.add(new Pop(text, at.x, at.y, color, System.currentTimeMillis()));
        anim.start();
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        Node n = minimap && mini.contains(e.getPoint()) ? null : nodeAt(e.getPoint());
        if (n == null) return null;
        if (mystery.test(n)) return "???";
        return "<html><b>" + esc(n.name) + "</b><br>" + esc(n.description).replace("\n", "<br>")
                + "<br><i>" + esc(I18n.t("info.perSecond", Fmt.num(n.income), tree.currency)) + ", "
                + esc(I18n.t("info.perClick", Fmt.num(n.clickBonus))) + "</i></html>";
    }

    /** {minX, maxX, minY, maxY} of all node centres (all 0 for an empty tree). */
    private double[] treeBounds() {
        double[] b = {0, 0, 0, 0};
        boolean first = true;
        for (Node n : tree.nodes.values()) {
            b[0] = first ? n.x : Math.min(b[0], n.x);
            b[1] = first ? n.x : Math.max(b[1], n.x);
            b[2] = first ? n.y : Math.min(b[2], n.y);
            b[3] = first ? n.y : Math.max(b[3], n.y);
            first = false;
        }
        return b;
    }

    private void centerView() {
        double[] b = treeBounds();
        offX = getWidth() / 2.0 - (b[0] + b[1]) / 2 * zoom;
        offY = Math.max(getHeight() / 2.0 - (b[2] + b[3]) / 2 * zoom, 230 - b[2] * zoom); // tall trees open at the top, below the HUD
    }

    /** Smoothly zooms out until the whole tree fits the view, then runs {@code done}. */
    public void zoomToFit(Runnable done) {
        double[] b = treeBounds();
        double pad = 140;
        double tz = Math.min(1.5, Math.min(getWidth() / (b[1] - b[0] + pad * 2), getHeight() / (b[3] - b[2] + pad * 2)));
        animateTo(tz, (b[0] + b[1]) / 2, (b[2] + b[3]) / 2, 3, done);
    }

    /** Smoothly pans so {@code n} is in the middle of the view, keeping the zoom. */
    public void focus(Node n) {
        animateTo(zoom, n.x, n.y, .6, () -> {});
    }

    private void animateTo(double tz, double worldX, double worldY, double seconds, Runnable done) {
        double tx = getWidth() / 2.0 - worldX * tz, ty = getHeight() / 2.0 - worldY * tz;
        double z0 = zoom, x0 = offX, y0 = offY;
        long start = System.nanoTime();
        Timer t = new Timer(16, null);
        t.addActionListener(e -> {
            double p = Math.min(1, (System.nanoTime() - start) / 1e9 / seconds), k = 1 - Math.pow(1 - p, 3); // ease out
            zoom = z0 + (tz - z0) * k;
            offX = x0 + (tx - x0) * k;
            offY = y0 + (ty - y0) * k;
            repaint();
            if (p >= 1) {
                t.stop();
                done.run();
            }
        });
        t.start();
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
                if (p != null && shown.test(p) && shown.test(n)) paintLink(g, p, n);
            }
        for (Node n : tree.nodes.values()) if (shown.test(n)) paintNode(g, n);
        if (goal != null) paintGoalRing(g, goal);
        paintRings(g);
        g.setTransform(screen);
        paintBonus(g);
        if (goal != null) paintGoalArrow(g, goal);
        if (minimap && !tree.nodes.isEmpty()) paintMinimap(g);
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
        String name = mystery.test(n) ? "???" : n.name;
        text(g, initials(name), x, y + g.getFontMetrics().getAscent() / 2.0 - 3, Color.WHITE);
        g.setFont(hex ? Theme.NODE_NAME.deriveFont(15f) : Theme.NODE_NAME);
        text(g, clip(g, name, 170), x, y + s + 22, Theme.TEXT);
        g.setFont(Theme.NODE_SUB);
        text(g, clip(g, subtitle.apply(n), 170), x, y + s + 39, Theme.GOLD);
    }

    private void paintRings(Graphics2D g) {
        long now = System.currentTimeMillis();
        rings.removeIf(r -> now - r.born > 1400);
        for (Ring r : rings) {
            double k = (now - r.born) / 1400.0, ease = 1 - Math.pow(1 - k, 3), rad = 50 + 900 * ease;
            g.setStroke(new BasicStroke((float) (10 * (1 - k) + 2)));
            g.setColor(Theme.alpha(Theme.GOLD, (int) (200 * (1 - k))));
            g.draw(new Ellipse2D.Double(r.x - rad, r.y - rad, rad * 2, rad * 2));
        }
    }

    /** The golden molecule: a small glowing hexagon ring with a bright core. */
    private void paintBonus(Graphics2D g) {
        Point2D b = bonusAt();
        if (b == null) return;
        double x = b.getX(), y = b.getY(), spin = (System.currentTimeMillis() - bonusBorn) / 900.0;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), 60, new float[]{0, 1},
                new Color[]{Theme.alpha(Theme.GOLD, 150), Theme.alpha(Theme.GOLD, 0)}));
        g.fill(new Ellipse2D.Double(x - 60, y - 60, 120, 120));
        Path2D hex = new Path2D.Double();
        for (int i = 0; i < 6; i++) {
            double a = spin + Math.PI / 3 * i;
            if (i == 0) hex.moveTo(x + 20 * Math.cos(a), y + 20 * Math.sin(a));
            else hex.lineTo(x + 20 * Math.cos(a), y + 20 * Math.sin(a));
        }
        hex.closePath();
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(Theme.GOLD);
        g.draw(hex);
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(x - 6, y - 6, 12, 12));
    }

    private static double pulse() {
        return System.currentTimeMillis() % 1200 / 1200.0;
    }

    private void paintGoalRing(Graphics2D g, Node n) {
        double p = pulse(), r = size(n) * (n.shape == Node.Shape.SQUARE ? 1.4 : 1) + 12 + 14 * p;
        g.setStroke(new BasicStroke(3f));
        g.setColor(Theme.alpha(Theme.GOLD, (int) (220 * (1 - p))));
        g.draw(new Ellipse2D.Double(n.x - r, n.y - r, r * 2, r * 2));
    }

    /** When the goal is off screen, a gold arrow at the edge points the way. */
    private void paintGoalArrow(Graphics2D g, Node n) {
        Point at = toScreen(n);
        int w = getWidth(), h = getHeight(), m = 60;
        if (at.x > m && at.x < w - m && at.y > m && at.y < h - m) return;
        double cx = w / 2.0, cy = h / 2.0, dx = at.x - cx, dy = at.y - cy;
        double k = Math.min((w / 2.0 - m) / Math.max(1e-9, Math.abs(dx)), (h / 2.0 - m) / Math.max(1e-9, Math.abs(dy)));
        double x = cx + dx * k, y = cy + dy * k, a = Math.atan2(dy, dx), len = 22 + 5 * Math.sin(pulse() * 2 * Math.PI);
        Path2D head = new Path2D.Double();
        head.moveTo(x + len * Math.cos(a), y + len * Math.sin(a));
        head.lineTo(x + 14 * Math.cos(a + 2.4), y + 14 * Math.sin(a + 2.4));
        head.lineTo(x + 14 * Math.cos(a - 2.4), y + 14 * Math.sin(a - 2.4));
        head.closePath();
        g.setColor(Theme.alpha(Theme.GOLD, 70));
        g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(head);
        g.setColor(Theme.GOLD);
        g.fill(head);
    }

    private void paintMinimap(Graphics2D g) {
        double[] b = miniWorld();
        double k = Math.min(200 / (b[1] - b[0]), 150 / (b[3] - b[2]));
        int mw = (int) ((b[1] - b[0]) * k), mh = (int) ((b[3] - b[2]) * k);
        mini = new Rectangle(16, 16, mw, mh); // top-left corner
        RoundRectangle2D box = new RoundRectangle2D.Double(mini.x - 6, mini.y - 6, mw + 12, mh + 12, 14, 14);
        g.setColor(Theme.alpha(Theme.BG, 210));
        g.fill(box);
        g.setColor(Theme.alpha(Theme.ACCENT, 90));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(box);
        for (Node n : tree.nodes.values()) {
            if (!shown.test(n)) continue;
            double d = n.shape == Node.Shape.HEXAGON ? 6 : 3.5;
            g.setColor(Theme.mix(fill.apply(n), Theme.MUTED, .35)); // lighter, so locked nodes still show
            g.fill(new Ellipse2D.Double(mini.x + (n.x - b[0]) * k - d / 2, mini.y + (n.y - b[2]) * k - d / 2, d, d));
        }
        if (goal != null) {
            g.setColor(Theme.GOLD);
            g.draw(new Ellipse2D.Double(mini.x + (goal.x - b[0]) * k - 5, mini.y + (goal.y - b[2]) * k - 5, 10, 10));
        }
        java.awt.Shape clip = g.getClip();
        g.clip(box);
        g.setColor(Theme.alpha(Color.WHITE, 200));
        g.draw(new Rectangle2D.Double(mini.x + (-offX / zoom - b[0]) * k, mini.y + (-offY / zoom - b[2]) * k,
                getWidth() / zoom * k, getHeight() / zoom * k));
        g.setClip(clip);
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
