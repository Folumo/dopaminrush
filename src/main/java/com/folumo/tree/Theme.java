package com.folumo.tree;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;

/** Shared sci-fi look: colours, fonts, glowing buttons and floating cards. */
public final class Theme {
    public static final Color BG = new Color(0x04060d), BG_TOP = new Color(0x0c1230),
            TEXT = new Color(0xe6eaf7), MUTED = new Color(0x8b93b0), GOLD = new Color(0xfcd34d),
            ACCENT = new Color(0x4f9dff), GREEN = new Color(0x34d399), PURPLE = new Color(0x7c6fc4),
            LOCKED = new Color(0x1c2233), RED = new Color(0xf87171);

    public static final Font TITLE = font(Font.BOLD, 34), H2 = font(Font.BOLD, 20), BODY = font(Font.PLAIN, 14),
            BOLD = font(Font.BOLD, 14), NODE_NAME = font(Font.BOLD, 13), NODE_SUB = font(Font.PLAIN, 12),
            GLYPH = font(Font.BOLD, 22), POP = font(Font.BOLD, 20);

    private Theme() {}

    public static Font font(int style, int size) {
        return new Font("Segoe UI", style, size); // falls back to the default font if missing
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    public static Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    public static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    /** Pill-shaped glowing button. */
    public static JButton button(String text, Color color) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color c = !isEnabled() ? new Color(0x2b3245)
                        : getModel().isPressed() ? color.darker()
                        : getModel().isRollover() ? mix(color, Color.WHITE, .18) : color;
                int w = getWidth(), h = getHeight();
                if (isEnabled()) {
                    g.setColor(alpha(c, 70));
                    g.fillRoundRect(0, 0, w, h, h, h);
                }
                g.setPaint(new GradientPaint(0, 0, mix(c, Color.WHITE, .22), 0, h, c.darker()));
                g.fillRoundRect(3, 3, w - 6, h - 6, h - 6, h - 6);
                g.dispose();
                super.paintComponent(g0);
            }
        };
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setForeground(Color.WHITE);
        b.setFont(font(Font.BOLD, 15));
        b.setBorder(BorderFactory.createEmptyBorder(12, 26, 12, 26));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** Translucent rounded panel that floats over the tree. */
    public static class Card extends JPanel {
        public Card() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(16, 22, 18, 22));
            addMouseListener(new MouseAdapter() {}); // swallow clicks so the tree underneath doesn't get them
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(10, 14, 32, 250)); // nearly opaque, so the tree behind never bleeds into the text
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
            g.setColor(new Color(120, 150, 255, 70));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
            g.dispose();
        }
    }
}
