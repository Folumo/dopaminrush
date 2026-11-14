package com.folumo.tree;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

/** The Dopamin Rush icon, drawn in code: the dopamine molecule's ring glowing on the night sky. */
public final class GameIcon {
    private GameIcon() {}

    /** All sizes Windows asks for (title bar, taskbar, Alt+Tab). */
    public static List<Image> images() {
        return List.of(draw(16), draw(32), draw(48), draw(64), draw(128), draw(256));
    }

    public static BufferedImage draw(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(size / 256.0, size / 256.0);

        // Rounded night-sky tile with a purple nebula behind the ring.
        RoundRectangle2D tile = new RoundRectangle2D.Double(8, 8, 240, 240, 56, 56);
        g.setPaint(new GradientPaint(0, 8, Theme.BG_TOP, 0, 248, Theme.BG));
        g.fill(tile);
        g.setClip(tile);
        g.setPaint(new RadialGradientPaint(110, 136, 120, new float[]{0, 1},
                new Color[]{Theme.alpha(Theme.PURPLE, 150), Theme.alpha(Theme.PURPLE, 0)}));
        g.fill(tile);
        if (size >= 48) { // stars only where they are visible
            g.setColor(Theme.alpha(Theme.TEXT, 170));
            for (int[] s : new int[][]{{40, 44, 4}, {200, 36, 3}, {218, 200, 4}, {30, 196, 3}, {180, 226, 3}})
                g.fill(new Ellipse2D.Double(s[0], s[1], s[2], s[2]));
        }
        g.setClip(null);

        // Catechol ring (the hexagon of dopamine), two OH arms and the amine side chain.
        double cx = 112, cy = 140, r = 58;
        Path2D ring = new Path2D.Double();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60 * i - 90);
            if (i == 0) ring.moveTo(cx + r * Math.cos(a), cy + r * Math.sin(a));
            else ring.lineTo(cx + r * Math.cos(a), cy + r * Math.sin(a));
        }
        ring.closePath();
        Path2D chain = new Path2D.Double();
        double[] top = corner(cx, cy, r, 0), upLeft = corner(cx, cy, r, 5), right = corner(cx, cy, r, 2);
        chain.moveTo(top[0], top[1]); chain.lineTo(top[0], top[1] - 40);                 // OH
        chain.moveTo(upLeft[0], upLeft[1]); chain.lineTo(upLeft[0] - 30, upLeft[1] - 17); // OH
        chain.moveTo(right[0], right[1]); chain.lineTo(right[0] + 30, right[1] - 17);
        chain.lineTo(right[0] + 60, right[1]);                                         // CH2-CH2
        java.awt.Shape bonds = new BasicStroke(size < 32 ? 26 : 16, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                .createStrokedShape(size < 32 ? ring : combine(ring, chain));

        for (int w = 34; w > 0; w -= 8) { // soft gold glow
            g.setColor(Theme.alpha(Theme.GOLD, 22));
            g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(bonds);
        }
        g.setPaint(new GradientPaint(50, 60, Theme.GOLD, 200, 210, new Color(0xff8a3d)));
        g.fill(bonds);

        if (size >= 32) { // the amine (NH2) as a bright spark at the end of the chain
            double nx = right[0] + 60, ny = right[1];
            g.setPaint(new RadialGradientPaint((float) nx, (float) ny, 30, new float[]{0, 1},
                    new Color[]{Color.WHITE, Theme.alpha(Theme.ACCENT, 0)}));
            g.fill(new Ellipse2D.Double(nx - 30, ny - 30, 60, 60));
            g.setColor(Theme.ACCENT);
            g.fill(new Ellipse2D.Double(nx - 13, ny - 13, 26, 26));
        }
        g.dispose();
        return img;
    }

    private static double[] corner(double cx, double cy, double r, int i) {
        double a = Math.toRadians(60 * i - 90);
        return new double[]{cx + r * Math.cos(a), cy + r * Math.sin(a)};
    }

    private static Path2D combine(Path2D a, Path2D b) {
        Path2D p = new Path2D.Double(a);
        p.append(b, false);
        return p;
    }

    /** Saves the icon as PNG files and a Windows icon.ico: {@code GameIcon <folder>}. */
    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : ".");
        for (int s : new int[]{16, 32, 64, 256, 512})
            ImageIO.write(draw(s), "png", dir.resolve("icon-" + s + ".png").toFile());
        java.nio.file.Files.write(dir.resolve("icon.ico"), ico(16, 32, 48, 256));
    }

    /** A .ico file holding PNG images (supported since Windows Vista): a header, one entry per size, then the PNGs. */
    static byte[] ico(int... sizes) throws java.io.IOException {
        byte[][] pngs = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) {
            java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
            ImageIO.write(draw(sizes[i]), "png", png);
            pngs[i] = png.toByteArray();
        }
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(6 + 16 * sizes.length + java.util.Arrays.stream(pngs).mapToInt(p -> p.length).sum())
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i] >= 256 ? 0 : sizes[i]; // 0 means 256
            b.put((byte) s).put((byte) s).put((byte) 0).put((byte) 0).putShort((short) 1).putShort((short) 32)
                    .putInt(pngs[i].length).putInt(offset);
            offset += pngs[i].length;
        }
        for (byte[] p : pngs) b.put(p);
        return b.array();
    }
}
